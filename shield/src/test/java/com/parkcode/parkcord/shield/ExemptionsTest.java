package com.parkcode.parkcord.shield;

import static org.junit.jupiter.api.Assertions.*;
import com.google.common.net.InetAddresses;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ExemptionsTest
{

    private static void check(Exemptions exemptions, boolean expected, String... literals)
    {
        for ( String literal : literals )
        {
            assertEquals( expected, exemptions.exempt( InetAddresses.forString( literal ) ), literal );
        }
    }

    @Test
    public void testLoopbackIsAlwaysExempt() throws UnknownHostException
    {
        Exemptions exemptions = new Exemptions( List.of(), false );
        check( exemptions, true, "127.0.0.1", "127.0.0.0", "127.255.255.255", "::1" );
        check( exemptions, false, "126.255.255.255", "128.0.0.0", "::", "::2", "0:0:0:1::1" );
        assertTrue( exemptions.exempt( AddressKeyTest.mapped( 127, 0, 0, 1 ) ) );
        assertFalse( exemptions.exempt( AddressKeyTest.mapped( 203, 0, 113, 7 ) ) );
    }

    @Test
    public void testNoAddressIsExempt()
    {
        assertTrue( new Exemptions( List.of(), false ).exempt( null ) );
    }

    @Test
    public void testPrivateRanges() throws UnknownHostException
    {
        String[] inside = new String[]
        {
            "10.0.0.0", "10.255.255.255", "172.16.0.0", "172.31.255.255", "192.168.0.0", "192.168.255.255",
            "100.64.0.0", "100.127.255.255", "169.254.0.0", "169.254.255.255",
            "fc00::", "fdff:ffff:ffff:ffff:ffff:ffff:ffff:ffff", "fe80::", "febf:ffff:ffff:ffff:ffff:ffff:ffff:ffff"
        };
        String[] outside = new String[]
        {
            "9.255.255.255", "11.0.0.0", "172.15.255.255", "172.32.0.0", "192.167.255.255", "192.169.0.0",
            "100.63.255.255", "100.128.0.0", "169.253.255.255", "169.255.0.0",
            "fbff:ffff:ffff:ffff:ffff:ffff:ffff:ffff", "fe00::", "fe7f:ffff:ffff:ffff:ffff:ffff:ffff:ffff", "fec0::",
            "192.0.2.1", "198.51.100.1", "203.0.113.7", "198.18.0.1", "198.19.255.255", "2001:db8::1"
        };
        Exemptions on = new Exemptions( List.of(), true );
        check( on, true, inside );
        check( on, false, outside );
        assertTrue( on.exempt( AddressKeyTest.mapped( 10, 1, 2, 3 ) ) );

        Exemptions off = new Exemptions( List.of(), false );
        check( off, false, inside );
        check( off, false, outside );
    }

    @Test
    public void testWhitelist() throws UnknownHostException
    {
        Exemptions exemptions = new Exemptions( List.of( "203.0.113.7", " 198.51.100.0/24 ", "198.18.0.0/15", "2001:db8:1::/48", "2001:db8:2:3::5", "2001:db8:0:1:8000::/65" ), false );
        assertEquals( List.of(), exemptions.problems() );
        check( exemptions, true, "203.0.113.7", "198.51.100.0", "198.51.100.255", "198.18.0.0", "198.19.255.255" );
        check( exemptions, false, "203.0.113.6", "203.0.113.8", "198.51.99.255", "198.51.101.0", "198.17.255.255", "198.20.0.0" );
        check( exemptions, true, "2001:db8:1::", "2001:db8:1:ffff:ffff:ffff:ffff:ffff", "2001:db8:2:3::5", "2001:db8:0:1:8000::", "2001:db8:0:1:ffff:ffff:ffff:ffff" );
        check( exemptions, false, "2001:db8:0:ffff:ffff:ffff:ffff:ffff", "2001:db8:2::", "2001:db8:2:3::4", "2001:db8:2:3::6", "2001:db8:0:1:7fff:ffff:ffff:ffff", "2001:db8:0:2::" );
        assertTrue( exemptions.exempt( AddressKeyTest.mapped( 203, 0, 113, 7 ) ) );
    }

    @Test
    public void testPrefixLengthEdges()
    {
        check( new Exemptions( List.of( "0.0.0.0/0" ), false ), true, "0.0.0.0", "203.0.113.7", "255.255.255.255" );
        check( new Exemptions( List.of( "0.0.0.0/0" ), false ), false, "2001:db8::1" );
        check( new Exemptions( List.of( "::/0" ), false ), true, "::", "2001:db8::1", "ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff" );
        check( new Exemptions( List.of( "::/0" ), false ), false, "203.0.113.7" );
        check( new Exemptions( List.of( "128.0.0.0/1", "8000::/1" ), false ), true, "128.0.0.0", "255.255.255.255", "8000::", "ffff::1" );
        check( new Exemptions( List.of( "128.0.0.0/1", "8000::/1" ), false ), false, "126.255.255.255", "7fff:ffff:ffff:ffff:ffff:ffff:ffff:ffff" );
        check( new Exemptions( List.of( "203.0.113.6/31", "2001:db8:1:2::/64", "2001:db8:5::2/127" ), false ), true, "203.0.113.6", "203.0.113.7", "2001:db8:1:2::", "2001:db8:1:2:ffff:ffff:ffff:ffff", "2001:db8:5::2", "2001:db8:5::3" );
        check( new Exemptions( List.of( "203.0.113.6/31", "2001:db8:1:2::/64", "2001:db8:5::2/127" ), false ), false, "203.0.113.5", "203.0.113.8", "2001:db8:1:1:ffff:ffff:ffff:ffff", "2001:db8:1:3::", "2001:db8:5::1", "2001:db8:5::4" );
        check( new Exemptions( List.of( "203.0.113.7/32", "2001:db8::7/128" ), false ), true, "203.0.113.7", "2001:db8::7" );
        check( new Exemptions( List.of( "203.0.113.7/32", "2001:db8::7/128" ), false ), false, "203.0.113.6", "2001:db8::6" );
    }

    @Test
    public void testBadEntriesAreReportedAndTheRestApplies()
    {
        Exemptions exemptions = new Exemptions( Arrays.asList( "203.0.113.7", "example.com", "203.0.113.0/33", "2001:db8::/129", "203.0.113.7/24",
                "", null, "198.51.100.0/24/8", "198.51.100.0/-1", "198.51.100.0/", "2001:db8::1/64", "::ffff:192.0.2.0/120", "198.51.100.9" ), false );
        assertEquals( List.of(
                "whitelist entry 'example.com' is not an address or CIDR range",
                "whitelist entry '203.0.113.0/33': the prefix length must be between 0 and 32",
                "whitelist entry '2001:db8::/129': the prefix length must be between 0 and 128",
                "whitelist entry '203.0.113.7/24': the address has bits set beyond /24",
                "empty whitelist entry",
                "empty whitelist entry",
                "whitelist entry '198.51.100.0/24/8': the prefix length must be between 0 and 32",
                "whitelist entry '198.51.100.0/-1': the prefix length must be between 0 and 32",
                "whitelist entry '198.51.100.0/': the prefix length must be between 0 and 32",
                "whitelist entry '2001:db8::1/64': the address has bits set beyond /64",
                "whitelist entry '::ffff:192.0.2.0/120': the prefix length must be between 0 and 32" ), exemptions.problems() );
        check( exemptions, true, "203.0.113.7", "198.51.100.9" );
        check( exemptions, false, "203.0.113.1", "198.51.100.1", "2001:db8::1", "192.0.2.1" );
    }

    @Test
    public void testNullListAndUnmodifiableProblems()
    {
        Exemptions exemptions = new Exemptions( null, true );
        assertEquals( List.of(), exemptions.problems() );
        assertThrows( UnsupportedOperationException.class, () -> exemptions.problems().add( "x" ) );
        check( exemptions, false, "203.0.113.7" );
    }

    @Test
    public void testEqualByValue()
    {
        Exemptions exemptions = new Exemptions( List.of( "203.0.113.7", "198.51.100.0/24" ), true );
        assertEquals( exemptions, new Exemptions( List.of( "203.0.113.7/32", " 198.51.100.0/24" ), true ) );
        assertEquals( exemptions.hashCode(), new Exemptions( List.of( "203.0.113.7/32", " 198.51.100.0/24" ), true ).hashCode() );
        assertNotEquals( exemptions, new Exemptions( List.of( "203.0.113.7", "198.51.100.0/24" ), false ) );
        assertNotEquals( exemptions, new Exemptions( List.of( "203.0.113.7" ), true ) );
        assertNotEquals( exemptions, new Exemptions( List.of( "203.0.113.7", "198.51.100.0/24", "bad" ), true ) );
    }
}
