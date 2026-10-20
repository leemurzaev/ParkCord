package com.parkcode.parkcord.shield;

import com.google.common.base.Preconditions;
import com.google.common.base.Ticker;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Decides from the rate of connections whether the proxy is under attack. By itself it switches on when more
 * connections than the limit arrived in every one of {@code attackTriggerSeconds} seconds in a row, none of them
 * inside the start grace, and off again after {@code attackCalmSeconds} seconds in a row without one above the
 * limit. An operator can force it on or off; the rate is watched all the same, so going back to automatic shows
 * at once what it has been doing.
 * <p>
 * Seconds are counted from the construction of this object, whatever the clock shows. There is no thread and no
 * sweep: a second is closed only by the next call of {@link #connection(ShieldSettings)} or
 * {@link #active(ShieldSettings)}, so {@link #peakPerSecond()} and {@link #lastChange()} report what the rate
 * did as of the last such call.
 * <p>
 * No caller of those two methods ever waits. Counting a connection takes no lock; closing a second is the one
 * piece of exclusive work, and a thread wins it or skips it.
 */
public final class AttackMode
{

    private static final long SECOND = TimeUnit.SECONDS.toNanos( 1 );
    private final Ticker ticker;
    // the clock value at construction: seconds are numbered from here, and the clock may wrap
    private final long start;
    // the connections of the open second
    private final LongAdder count = new LongAdder();
    // Seconds are closed and the override is changed with this lock held. Of the fields below, the volatile ones
    // are written with it held and read without it, and the others are used with it held only.
    private final ReentrantLock lock = new ReentrantLock();
    // the number of the open second
    private volatile long current;
    private volatile Control override = Control.AUTO;
    // what the detector says, also while an operator forces something else
    private volatile boolean auto;
    private volatile int peak;
    private volatile String lastChange;
    // hot seconds closed in a row; advance() says what makes a second hot
    private long hotRun;
    // seconds that were not hot closed in a row, counted while the detector says on
    private long calmRun;
    // the highest second of the present hot run; it joins the peak when the run ends without switching on
    private int runMax;

    /**
     * Attack mode off and nothing forced. The start grace begins with this call.
     *
     * @param ticker the clock
     */
    public AttackMode(Ticker ticker)
    {
        this.ticker = Preconditions.checkNotNull( ticker, "ticker" );
        start = ticker.read();
    }

    /**
     * Counts one connection that passed the accept stage.
     *
     * @param settings the settings in use; they decide about the seconds that this call closes
     */
    public void connection(ShieldSettings settings)
    {
        advance( settings );
        count.increment();
    }

    /**
     * Whether attack mode is on. Asking counts nothing. It does close the seconds that are over, which is how
     * attack mode switches off when no connection arrives any more.
     *
     * @param settings the settings in use; they decide about the seconds that this call closes
     * @return true when an operator forced it on, or when nothing is forced and the rate switched it on
     */
    public boolean active(ShieldSettings settings)
    {
        advance( settings );
        // one read of the field: with two, an override( ON ) between them could give false although the answer is
        // true before it and after it
        Control mode = override;
        return mode == Control.ON || ( mode == Control.AUTO && auto );
    }

    /**
     * Forces attack mode on or off, or hands the decision back to the rate.
     *
     * @param value what holds from now on
     */
    public void override(Control value)
    {
        Preconditions.checkNotNull( value, "value" );
        // an operator's command, not the accept path: it may wait for a second that is being closed
        lock.lock();
        try
        {
            boolean before = override == Control.ON || ( override == Control.AUTO && auto );
            override = value;
            boolean after = override == Control.ON || ( override == Control.AUTO && auto );
            if ( before != after )
            {
                if ( value == Control.AUTO )
                {
                    lastChange = ( after ) ? "on: back to automatic" : "off: back to automatic";
                } else
                {
                    lastChange = ( after ) ? "on: set by an operator" : "off: set by an operator";
                }
            }
        } finally
        {
            lock.unlock();
        }
    }

    /**
     * What an operator set last.
     *
     * @return {@link Control#AUTO} unless {@link #override(Control)} set something else
     */
    public Control override()
    {
        return override;
    }

    /**
     * The highest number of connections in one whole second outside an attack, as a guide for the limit. A second
     * counts when it closes while the rate has not switched attack mode on and no operator has forced it on. The
     * seconds above the limit that switched it on do not count; a shorter run above the limit does.
     *
     * @return the peak, 0 at first and at most {@code Integer.MAX_VALUE}
     */
    public int peakPerSecond()
    {
        return peak;
    }

    /**
     * Why the answer of {@link #active(ShieldSettings)} last changed. A change of the rate that an operator's
     * choice hides, and a command that changes nothing, leave it as it is.
     *
     * @return null while the answer has never changed; otherwise a text that begins with "on: " or "off: " and
     * names the cause
     */
    public String lastChange()
    {
        return lastChange;
    }

    // Closes the seconds that are over and opens the present one. No thread waits here.
    // A caller that loses the tryLock counts or answers from the state before the roll, which is the answer it
    // would have got a moment earlier.
    private void advance(ShieldSettings settings)
    {
        long second = ( ticker.read() - start ) / SECOND;
        if ( second <= current || !lock.tryLock() )
        {
            return;
        }
        try
        {
            long open = current;
            if ( second > open )
            {
                // an increment that races with this line is in the second that closes or in the next one, never lost
                int rate = (int) Math.min( count.sumThenReset(), Integer.MAX_VALUE );
                int limit = settings.attackConnectionsPerSecond();
                // hot: above the limit and past the start grace; a limit of 0 or less is never exceeded
                step( limit > 0 && rate > limit && open >= settings.attackStartGraceSeconds(), rate, 1, settings );
                if ( second - open > 1 )
                {
                    // the seconds without any call, in one step: the clock may jump by hours
                    step( false, 0, second - open - 1, settings );
                }
                current = second;
            }
        } finally
        {
            lock.unlock();
        }
    }

    // lock held: "seconds" whole seconds are over, each with "rate" connections
    private void step(boolean hot, int rate, long seconds, ShieldSettings settings)
    {
        if ( hot )
        {
            calmRun = 0;
            hotRun += seconds;
            int trigger = Math.max( 1, settings.attackTriggerSeconds() );
            if ( !auto )
            {
                runMax = Math.max( runMax, rate );
                if ( hotRun >= trigger )
                {
                    runMax = 0;
                    detector( true, "on: more than " + settings.attackConnectionsPerSecond() + " connections/s for " + trigger + " s" );
                }
            }
            return;
        }
        hotRun = 0;
        if ( auto )
        {
            int calm = Math.max( 1, settings.attackCalmSeconds() );
            calmRun += seconds;
            if ( calmRun >= calm )
            {
                calmRun = 0;
                detector( false, "off: calm for " + calm + " s" );
            }
        } else if ( override != Control.ON )
        {
            peak = Math.max( peak, Math.max( rate, runMax ) );
        }
        runMax = 0;
    }

    // lock held: the detector's own state; it is a switch only while nothing is forced
    private void detector(boolean on, String why)
    {
        auto = on;
        if ( override == Control.AUTO )
        {
            lastChange = why;
        }
    }

    /**
     * What an operator chose: {@link #AUTO} lets the rate decide, {@link #ON} and {@link #OFF} hold attack mode
     * there whatever the rate does.
     */
    public enum Control
    {

        AUTO,
        ON,
        OFF
    }
}
