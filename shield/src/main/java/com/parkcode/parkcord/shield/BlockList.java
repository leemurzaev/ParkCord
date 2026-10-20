package com.parkcode.parkcord.shield;

import com.google.common.base.Preconditions;
import com.google.common.base.Ticker;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The keys that are refused outright, each until its block runs out. There are two tables that share nothing:
 * automatic blocks, which Shield sets, and manual blocks, which an admin sets. Both are bounded and have no lock
 * of their own.
 * <p>
 * Nothing is ever evicted: a full table takes no new key, so a flood of automatic blocks can neither push out a
 * live block nor touch a manual one. Room comes back when a block is removed: by {@link #sweep()}, by
 * {@link #unblock(AddressKey)}, or by a look at that very key after it ran out.
 */
public final class BlockList
{

    private final Table automatic;
    private final Table manual;
    private final Ticker ticker;

    /**
     * An empty list.
     *
     * @param maxAutomatic how many automatic blocks there can be at once, more than 0
     * @param maxManual how many manual blocks there can be at once, more than 0
     * @param ticker the clock
     */
    public BlockList(int maxAutomatic, int maxManual, Ticker ticker)
    {
        automatic = new Table( maxAutomatic );
        manual = new Table( maxManual );
        this.ticker = Preconditions.checkNotNull( ticker, "ticker" );
    }

    /**
     * Whether a key is blocked now. Asking never writes to a live block, so it cannot make a block longer; a
     * block that has run out is removed.
     *
     * @param key the address, or null
     * @return true when the key has a live manual or automatic block; false for no key
     */
    public boolean blocked(AddressKey key)
    {
        if ( key == null )
        {
            return false;
        }
        long now = ticker.read();
        return manual.live( key, now ) || automatic.live( key, now );
    }

    /**
     * Blocks a key unless it is blocked already. A live block, automatic or manual, is left exactly as it was:
     * blocking again never makes a block longer. When the automatic table is full the answer comes in constant
     * time, without a scan and without an eviction.
     *
     * @param key the address, or null
     * @param seconds how long a new block lasts; below 1 counts as 1
     * @param reason a short text, stored by reference and never copied or cut; null counts as empty
     * @return true when the key is blocked on return; false for no key and when the automatic table is full
     */
    public boolean blockAutomatic(AddressKey key, int seconds, String reason)
    {
        if ( key == null )
        {
            return false;
        }
        long now = ticker.read();
        if ( manual.live( key, now ) || automatic.live( key, now ) )
        {
            return true;
        }
        return automatic.add( key, block( now, seconds, reason ) );
    }

    /**
     * Blocks a key by hand. The latest word counts: a manual block the key already has is replaced, by a
     * shorter one too, and an automatic block of the key is removed, so that the key is listed once.
     *
     * @param key the address, or null
     * @param seconds how long the block lasts from now; below 1 counts as 1
     * @param reason a short text, stored by reference and never copied or cut; null counts as empty
     * @return true when the key is blocked on return; false for no key and when the key is new and the manual
     * table is full of live blocks
     */
    public boolean blockManual(AddressKey key, int seconds, String reason)
    {
        if ( key == null )
        {
            return false;
        }
        long now = ticker.read();
        if ( !manual.set( key, block( now, seconds, reason ), now ) )
        {
            return false;
        }
        automatic.take( key, now );
        return true;
    }

    /**
     * Removes the blocks of a key, manual and automatic.
     *
     * @param key the address, or null
     * @return true when a block that was still live was removed
     */
    public boolean unblock(AddressKey key)
    {
        if ( key == null )
        {
            return false;
        }
        long now = ticker.read();
        // both tables, no short-circuit: a key can be in both for an instant
        boolean wasManual = manual.take( key, now );
        boolean wasAutomatic = automatic.take( key, now );
        return wasManual || wasAutomatic;
    }

    /**
     * The live blocks: the manual ones first, then the automatic ones, in no further order. Nothing is sorted,
     * because the automatic table can be large and the caller shows a few.
     *
     * @param max how many entries at most; 0 or less gives an empty list
     * @return the entries; unmodifiable
     */
    public List<Entry> entries(int max)
    {
        List<Entry> found = new ArrayList<>();
        long now = ticker.read();
        manual.list( found, max, now, true );
        automatic.list( found, max, now, false );
        return Collections.unmodifiableList( found );
    }

    /**
     * Removes the blocks that have run out. It walks both tables, which takes tens of milliseconds when they are
     * full: never call it from a Netty thread.
     */
    public void sweep()
    {
        long now = ticker.read();
        manual.sweep( now );
        automatic.sweep( now );
    }

    /**
     * How many blocks there are.
     *
     * @return manual plus automatic entries; it may count a block that has run out until the next sweep
     */
    public int size()
    {
        return manual.size() + automatic.size();
    }

    private static Block block(long now, int seconds, String reason)
    {
        return new Block( now + TimeUnit.SECONDS.toNanos( Math.max( 1, seconds ) ), ( reason == null ) ? "" : reason );
    }

    /**
     * One live block, as {@link #entries(int)} lists it.
     *
     * @param key the blocked address
     * @param secondsLeft how long the block still lasts, rounded up, so never 0
     * @param manual true for a block set by hand, false for an automatic one
     * @param reason why the key is blocked; never null
     */
    public record Entry( AddressKey key, long secondsLeft, boolean manual, String reason )
    {
    }

    // expires: the clock value at which the block ends; the clock may wrap, so live is now - expires < 0 only
    private record Block( long expires, String reason )
    {
    }

    // One bounded table of blocks. A slot is taken in the counter before a key is mapped and given back only
    // after it was unmapped, so the number of mapped keys never exceeds the maximum.
    private static final class Table
    {

        private final ConcurrentHashMap<AddressKey, Block> blocks = new ConcurrentHashMap<>();
        private final AtomicInteger reserved = new AtomicInteger();
        private final int max;

        private Table(int max)
        {
            Preconditions.checkArgument( max > 0, "max must be positive" );
            this.max = max;
        }

        // whether the key has a live block; one that has run out is removed on the spot
        boolean live(AddressKey key, long now)
        {
            Block block = blocks.get( key );
            if ( block == null )
            {
                return false;
            }
            if ( now - block.expires() < 0 )
            {
                return true;
            }
            remove( key, block );
            return false;
        }

        // Maps the block unless the key is mapped. False when the table is full; true when the key is mapped on
        // return, by this call or by a thread that was first.
        boolean add(AddressKey key, Block block)
        {
            if ( !reserve() )
            {
                return false;
            }
            if ( blocks.putIfAbsent( key, block ) != null )
            {
                reserved.decrementAndGet();
            }
            return true;
        }

        // Maps the block in place of the one the key has, which needs no slot. A new key needs a slot: when there
        // is none the table is swept once, and false means it is full of live blocks.
        boolean set(AddressKey key, Block block, long now)
        {
            if ( blocks.replace( key, block ) != null )
            {
                return true;
            }
            if ( !reserve() )
            {
                sweep( now );
                if ( !reserve() )
                {
                    return false;
                }
            }
            if ( blocks.put( key, block ) != null )
            {
                // another thread mapped the key in between and its slot is enough
                reserved.decrementAndGet();
            }
            return true;
        }

        // removes the key's block; true when it was still live
        boolean take(AddressKey key, long now)
        {
            Block block = blocks.remove( key );
            if ( block == null )
            {
                return false;
            }
            reserved.decrementAndGet();
            return now - block.expires() < 0;
        }

        // adds the live blocks, in map order, until the list holds atMost entries
        void list(List<Entry> into, int atMost, long now, boolean manualTable)
        {
            for ( Map.Entry<AddressKey, Block> mapped : blocks.entrySet() )
            {
                if ( into.size() >= atMost )
                {
                    return;
                }
                Block block = mapped.getValue();
                long left = block.expires() - now;
                if ( left > 0 )
                {
                    // rounded up, so that a live block never shows 0
                    into.add( new Entry( mapped.getKey(), ( left + 999_999_999L ) / 1_000_000_000L, manualTable, block.reason() ) );
                }
            }
        }

        void sweep(long now)
        {
            for ( Map.Entry<AddressKey, Block> mapped : blocks.entrySet() )
            {
                Block block = mapped.getValue();
                if ( now - block.expires() >= 0 )
                {
                    remove( mapped.getKey(), block );
                }
            }
        }

        int size()
        {
            return blocks.size();
        }

        private boolean reserve()
        {
            if ( reserved.incrementAndGet() > max )
            {
                reserved.decrementAndGet();
                return false;
            }
            return true;
        }

        private void remove(AddressKey key, Block block)
        {
            if ( blocks.remove( key, block ) )
            {
                reserved.decrementAndGet();
            }
        }
    }
}
