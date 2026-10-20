package com.parkcode.parkcord.shield;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class WindowTableTest
{

    private static final AddressKey ENDED = AddressKey.parse( "203.0.113.7" );
    private static final AddressKey OPEN = AddressKey.parse( "203.0.113.8" );
    private static final AddressKey THIRD = AddressKey.parse( "203.0.113.9" );
    // One round is one chance for a sweep to take the ended head of the queue between the peek and the poll of a
    // trim. While the sweep could do that, 7 to 17 of 100 rounds lost the open window on a machine with 6 cores,
    // but only once the JIT compiler was done with this class: after the other test classes had run in the same
    // JVM, the first loss came as late as round 23,286 (43 runs). 250,000 rounds are 10 times that and take less
    // than a second. More rounds would only bring a very busy machine closer to the 60 seconds of Threads.
    private static final int ROUNDS = 250000;
    private final FakeTicker ticker = new FakeTicker();

    @Test
    public void testSweepAndTrimTogetherNeverDropAnOpenWindow() throws Exception
    {
        // the table of the round that is running; null while the first thread checks it and sets up the next one
        AtomicReference<WindowTable> running = new AtomicReference<>();
        Threads.run( 2, (thread) ->
        {
            for ( int round = 1; round <= ROUNDS; round++ )
            {
                if ( thread == 0 )
                {
                    sweepRound( running, round );
                } else
                {
                    trimRound( running );
                }
            }
        } );
    }

    // One round on the first thread: a table with one window that has ended and one that is open, then the sweep
    // together with the hit of the second thread, then the look at the open window.
    // The sweep has the longer way to the queue, because it walks the map first, so it is the call that starts
    // first: the thread that hands the table over sweeps and the thread that takes it hits. The other way round
    // the two calls met in fewer than 30 of 1,000,000 rounds.
    private void sweepRound(AtomicReference<WindowTable> running, int round)
    {
        WindowTable table = new WindowTable( 1000, ticker );
        table.hit( ENDED, 1 );
        table.hit( OPEN, 1000 );
        table.hit( OPEN, 1000 );
        ticker.advanceSeconds( 1 );
        // the table holds 2 of 1,000 keys: ENDED has ended and is the head of the queue, OPEN is open with 2 hits
        running.set( table );
        table.sweep();
        await( running, false );
        assertEquals( 3, table.hit( OPEN, 1000 ), () -> "round " + round + ": the open window was dropped although the table has room" );
    }

    // One round on the second thread: the hit on a third key opens a window, and that trims the queue.
    private static void trimRound(AtomicReference<WindowTable> running)
    {
        WindowTable table = await( running, true );
        table.hit( THIRD, 1000 );
        running.set( null );
    }

    // Waits without sleeping until the other thread has handed a table over (present) or has handed it back.
    // A hang ends after the 60 seconds of Threads: its interrupt ends this loop.
    private static WindowTable await(AtomicReference<WindowTable> running, boolean present)
    {
        WindowTable table = running.get();
        while ( ( table != null ) != present )
        {
            if ( Thread.currentThread().isInterrupted() )
            {
                throw new IllegalStateException( "interrupted while waiting for the other thread" );
            }
            Thread.onSpinWait();
            table = running.get();
        }
        return table;
    }
}
