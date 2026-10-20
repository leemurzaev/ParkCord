package com.parkcode.parkcord.shield;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class BlockListTest
{

    private static final AddressKey KEY = AddressKey.parse( "203.0.113.7" );
    private static final AddressKey V6 = AddressKey.parse( "2001:db8:1:2::1" );
    private final FakeTicker ticker = new FakeTicker();

    private static AddressKey key(long number)
    {
        return new AddressKey( false, number );
    }

    @Test
    public void testAutomaticBlockEndsOnTime()
    {
        BlockList blocks = new BlockList( 100000, 10000, ticker );
        assertFalse( blocks.blocked( KEY ) );
        assertTrue( blocks.blockAutomatic( KEY, 300, "10 strikes in 60 s" ) );
        assertTrue( blocks.blocked( KEY ) );
        assertFalse( blocks.blocked( V6 ) );
        ticker.advanceMillis( 299999 );
        assertTrue( blocks.blocked( KEY ) );
        assertEquals( 1, blocks.size() );
        ticker.advanceMillis( 1 );
        assertFalse( blocks.blocked( KEY ) );
        assertEquals( 0, blocks.size(), "an expired block is removed when it is looked at" );
    }

    @Test
    public void testAskingDoesNotMakeABlockLonger()
    {
        BlockList blocks = new BlockList( 100000, 10000, ticker );
        blocks.blockAutomatic( KEY, 300, "10 strikes in 60 s" );
        for ( int second = 1; second < 300; second++ )
        {
            ticker.advanceSeconds( 1 );
            assertTrue( blocks.blocked( KEY ) );
        }
        ticker.advanceSeconds( 1 );
        assertFalse( blocks.blocked( KEY ) );
    }

    @Test
    public void testBlockingAgainDoesNotMakeABlockLonger()
    {
        BlockList blocks = new BlockList( 100000, 10000, ticker );
        assertTrue( blocks.blockAutomatic( KEY, 300, "first" ) );
        ticker.advanceSeconds( 200 );
        assertTrue( blocks.blockAutomatic( KEY, 300, "second" ), "true means: the key is blocked" );
        assertEquals( List.of( new BlockList.Entry( KEY, 100, false, "first" ) ), blocks.entries( 50 ) );
        ticker.advanceSeconds( 100 );
        assertFalse( blocks.blocked( KEY ) );
    }

    @Test
    public void testManualBlockTakesOverAndCanBeReplaced()
    {
        BlockList blocks = new BlockList( 100000, 10000, ticker );
        blocks.blockAutomatic( KEY, 300, "10 strikes in 60 s" );
        assertTrue( blocks.blockManual( KEY, 3600, "manual" ) );
        assertEquals( List.of( new BlockList.Entry( KEY, 3600, true, "manual" ) ), blocks.entries( 50 ) );
        assertTrue( blocks.blockAutomatic( KEY, 300, "10 strikes in 60 s" ) );
        assertEquals( 1, blocks.size(), "a key that is blocked by hand gets no automatic block as well" );
        assertEquals( List.of( new BlockList.Entry( KEY, 3600, true, "manual" ) ), blocks.entries( 50 ), "and the manual block is as it was" );
        ticker.advanceSeconds( 1800 );
        assertTrue( blocks.blockManual( KEY, 60, "shorter" ) );
        assertEquals( List.of( new BlockList.Entry( KEY, 60, true, "shorter" ) ), blocks.entries( 50 ) );
        ticker.advanceSeconds( 60 );
        assertFalse( blocks.blocked( KEY ) );
    }

    @Test
    public void testUnblock()
    {
        BlockList blocks = new BlockList( 100000, 10000, ticker );
        assertFalse( blocks.unblock( KEY ) );
        blocks.blockAutomatic( KEY, 300, "10 strikes in 60 s" );
        assertTrue( blocks.unblock( KEY ) );
        assertFalse( blocks.blocked( KEY ) );
        assertFalse( blocks.unblock( KEY ) );
        blocks.blockManual( V6, 300, "manual" );
        assertTrue( blocks.unblock( V6 ) );
        assertEquals( 0, blocks.size() );
        blocks.blockAutomatic( KEY, 300, "10 strikes in 60 s" );
        ticker.advanceSeconds( 300 );
        assertFalse( blocks.unblock( KEY ), "it had already run out" );
        assertEquals( 0, blocks.size() );
    }

    @Test
    public void testEntries()
    {
        BlockList blocks = new BlockList( 100000, 10000, ticker );
        blocks.blockAutomatic( KEY, 300, "10 strikes in 60 s" );
        blocks.blockManual( V6, 86400, null );
        ticker.advanceMillis( 500 );
        assertEquals( List.of( new BlockList.Entry( V6, 86400, true, "" ), new BlockList.Entry( KEY, 300, false, "10 strikes in 60 s" ) ), blocks.entries( 50 ),
                "manual first; seconds left are rounded up" );
        assertEquals( List.of( new BlockList.Entry( V6, 86400, true, "" ) ), blocks.entries( 1 ) );
        assertEquals( List.of(), blocks.entries( 0 ) );
        ticker.advanceMillis( 299500 );
        assertEquals( List.of( new BlockList.Entry( V6, 86100, true, "" ) ), blocks.entries( 50 ), "an expired block is not listed" );
        assertThrows( UnsupportedOperationException.class, () -> blocks.entries( 50 ).clear() );
    }

    @Test
    public void testSweep()
    {
        BlockList blocks = new BlockList( 100000, 10000, ticker );
        blocks.blockAutomatic( key( 1 ), 300, "10 strikes in 60 s" );
        blocks.blockManual( key( 2 ), 600, "manual" );
        ticker.advanceSeconds( 300 );
        assertEquals( 2, blocks.size(), "nobody has looked yet" );
        blocks.sweep();
        assertEquals( 1, blocks.size() );
        ticker.advanceSeconds( 300 );
        blocks.sweep();
        assertEquals( 0, blocks.size() );
    }

    @Test
    public void testFullTableTakesNoNewAutomaticBlockAndEvictsNone()
    {
        BlockList blocks = new BlockList( 1000, 10, ticker );
        for ( int i = 0; i < 1000; i++ )
        {
            assertTrue( blocks.blockAutomatic( key( i ), 300, "10 strikes in 60 s" ) );
        }
        ticker.advanceSeconds( 299 );
        assertFalse( blocks.blockAutomatic( key( 1000 ), 300, "10 strikes in 60 s" ) );
        assertFalse( blocks.blocked( key( 1000 ) ) );
        assertTrue( blocks.blockAutomatic( key( 5 ), 300, "10 strikes in 60 s" ), "already blocked, so true even when full" );
        for ( int i = 0; i < 1000; i++ )
        {
            assertTrue( blocks.blocked( key( i ) ), "a live block was evicted" );
        }
        ticker.advanceSeconds( 1 );
        assertFalse( blocks.blockAutomatic( key( 1000 ), 300, "10 strikes in 60 s" ), "room is made by the sweep, not on this path" );
        blocks.sweep();
        assertEquals( 0, blocks.size() );
        assertTrue( blocks.blockAutomatic( key( 1000 ), 300, "10 strikes in 60 s" ) );
    }

    @Test
    public void testFullManualTableRefuses()
    {
        BlockList blocks = new BlockList( 1000, 3, ticker );
        assertTrue( blocks.blockManual( key( 1 ), 60, "manual" ) );
        assertTrue( blocks.blockManual( key( 2 ), 600, "manual" ) );
        assertTrue( blocks.blockManual( key( 3 ), 600, "manual" ) );
        assertFalse( blocks.blockManual( key( 4 ), 600, "manual" ) );
        assertFalse( blocks.blocked( key( 4 ) ) );
        assertTrue( blocks.blockManual( key( 2 ), 900, "again" ), "replacing needs no room" );
        assertTrue( blocks.blockAutomatic( key( 4 ), 300, "10 strikes in 60 s" ), "the automatic table is a table of its own" );
        ticker.advanceSeconds( 60 );
        assertTrue( blocks.blockManual( key( 5 ), 600, "manual" ), "an expired manual block makes room at once" );
        assertFalse( blocks.blockManual( key( 6 ), 600, "manual" ) );
    }

    @Test
    public void testManualBlockSurvivesAMillionAutomaticOnes()
    {
        BlockList blocks = new BlockList( 100000, 10000, ticker );
        assertTrue( blocks.blockManual( KEY, 86400, "manual" ) );
        int accepted = 0;
        for ( int i = 0; i < 1000000; i++ )
        {
            if ( blocks.blockAutomatic( key( i ), 300, "10 strikes in 60 s" ) )
            {
                accepted++;
            }
        }
        assertEquals( 100000, accepted );
        assertEquals( 100001, blocks.size() );
        assertTrue( blocks.blocked( KEY ) );
        assertEquals( new BlockList.Entry( KEY, 86400, true, "manual" ), blocks.entries( 1 ).get( 0 ) );
        assertTrue( blocks.blocked( key( 0 ) ), "the first automatic block is still there" );
        assertTrue( blocks.blocked( key( 99999 ) ) );
        assertFalse( blocks.blocked( key( 100000 ) ), "the table was full" );
    }

    @Test
    public void testNullKey()
    {
        BlockList blocks = new BlockList( 100000, 10000, ticker );
        assertFalse( blocks.blocked( null ) );
        assertFalse( blocks.blockAutomatic( null, 300, "10 strikes in 60 s" ) );
        assertFalse( blocks.blockManual( null, 300, "manual" ) );
        assertFalse( blocks.unblock( null ) );
        assertEquals( 0, blocks.size() );
    }

    @Test
    public void testClockNearOverflow()
    {
        ticker.set( Long.MAX_VALUE - TimeUnit.SECONDS.toNanos( 5 ) );
        BlockList blocks = new BlockList( 100000, 10000, ticker );
        blocks.blockAutomatic( KEY, 300, "10 strikes in 60 s" );
        assertTrue( blocks.blocked( KEY ), "the end of the block is negative already and the clock value is not, the block is live" );
        ticker.advanceSeconds( 299 );
        assertTrue( blocks.blocked( KEY ) );
        assertEquals( List.of( new BlockList.Entry( KEY, 1, false, "10 strikes in 60 s" ) ), blocks.entries( 50 ) );
        blocks.sweep();
        assertEquals( 1, blocks.size() );
        ticker.advanceSeconds( 1 );
        assertFalse( blocks.blocked( KEY ) );
    }

    @Test
    public void testConcurrentBlocksFillExactlyToTheMaximum() throws Exception
    {
        BlockList blocks = new BlockList( 100000, 10000, ticker );
        blocks.blockManual( KEY, 86400, "manual" );
        AtomicInteger accepted = new AtomicInteger();
        Threads.run( 8, (thread) ->
        {
            for ( int i = 0; i < 50000; i++ )
            {
                if ( blocks.blockAutomatic( key( thread * 50000L + i ), 300, "10 strikes in 60 s" ) )
                {
                    accepted.incrementAndGet();
                }
                assertTrue( blocks.blocked( KEY ) );
            }
        } );
        assertEquals( 100000, accepted.get() );
        assertEquals( 100001, blocks.size() );
    }

    @Test
    public void testConcurrentBlocksOfOneKeyTakeOneSlot() throws Exception
    {
        BlockList blocks = new BlockList( 10, 10, ticker );
        Threads.run( 8, (thread) ->
        {
            for ( int i = 0; i < 10000; i++ )
            {
                assertTrue( blocks.blockAutomatic( KEY, 300, "10 strikes in 60 s" ) );
                if ( i % 100 == thread )
                {
                    blocks.unblock( KEY );
                }
            }
        } );
        blocks.unblock( KEY );
        assertEquals( 0, blocks.size() );
        for ( int i = 0; i < 10; i++ )
        {
            assertTrue( blocks.blockAutomatic( key( i ), 300, "10 strikes in 60 s" ), "a slot was lost in the race" );
        }
        assertFalse( blocks.blockAutomatic( key( 10 ), 300, "10 strikes in 60 s" ), "a slot was gained in the race" );
    }
}
