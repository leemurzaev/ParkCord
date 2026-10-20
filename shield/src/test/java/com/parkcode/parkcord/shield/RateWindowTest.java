package com.parkcode.parkcord.shield;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class RateWindowTest
{

    private static final AddressKey KEY = AddressKey.parse( "203.0.113.7" );
    private final FakeTicker ticker = new FakeTicker();

    private static AddressKey key(long number)
    {
        return new AddressKey( false, number );
    }

    @Test
    public void testLimitWithinTheWindow()
    {
        RateWindow window = new RateWindow( 200000, ticker );
        for ( int i = 0; i < 30; i++ )
        {
            assertTrue( window.tryAcquire( KEY, 30, 10 ) );
        }
        assertFalse( window.tryAcquire( KEY, 30, 10 ) );
        assertTrue( window.tryAcquire( AddressKey.parse( "203.0.113.8" ), 30, 10 ), "every key has its own window" );
        ticker.advanceMillis( 9999 );
        assertFalse( window.tryAcquire( KEY, 30, 10 ) );
        ticker.advanceMillis( 1 );
        assertTrue( window.tryAcquire( KEY, 30, 10 ), "the window ends 10 seconds after its first connection" );
    }

    @Test
    public void testBurstOf100LeavesNoResidue()
    {
        RateWindow window = new RateWindow( 200000, ticker );
        int allowed = 0;
        for ( int i = 0; i < 100; i++ )
        {
            if ( window.tryAcquire( KEY, 30, 10 ) )
            {
                allowed++;
            }
        }
        assertEquals( 30, allowed );
        assertEquals( 1, window.size() );
        ticker.advanceSeconds( 10 );
        window.sweep();
        assertEquals( 0, window.size() );
        assertTrue( window.tryAcquire( KEY, 30, 10 ) );
    }

    @Test
    public void testLimitZeroIsOff()
    {
        RateWindow window = new RateWindow( 200000, ticker );
        for ( int i = 0; i < 1000; i++ )
        {
            assertTrue( window.tryAcquire( KEY, 0, 10 ) );
        }
        assertEquals( 0, window.size() );
    }

    @Test
    public void testNullKeyIsAllowed()
    {
        RateWindow window = new RateWindow( 200000, ticker );
        assertTrue( window.tryAcquire( null, 1, 10 ) );
        assertTrue( window.tryAcquire( null, 1, 10 ) );
        assertEquals( 0, window.size() );
    }

    @Test
    public void testExpiredWindowIsReplacedOnAccess()
    {
        RateWindow window = new RateWindow( 200000, ticker );
        assertTrue( window.tryAcquire( KEY, 1, 10 ) );
        assertFalse( window.tryAcquire( KEY, 1, 10 ) );
        ticker.advanceSeconds( 10 );
        assertTrue( window.tryAcquire( KEY, 1, 10 ) );
        assertFalse( window.tryAcquire( KEY, 1, 10 ) );
        assertEquals( 1, window.size() );
    }

    @Test
    public void testAMillionKeysStayWithinTheMaximum()
    {
        RateWindow window = new RateWindow( 200000, ticker );
        for ( int i = 0; i < 1000000; i++ )
        {
            assertTrue( window.tryAcquire( key( i ), 1, 10 ) );
            assertTrue( window.size() <= 200000 );
        }
        assertEquals( 200000, window.size() );
        assertFalse( window.tryAcquire( key( 999999 ), 1, 10 ), "the newest key is still counted" );
        assertFalse( window.tryAcquire( key( 800000 ), 1, 10 ), "the 200,000 newest keys are still counted" );
        assertTrue( window.tryAcquire( key( 799999 ), 1, 10 ), "older keys were evicted: fails open" );
        assertEquals( 200000, window.size() );
    }

    @Test
    public void testExpiredGoFirstThenTheOldest()
    {
        RateWindow window = new RateWindow( 3, ticker );
        window.tryAcquire( key( 1 ), 1, 10 );
        ticker.advanceSeconds( 5 );
        window.tryAcquire( key( 2 ), 1, 10 );
        window.tryAcquire( key( 3 ), 1, 10 );
        ticker.advanceSeconds( 5 );
        assertTrue( window.tryAcquire( key( 4 ), 1, 10 ) );
        assertEquals( 3, window.size() );
        assertFalse( window.tryAcquire( key( 2 ), 1, 10 ), "key 1 had expired, so no live window had to go" );
        assertFalse( window.tryAcquire( key( 3 ), 1, 10 ) );
        assertTrue( window.tryAcquire( key( 5 ), 1, 10 ) );
        assertFalse( window.tryAcquire( key( 3 ), 1, 10 ) );
        assertFalse( window.tryAcquire( key( 4 ), 1, 10 ) );
        assertTrue( window.tryAcquire( key( 2 ), 1, 10 ), "key 2 was the oldest live window and went for key 5" );
        assertEquals( 3, window.size() );
    }

    @Test
    public void testClockNearOverflow()
    {
        ticker.set( Long.MAX_VALUE - TimeUnit.SECONDS.toNanos( 5 ) );
        RateWindow window = new RateWindow( 200000, ticker );
        assertTrue( window.tryAcquire( KEY, 1, 10 ) );
        ticker.advanceSeconds( 3 );
        assertFalse( window.tryAcquire( KEY, 1, 10 ), "the end of the window is negative already and the clock value is not, the window is still open" );
        ticker.advanceSeconds( 6 );
        assertFalse( window.tryAcquire( KEY, 1, 10 ), "the clock value is negative now, the window is still open" );
        window.sweep();
        assertEquals( 1, window.size() );
        ticker.advanceSeconds( 1 );
        assertTrue( window.tryAcquire( KEY, 1, 10 ) );
    }

    @Test
    public void testConcurrentCallsOnOneKeyLoseNothing() throws Exception
    {
        RateWindow window = new RateWindow( 200000, ticker );
        AtomicInteger allowed = new AtomicInteger();
        Threads.run( 8, (thread) ->
        {
            for ( int i = 0; i < 100000; i++ )
            {
                if ( window.tryAcquire( KEY, 30, 10 ) )
                {
                    allowed.incrementAndGet();
                }
            }
        } );
        assertEquals( 30, allowed.get() );
        assertEquals( 1, window.size() );
    }

    @Test
    public void testConcurrentCallsOnManyKeysLoseNothing() throws Exception
    {
        RateWindow window = new RateWindow( 200000, ticker );
        AtomicInteger allowed = new AtomicInteger();
        Threads.run( 8, (thread) ->
        {
            for ( int round = 0; round < 100; round++ )
            {
                for ( int i = 0; i < 1000; i++ )
                {
                    if ( window.tryAcquire( key( i ), 50, 10 ) )
                    {
                        allowed.incrementAndGet();
                    }
                }
            }
        } );
        assertEquals( 50 * 1000, allowed.get() );
        assertEquals( 1000, window.size() );
    }

    @Test
    public void testConcurrentFloodStaysWithinTheMaximum() throws Exception
    {
        RateWindow window = new RateWindow( 1000, ticker );
        Threads.run( 8, (thread) ->
        {
            for ( int i = 0; i < 200000; i++ )
            {
                assertTrue( window.tryAcquire( key( thread * 1000000L + i ), 1, 10 ) );
            }
        } );
        assertTrue( window.size() <= 1000, "size " + window.size() );
        for ( int i = 0; i < 2000; i++ )
        {
            assertTrue( window.tryAcquire( key( 9000000L + i ), 1, 10 ) );
        }
        assertEquals( 1000, window.size(), "no slot was lost or gained in the flood" );
        assertFalse( window.tryAcquire( key( 9001999L ), 1, 10 ), "and new keys are still counted" );
    }
}
