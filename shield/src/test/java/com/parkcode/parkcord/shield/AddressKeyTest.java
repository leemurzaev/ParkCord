package com.parkcode.parkcord.shield;

import static org.junit.jupiter.api.Assertions.*;
import com.google.common.net.InetAddresses;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class AddressKeyTest
{

    private static final AddressKey V4 = new AddressKey( false, 0xCB007107L );
    private static final AddressKey V6 = new AddressKey( true, 0x20010DB800010002L );

    private static AddressKey key(String literal)
    {
        return AddressKey.of( InetAddresses.forString( literal ) );
    }

    static InetAddress mapped(int a, int b, int c, int d) throws UnknownHostException
    {
        byte[] raw = new byte[ 16 ];
        raw[10] = (byte) 0xFF;
        raw[11] = (byte) 0xFF;
        raw[12] = (byte) a;
        raw[13] = (byte) b;
        raw[14] = (byte) c;
        raw[15] = (byte) d;
        return Inet6Address.getByAddress( null, raw, 0 );
    }

    @Test
    public void testNull()
    {
        assertNull( AddressKey.of( null ) );
    }

    @Test
    public void testIpv4IsThe32Bits()
    {
        assertEquals( V4, key( "203.0.113.7" ) );
        assertEquals( new AddressKey( false, 0xC0000201L ), key( "192.0.2.1" ) );
        assertEquals( new AddressKey( false, 0xC63364C8L ), key( "198.51.100.200" ) );
        assertEquals( new AddressKey( false, 0xC6120001L ), key( "198.18.0.1" ) );
        assertEquals( new AddressKey( false, 0xFFFFFFFFL ), key( "255.255.255.255" ) );
        assertNotEquals( V4, key( "203.0.113.8" ) );
    }

    @Test
    public void testIpv6IsTheFirst64Bits()
    {
        assertEquals( V6, key( "2001:db8:1:2::1" ) );
        assertEquals( V6, key( "2001:db8:1:2:ffff:ffff:ffff:ffff" ) );
        assertEquals( new AddressKey( true, 0x20010DB800010003L ), key( "2001:db8:1:3::1" ) );
        assertEquals( new AddressKey( true, 0xFF02000000000000L ), key( "ff02::1" ) );
        assertEquals( new AddressKey( true, 0L ), key( "::1" ) );
        assertNotEquals( new AddressKey( false, 0L ), key( "::1" ) );
    }

    @Test
    public void testOneMillionAddressesOfOnePrefixAreOneKey() throws UnknownHostException
    {
        Set<AddressKey> keys = new HashSet<>();
        for ( long host = 0; host < 1000000; host++ )
        {
            byte[] raw = ByteBuffer.allocate( 16 ).putLong( 0x20010DB800010002L ).putLong( host * 0x9E3779B97F4A7C15L ).array();
            keys.add( AddressKey.of( InetAddress.getByAddress( raw ) ) );
        }
        assertEquals( Set.of( V6 ), keys );
    }

    @Test
    public void testMappedIpv4IsIpv4() throws UnknownHostException
    {
        InetAddress address = mapped( 192, 0, 2, 1 );
        assertInstanceOf( Inet6Address.class, address );
        assertEquals( new AddressKey( false, 0xC0000201L ), AddressKey.of( address ) );
        assertEquals( new AddressKey( true, 0L ), key( "::192.0.2.1" ), "IPv4-compatible is not IPv4-mapped" );
        assertEquals( new AddressKey( true, 0x0064FF9B00000000L ), key( "64:ff9b::192.0.2.1" ), "NAT64 stays IPv6" );
    }

    @Test
    public void testToString()
    {
        assertEquals( "203.0.113.7", V4.toString() );
        assertEquals( "255.255.255.255", new AddressKey( false, 0xFFFFFFFFL ).toString() );
        assertEquals( "0.0.0.0", new AddressKey( false, 0L ).toString() );
        assertEquals( "2001:db8:1:2::/64", V6.toString() );
        assertEquals( "2001:db8:0:0::/64", key( "2001:db8::1" ).toString() );
        assertEquals( "ff02:0:0:0::/64", key( "ff02::1" ).toString() );
        assertEquals( "0:0:0:0::/64", key( "::1" ).toString() );
    }

    @Test
    public void testParse()
    {
        assertEquals( V4, AddressKey.parse( "203.0.113.7" ) );
        assertEquals( V6, AddressKey.parse( "2001:db8:1:2::1" ) );
        assertEquals( V6, AddressKey.parse( "2001:DB8:1:2:0:0:0:1" ) );
        assertEquals( new AddressKey( false, 0xC0000201L ), AddressKey.parse( "::ffff:192.0.2.1" ) );
    }

    @Test
    public void testParseReadsWhatToStringWrites()
    {
        assertEquals( V4, AddressKey.parse( V4.toString() ) );
        assertEquals( V6, AddressKey.parse( V6.toString() ) );
        assertEquals( new AddressKey( true, 0L ), AddressKey.parse( "0:0:0:0::/64" ) );
        assertEquals( new AddressKey( true, 0xFF02000000000000L ), AddressKey.parse( "ff02:0:0:0::/64" ) );
    }

    @Test
    public void testParseRefusesEverythingElse()
    {
        String[] bad = new String[]
        {
            null, "", " ", "localhost", "example.com", "cafe.de", " 203.0.113.7", "203.0.113.7 ", "203.0.113", "203.0.113.256",
            "203.0.113.07", "0xCB.0.113.7", "203.0.113.7/32", "203.0.113.7/64", "::ffff:192.0.2.1/64", "2001:db8:1:2::1/48",
            "2001:db8:1:2::1%1", "[2001:db8:1:2::1]", "203.0.113.7:25565", "2001:db8:1:2:3:4:5:6:7", "\uFF11.2.3.4", "/64"
        };
        for ( String text : bad )
        {
            IllegalArgumentException ex = assertThrows( IllegalArgumentException.class, () -> AddressKey.parse( text ), String.valueOf( text ) );
            assertEquals( "not an IP address: " + text, ex.getMessage() );
        }
    }

    @Test
    public void testOrder()
    {
        assertTrue( key( "255.255.255.255" ).compareTo( key( "::1" ) ) < 0, "IPv4 sorts before IPv6" );
        assertTrue( key( "9.255.255.255" ).compareTo( key( "203.0.113.7" ) ) < 0 );
        assertTrue( key( "2001:db8::1" ).compareTo( key( "ff02::1" ) ) < 0, "unsigned" );
        assertEquals( 0, V6.compareTo( key( "2001:db8:1:2::99" ) ) );
    }
}
