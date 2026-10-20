package com.parkcode.parkcord.shield;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class AttackModeTest
{

    private final FakeTicker ticker = new FakeTicker();
    private final AttackMode attack = new AttackMode( ticker );
    // design 4.1: more than 60 a second, 3 seconds in a row, not in the first 120 seconds, off after 30 calm seconds
    private ShieldSettings settings = ShieldSettings.defaults();

    // one whole second with this many connections
    private void second(int connections)
    {
        for ( int i = 0; i < connections; i++ )
        {
            attack.connection( settings );
        }
        ticker.advanceSeconds( 1 );
    }

    private void seconds(int howMany, int connectionsEach)
    {
        for ( int i = 0; i < howMany; i++ )
        {
            second( connectionsEach );
        }
    }

    // past the grace period, then seconds 120, 121 and 122 above the limit: on at second 123
    private void switchOn()
    {
        seconds( 120, 0 );
        seconds( 3, 61 );
        assertTrue( attack.active( settings ), "on after 3 seconds above 60" );
    }

    private static ShieldSettings attackSettings(int perSecond, int triggerSeconds, int graceSeconds, int calmSeconds)
    {
        ShieldSettings d = ShieldSettings.defaults();
        return new ShieldSettings( d.enabled(), d.mode(), d.exemptions(), d.connectionsLimit(), d.connectionsSeconds(), d.pingsLimit(), d.pingsSeconds(),
                d.newAccountsPerIp(), d.nameBlacklist(), d.afterStrikes(), d.strikeSeconds(), d.blockSeconds(), perSecond, triggerSeconds, graceSeconds, calmSeconds,
                d.reconnectCheck(), d.fastChatMillis(), d.sameMessageAccounts(), d.sameMessageSeconds(), d.sameMessageMinLength() );
    }

    @Test
    public void testOffAtStart()
    {
        assertFalse( attack.active( settings ) );
        assertEquals( AttackMode.Control.AUTO, attack.override() );
        assertEquals( 0, attack.peakPerSecond() );
        assertNull( attack.lastChange() );
    }

    @Test
    public void testSustainedRateSwitchesOn()
    {
        seconds( 120, 0 );
        seconds( 2, 61 );
        assertFalse( attack.active( settings ), "2 seconds above the limit are not enough" );
        second( 61 );
        assertTrue( attack.active( settings ) );
        assertEquals( "on: more than 60 connections/s for 3 s", attack.lastChange() );
    }

    @Test
    public void testExactlyTheLimitIsNotEnough()
    {
        seconds( 120, 0 );
        seconds( 10, 60 );
        assertFalse( attack.active( settings ) );
        assertEquals( 60, attack.peakPerSecond() );
    }

    @Test
    public void testOneSecondSpikeDoesNotSwitchOn()
    {
        seconds( 120, 0 );
        second( 5000 );
        seconds( 5, 10 );
        assertFalse( attack.active( settings ) );
        assertNull( attack.lastChange() );
        assertEquals( 5000, attack.peakPerSecond(), "a spike that did not switch it on is part of the peak" );
    }

    @Test
    public void testHotSecondsMustBeInARow()
    {
        seconds( 120, 0 );
        seconds( 2, 61 );
        second( 60 );
        seconds( 2, 61 );
        second( 60 );
        assertFalse( attack.active( settings ) );
    }

    @Test
    public void testRestartBurstInsideGraceDoesNotSwitchOn()
    {
        seconds( 20, 150 );
        assertFalse( attack.active( settings ), "everybody reconnects after a restart" );
        seconds( 100, 150 );
        assertFalse( attack.active( settings ), "second 119 is still inside the 120" );
        assertEquals( 150, attack.peakPerSecond() );
    }

    @Test
    public void testGraceSecondsDoNotCountTowardsTheRun()
    {
        seconds( 118, 0 );
        seconds( 4, 150 );
        assertFalse( attack.active( settings ), "118 and 119 are grace, 120 and 121 make two" );
        second( 150 );
        assertTrue( attack.active( settings ) );
    }

    @Test
    public void testGraceRunsFromConstructionWhateverTheClockShows()
    {
        FakeTicker late = new FakeTicker();
        late.set( Long.MAX_VALUE - 60000000000L );
        AttackMode restarted = new AttackMode( late );
        for ( int i = 0; i < 120; i++ )
        {
            for ( int j = 0; j < 150; j++ )
            {
                restarted.connection( settings );
            }
            late.advanceSeconds( 1 );
        }
        assertFalse( restarted.active( settings ), "150 a second inside the start grace is not an attack" );
        for ( int i = 0; i < 3; i++ )
        {
            for ( int j = 0; j < 150; j++ )
            {
                restarted.connection( settings );
            }
            late.advanceSeconds( 1 );
        }
        assertTrue( restarted.active( settings ), "3 seconds above the limit after the grace" );
    }

    @Test
    public void testLimitZeroNeverSwitchesOnByItself()
    {
        settings = attackSettings( 0, 3, 120, 30 );
        seconds( 120, 0 );
        seconds( 10, 1000 );
        assertFalse( attack.active( settings ) );
        assertEquals( 1000, attack.peakPerSecond() );
        attack.override( AttackMode.Control.ON );
        assertTrue( attack.active( settings ), "an operator can still switch it on" );
    }

    @Test
    public void testSwitchesOffAfterCalmTime()
    {
        switchOn();
        seconds( 29, 60 );
        assertTrue( attack.active( settings ), "29 calm seconds" );
        second( 60 );
        assertFalse( attack.active( settings ) );
        assertEquals( "off: calm for 30 s", attack.lastChange() );
    }

    @Test
    public void testHotSecondRestartsCalmTime()
    {
        switchOn();
        seconds( 29, 0 );
        second( 61 );
        seconds( 29, 0 );
        assertTrue( attack.active( settings ) );
        second( 0 );
        assertFalse( attack.active( settings ) );
    }

    @Test
    public void testSwitchesOffWithoutAnyTraffic()
    {
        switchOn();
        ticker.advanceSeconds( 29 );
        assertTrue( attack.active( settings ) );
        ticker.advanceSeconds( 1 );
        assertFalse( attack.active( settings ) );
    }

    @Test
    public void testOverrideOnSurvivesCalmTime()
    {
        attack.override( AttackMode.Control.ON );
        assertTrue( attack.active( settings ), "also inside the grace period" );
        assertEquals( "on: set by an operator", attack.lastChange() );
        ticker.advanceSeconds( 3600 );
        assertTrue( attack.active( settings ), "an hour without one connection" );
        assertEquals( AttackMode.Control.ON, attack.override() );
    }

    @Test
    public void testOverrideOffHoldsItOffUnderLoad()
    {
        attack.override( AttackMode.Control.OFF );
        seconds( 120, 0 );
        seconds( 10, 500 );
        assertFalse( attack.active( settings ) );
        assertNull( attack.lastChange(), "nothing switched" );
        attack.override( AttackMode.Control.AUTO );
        assertTrue( attack.active( settings ), "the rate was watched all along" );
        assertEquals( "on: back to automatic", attack.lastChange() );
    }

    @Test
    public void testEveryOverrideChangeSaysWhy()
    {
        attack.override( AttackMode.Control.ON );
        assertEquals( "on: set by an operator", attack.lastChange() );
        attack.override( AttackMode.Control.OFF );
        assertEquals( "off: set by an operator", attack.lastChange() );
        attack.override( AttackMode.Control.ON );
        attack.override( AttackMode.Control.AUTO );
        assertEquals( "off: back to automatic", attack.lastChange() );
        assertFalse( attack.active( settings ) );
        attack.override( AttackMode.Control.OFF );
        assertEquals( "off: back to automatic", attack.lastChange(), "a value that changes nothing is not a switch" );
    }

    @Test
    public void testPeakIsTheHighestSecondWhileOff()
    {
        second( 12 );
        second( 40 );
        second( 7 );
        attack.active( settings );
        assertEquals( 40, attack.peakPerSecond() );
    }

    @Test
    public void testPeakIgnoresAttackSeconds()
    {
        seconds( 120, 0 );
        second( 40 );
        seconds( 3, 5000 );
        seconds( 5, 9000 );
        assertTrue( attack.active( settings ) );
        assertEquals( 40, attack.peakPerSecond(), "neither the 3 seconds that switched it on nor the seconds while on" );
    }

    @Test
    public void testPeakIgnoresSecondsForcedOn()
    {
        attack.override( AttackMode.Control.ON );
        seconds( 3, 300 );
        attack.active( settings );
        assertEquals( 0, attack.peakPerSecond() );
    }

    @Test
    public void testAskingIsNotCounting()
    {
        for ( int i = 0; i < 10000; i++ )
        {
            attack.active( settings );
        }
        ticker.advanceSeconds( 1 );
        assertFalse( attack.active( settings ) );
        assertEquals( 0, attack.peakPerSecond() );
    }

    // what /parkcord reload does
    @Test
    public void testNewSettingsApplyFromTheNextSecond()
    {
        switchOn();
        settings = attackSettings( 0, 3, 120, 30 );
        seconds( 30, 5000 );
        assertFalse( attack.active( settings ), "with the limit off nothing is above it" );
    }

    @Test
    public void testNonsenseSettingsAreClamped()
    {
        settings = attackSettings( 60, 0, -5, 0 );
        second( 61 );
        assertTrue( attack.active( settings ), "trigger 0 counts as 1, a negative grace as none" );
        second( 0 );
        assertFalse( attack.active( settings ), "calm 0 counts as 1" );
    }

    @Test
    public void testConcurrentConnectionsAreAllCounted() throws Exception
    {
        Threads.run( 8, (thread) ->
        {
            for ( int i = 0; i < 25000; i++ )
            {
                attack.connection( settings );
            }
        } );
        ticker.advanceSeconds( 1 );
        attack.active( settings );
        assertEquals( 200000, attack.peakPerSecond() );
    }
}
