package com.parkcode.parkcord.shield;

import com.google.common.base.Ticker;

/**
 * The strikes of each key within a fixed window, which begins with the key's first strike. Bounded, without a
 * lock of its own, and it fails open: a key that was evicted from the full table starts again at one.
 * <p>
 * It knows nothing of blocks: what a number of strikes leads to is the caller's decision.
 */
public final class StrikeTable
{

    private final WindowTable table;

    /**
     * An empty table.
     *
     * @param maxKeys how many keys are counted at once, more than 0; when there are more, the windows that have
     * ended go first and then the window that was opened first
     * @param ticker the clock
     */
    public StrikeTable(int maxKeys, Ticker ticker)
    {
        table = new WindowTable( maxKeys, ticker );
    }

    /**
     * Counts one strike. Each count of a window is handed out exactly once, also under concurrency, so exactly
     * one caller sees a given number.
     *
     * @param key the address, or null
     * @param withinSeconds the length of a window that this call opens; below 1 counts as 1
     * @return the strikes of the key in its window, this one included; 0 for no key; 1 when the full table could
     * not hold the key, because an untracked strike is still one strike
     */
    public int strike(AddressKey key, int withinSeconds)
    {
        return ( key == null ) ? 0 : Math.max( 1, table.hit( key, withinSeconds ) );
    }

    /**
     * Forgets the strikes of a key.
     *
     * @param key the address, or null
     */
    public void clear(AddressKey key)
    {
        table.clear( key );
    }

    /**
     * Removes the windows that have ended. It walks the whole table, which takes tens of milliseconds when the
     * table is full: never call it from a Netty thread.
     */
    public void sweep()
    {
        table.sweep();
    }

    /**
     * How many keys have strikes now.
     *
     * @return the number of windows; an estimate while other threads change the table
     */
    public int size()
    {
        return table.size();
    }
}
