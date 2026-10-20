package com.parkcode.parkcord.shield;

import com.google.common.base.Ticker;

/**
 * A limit per key and fixed window: so many times within the seconds that follow the key's first time. Bounded,
 * without a lock of its own, and it fails open: a key the table cannot hold is allowed.
 * <p>
 * It knows nothing of strikes or blocks: a refusal here has no other consequence.
 */
public final class RateWindow
{

    private final WindowTable table;

    /**
     * An empty table.
     *
     * @param maxKeys how many keys are counted at once, more than 0; when there are more, the windows that have
     * ended go first and then the window that was opened first
     * @param ticker the clock
     */
    public RateWindow(int maxKeys, Ticker ticker)
    {
        table = new WindowTable( maxKeys, ticker );
    }

    /**
     * Counts one more time for the key and says whether it is within the limit. A refused call is counted too;
     * that changes nothing, because the window ends at its fixed time.
     *
     * @param key the address, or null
     * @param limit how many times are allowed within one window; 0 or less switches the limit off
     * @param seconds the length of a window that this call opens; below 1 counts as 1
     * @return false when the key is over the limit; true otherwise, also for no key, for a limit that is off
     * (nothing is stored then) and for a key the full table could not hold
     */
    public boolean tryAcquire(AddressKey key, int limit, int seconds)
    {
        return limit <= 0 || table.hit( key, seconds ) <= limit;
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
     * How many keys are counted now.
     *
     * @return the number of windows; an estimate while other threads change the table
     */
    public int size()
    {
        return table.size();
    }
}
