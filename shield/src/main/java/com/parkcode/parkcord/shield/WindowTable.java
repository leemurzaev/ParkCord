package com.parkcode.parkcord.shield;

import com.google.common.base.Preconditions;
import com.google.common.base.Ticker;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The engine of {@link RateWindow} and {@link StrikeTable}: a bounded table with one fixed window per key. A
 * window opens with the key's first hit and ends the given number of seconds later, whatever happens in between.
 * <p>
 * It has no lock of its own and no caller ever waits. The number of keys never exceeds the maximum: when the
 * table is full, the windows that have ended go first and then the window that was opened first, however often
 * it was hit. A key that lost its window starts again at zero, so a full table fails open.
 * <p>
 * Under concurrency a hit can be lost only when it lands on a window in the instant that window ends or is
 * evicted. Without expiry and eviction no hit is lost.
 */
final class WindowTable
{

    // the map is the truth, the queue is only the order of age
    private final ConcurrentHashMap<AddressKey, Window> windows = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Window> order = new ConcurrentLinkedQueue<>();
    // A slot is taken here before a key is mapped and given back only after it was unmapped, so the number of
    // mapped keys never exceeds the maximum, at any moment and with any number of threads.
    private final AtomicInteger reserved = new AtomicInteger();
    // the one piece of exclusive work, taking ended windows out of the queue (trim and the queue pass of the
    // sweep): a thread wins it or skips it
    private final AtomicBoolean trimming = new AtomicBoolean();
    private final int maxKeys;
    private final Ticker ticker;

    WindowTable(int maxKeys, Ticker ticker)
    {
        Preconditions.checkArgument( maxKeys > 0, "maxKeys must be positive" );
        this.maxKeys = maxKeys;
        this.ticker = Preconditions.checkNotNull( ticker, "ticker" );
    }

    // Counts one hit of the key in its window, which lasts the given seconds (below 1 counts as 1) from its first
    // hit. Returns the count after this hit, at most Integer.MAX_VALUE, or 0 when the key could not be tracked:
    // no key, or the queue was momentarily empty while the table was full.
    int hit(AddressKey key, int seconds)
    {
        if ( key == null )
        {
            return 0;
        }
        long now = ticker.read();
        Window window = windows.get( key );
        if ( window != null && now - window.expires >= 0 )
        {
            remove( window );
            window = null;
        }
        if ( window == null )
        {
            window = open( key, now, seconds );
            if ( window == null )
            {
                return 0;
            }
        }
        return window.updateAndGet( (count) -> ( count == Integer.MAX_VALUE ) ? count : count + 1 );
    }

    // Removes the key's window, if there is one. Its queue entry stays until the window would have ended, which
    // is harmless because every removal is conditional on the window itself.
    void clear(AddressKey key)
    {
        if ( key == null )
        {
            return;
        }
        Window window = windows.get( key );
        if ( window != null )
        {
            remove( window );
        }
    }

    // Removes every window that has ended. It walks the whole table, which takes tens of milliseconds when the
    // table is full: never call it from a Netty thread.
    void sweep()
    {
        long now = ticker.read();
        // the map pass comes first and is the one that frees slots: nothing stays mapped past its end, even if
        // its queue entry was lost
        for ( Window window : windows.values() )
        {
            if ( now - window.expires >= 0 )
            {
                remove( window );
            }
        }
        // The queue pass takes entries out of the queue, so it runs only with the flag of trim(), which reads the
        // head in two steps. When the flag is taken this run skips the pass and nobody waits: the slots are free
        // already, and the next trim or sweep removes the queue entries.
        if ( trimming.compareAndSet( false, true ) )
        {
            try
            {
                order.removeIf( (window) -> now - window.expires >= 0 );
            } finally
            {
                trimming.set( false );
            }
        }
    }

    int size()
    {
        return windows.size();
    }

    // Maps a new window for the key and returns it, or the window another thread mapped first, or null when the
    // table is full and there was nothing to evict.
    private Window open(AddressKey key, long now, int seconds)
    {
        trim( now );
        while ( reserved.incrementAndGet() > maxKeys )
        {
            reserved.decrementAndGet();
            Window oldest = order.poll();
            if ( oldest == null )
            {
                return null;
            }
            remove( oldest );
        }
        Window fresh = new Window( key, now + TimeUnit.SECONDS.toNanos( Math.max( 1, seconds ) ) );
        Window raced = windows.putIfAbsent( key, fresh );
        if ( raced != null )
        {
            reserved.decrementAndGet();
            return raced;
        }
        // map first, queue second: a mapped window always has its queue entry, apart from this instant
        order.add( fresh );
        return fresh;
    }

    // Takes the windows that have ended off the head of the queue. With one window length per table every ended
    // window is at the head, so the queue cleans itself even if the sweeper never ran.
    // Peek and poll are two steps: if another thread took the head in between, the poll would take the next window,
    // which may still be open. The flag keeps the queue pass of the sweep out, so that cannot happen while the
    // table has room. Only the eviction in open() still takes from the queue without the flag, and it runs only
    // when the table is full, where a live window may go: fails open.
    private void trim(long now)
    {
        if ( !trimming.compareAndSet( false, true ) )
        {
            return;
        }
        try
        {
            Window head;
            while ( ( head = order.peek() ) != null && now - head.expires >= 0 )
            {
                head = order.poll();
                if ( head != null )
                {
                    remove( head );
                }
            }
        } finally
        {
            trimming.set( false );
        }
    }

    private void remove(Window window)
    {
        if ( windows.remove( window.key, window ) )
        {
            reserved.decrementAndGet();
        }
    }

    // A window is its own counter. It must keep identity equality: the conditional removal relies on it, so this
    // is not a record.
    private static final class Window extends AtomicInteger
    {

        private final AddressKey key;
        // the clock value at which the window ends; the clock may wrap, so compare with now - expires >= 0 only
        private final long expires;

        private Window(AddressKey key, long expires)
        {
            this.key = key;
            this.expires = expires;
        }
    }
}
