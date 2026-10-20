package com.parkcode.parkcord.shield;

import static org.junit.jupiter.api.Assertions.*;
import com.google.common.net.InetAddresses;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class ShieldSettingsTest
{

    @Test
    public void testDefaultsAreTheDesignValues()
    {
        ShieldSettings defaults = ShieldSettings.defaults();
        assertEquals( true, defaults.enabled() );
        assertEquals( ShieldSettings.Mode.ENFORCE, defaults.mode() );
        assertEquals( new Exemptions( List.of(), true ), defaults.exemptions() );
        assertTrue( defaults.exemptions().exempt( InetAddresses.forString( "127.0.0.1" ) ) );
        assertTrue( defaults.exemptions().exempt( InetAddresses.forString( "10.1.2.3" ) ) );
        assertFalse( defaults.exemptions().exempt( InetAddresses.forString( "203.0.113.7" ) ) );
        assertEquals( 30, defaults.connectionsLimit() );
        assertEquals( 10, defaults.connectionsSeconds() );
        assertEquals( 20, defaults.pingsLimit() );
        assertEquals( 10, defaults.pingsSeconds() );
        assertEquals( 10, defaults.newAccountsPerIp() );
        assertEquals( Set.of( "mcstorm", "mcdown", "mcbot", "dropbot", "kingbot" ), defaults.nameBlacklist() );
        assertEquals( 10, defaults.afterStrikes() );
        assertEquals( 60, defaults.strikeSeconds() );
        assertEquals( 300, defaults.blockSeconds() );
        assertEquals( 60, defaults.attackConnectionsPerSecond() );
        assertEquals( 3, defaults.attackTriggerSeconds() );
        assertEquals( 120, defaults.attackStartGraceSeconds() );
        assertEquals( 30, defaults.attackCalmSeconds() );
        assertEquals( true, defaults.reconnectCheck() );
        assertEquals( 1000, defaults.fastChatMillis() );
        assertEquals( 5, defaults.sameMessageAccounts() );
        assertEquals( 10, defaults.sameMessageSeconds() );
        assertEquals( 8, defaults.sameMessageMinLength() );
    }

    @Test
    public void testEqualByValue()
    {
        assertEquals( ShieldSettings.defaults(), ShieldSettings.defaults() );
    }
}
