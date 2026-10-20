package com.parkcode.parkcord.shield;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class StrikeTableTest
{

    private static final AddressKey KEY = AddressKey.parse( "203.0.113.7" );
    private final FakeTicker ticker = new FakeTicker();

    private static AddressKey key(long number)
    {
        return new AddressKey( false, number );
    }

    @Test
    public void testCountsWithinTheWindow()
    {
        StrikeTable strikes = new StrikeTable( 200000, ticker );
        for ( int i = 1; i <= 10; i++ )
        {
            assertEquals( i, strikes.strike( KEY, 60 ) );
        }
        assertEquals( 1, strikes.strike( AddressKey.parse( "2001:db8:1:2::1" ), 60 ) );
        ticker.advanceSeconds( 59 );
        assertEquals( 11, strikes.strike( KEY, 60 ) );
        ticker.advanceSeconds( 1 );
        assertEquals( 1, strikes.strike( KEY, 60 ), "the window ends 60 seconds after its first strike" );
    }

    @Test
    public void testClear()
    {
        StrikeTable strikes = new StrikeTable( 200000, ticker );
        strikes.strike( KEY, 60 );
        strikes.strike( KEY, 60 );
        strikes.clear( KEY );
        assertEquals( 0, strikes.size() );
        assertEquals( 1, strikes.strike( KEY, 60 ) );
        strikes.clear( key( 1 ) );
        strikes.clear( null );
        assertEquals( 1, strikes.size() );
    }

    @Test
    public void testSweepRemovesWhatHasExpired()
    {
        StrikeTable strikes = new StrikeTable( 200000, ticker );
        strikes.strike( key( 1 ), 60 );
        ticker.advanceSeconds( 30 );
        strikes.strike( key( 2 ), 60 );
        ticker.advanceSeconds( 30 );
        assertEquals( 2, strikes.size() );
        strikes.sweep();
        assertEquals( 1, strikes.size() );
        ticker.advanceSeconds( 30 );
        strikes.sweep();
        assertEquals( 0, strikes.size() );
    }

    @Test
    public void testNullKey()
    {
        StrikeTable strikes = new StrikeTable( 200000, ticker );
        assertEquals( 0, strikes.strike( null, 60 ) );
        assertEquals( 0, strikes.size() );
    }

    @Test
    public void testAMillionKeysStayWithinTheMaximum()
    {
        StrikeTable strikes = new StrikeTable( 200000, ticker );
        for ( int i = 0; i < 1000000; i++ )
        {
            assertEquals( 1, strikes.strike( key( i ), 60 ) );
            assertTrue( strikes.size() <= 200000 );
        }
        assertEquals( 200000, strikes.size() );
        assertEquals( 2, strikes.strike( key( 999999 ), 60 ) );
        assertEquals( 2, strikes.strike( key( 800000 ), 60 ) );
        assertEquals( 1, strikes.strike( key( 799999 ), 60 ), "older keys were evicted: fails open" );
    }

    @Test
    public void testTheOldestWindowGoesFirst()
    {
        StrikeTable strikes = new StrikeTable( 3, ticker );
        strikes.strike( key( 1 ), 60 );
        ticker.advanceSeconds( 1 );
        strikes.strike( key( 2 ), 60 );
        ticker.advanceSeconds( 1 );
        strikes.strike( key( 3 ), 60 );
        ticker.advanceSeconds( 1 );
        assertEquals( 2, strikes.strike( key( 1 ), 60 ) );
        assertEquals( 1, strikes.strike( key( 4 ), 60 ) );
        assertEquals( 2, strikes.strike( key( 2 ), 60 ) );
        assertEquals( 2, strikes.strike( key( 3 ), 60 ) );
        assertEquals( 1, strikes.strike( key( 1 ), 60 ), "the window that began first went, however often it was hit" );
        assertEquals( 3, strikes.size() );
    }

    @Test
    public void testConcurrentStrikesLoseNothing() throws Exception
    {
        StrikeTable strikes = new StrikeTable( 200000, ticker );
        AtomicInteger highest = new AtomicInteger();
        Threads.run( 8, (thread) ->
        {
            for ( int i = 0; i < 10000; i++ )
            {
                highest.accumulateAndGet( strikes.strike( KEY, 60 ), Math::max );
            }
        } );
        assertEquals( 80000, highest.get() );
        assertEquals( 80001, strikes.strike( KEY, 60 ) );
        assertEquals( 1, strikes.size() );
    }
}
