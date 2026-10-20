package com.parkcode.parkcord.shield;

import com.google.common.base.CharMatcher;
import com.google.common.net.InetAddresses;
import java.net.InetAddress;

/**
 * What Shield counts, strikes and blocks: one IPv4 address, or one IPv6 /64, because a single IPv6 customer owns
 * at least a whole /64 and would otherwise have 2^64 separate keys.
 * <p>
 * The constructor does not check its arguments: tests and tables build keys from plain numbers.
 *
 * @param v6 false for an IPv4 address, true for an IPv6 /64
 * @param bits the 32 bits of the IPv4 address (0 to 0xFFFFFFFF), or the first 64 bits of the IPv6 address
 */
public record AddressKey( boolean v6, long bits ) implements Comparable<AddressKey>
{

    private static final String PREFIX_LENGTH = "/64";
    // the longest literal is an IPv6 address that ends in a dotted quad
    private static final int LONGEST_LITERAL = 45;
    private static final CharMatcher LITERAL_CHARACTERS = CharMatcher.anyOf( "0123456789abcdefABCDEF:." );

    /**
     * The key of an address. An IPv4-mapped IPv6 address ({@code ::ffff:a.b.c.d}) has the key of its IPv4
     * address; every other IPv6 form (IPv4-compatible, 6to4, NAT64) stays IPv6.
     *
     * @param address the address, or null
     * @return the key, or null when there is no address or it has neither 4 nor 16 bytes
     */
    public static AddressKey of(InetAddress address)
    {
        if ( address == null )
        {
            return null;
        }
        byte[] raw = address.getAddress();
        if ( raw.length == 4 )
        {
            return new AddressKey( false, number( raw, 0, 4 ) );
        }
        if ( raw.length != 16 )
        {
            return null;
        }
        // mapped: 80 zero bits, then 16 one bits, then the IPv4 address
        if ( number( raw, 0, 8 ) == 0 && number( raw, 8, 4 ) == 0xFFFFL )
        {
            return new AddressKey( false, number( raw, 12, 4 ) );
        }
        return new AddressKey( true, number( raw, 0, 8 ) );
    }

    /**
     * The key of an address literal, or of the text {@link #toString()} writes. Never asks a resolver.
     *
     * @param text an IPv4 or IPv6 literal, or an IPv6 literal followed by {@code /64}
     * @return the key, never null
     * @throws IllegalArgumentException for null and for every other text
     */
    public static AddressKey parse(String text)
    {
        if ( text != null )
        {
            boolean prefix = text.endsWith( PREFIX_LENGTH );
            String rest = ( prefix ) ? text.substring( 0, text.length() - PREFIX_LENGTH.length() ) : text;
            if ( literal( rest ) )
            {
                AddressKey key = of( InetAddresses.forString( rest ) );
                if ( key != null && ( key.v6 || !prefix ) )
                {
                    return key;
                }
            }
        }
        throw new IllegalArgumentException( "not an IP address: " + text );
    }

    // Whether the text is an IP literal and nothing else. The character check comes first because Guava alone
    // accepts full-width digits and, after a '%', asks the operating system for a network interface.
    static boolean literal(String text)
    {
        return text != null && !text.isEmpty() && text.length() <= LONGEST_LITERAL
                && LITERAL_CHARACTERS.matchesAllOf( text ) && InetAddresses.isInetAddress( text );
    }

    private static long number(byte[] raw, int from, int count)
    {
        long value = 0;
        for ( int i = from; i < from + count; i++ )
        {
            value = ( value << 8 ) | ( raw[i] & 0xFF );
        }
        return value;
    }

    @Override
    public int compareTo(AddressKey other)
    {
        return ( v6 != other.v6 ) ? Boolean.compare( v6, other.v6 ) : Long.compareUnsigned( bits, other.bits );
    }

    @Override
    public String toString()
    {
        if ( v6 )
        {
            return group( 48 ) + ":" + group( 32 ) + ":" + group( 16 ) + ":" + group( 0 ) + "::" + PREFIX_LENGTH;
        }
        return ( ( bits >>> 24 ) & 0xFF ) + "." + ( ( bits >>> 16 ) & 0xFF ) + "." + ( ( bits >>> 8 ) & 0xFF ) + "." + ( bits & 0xFF );
    }

    private String group(int shift)
    {
        return Long.toHexString( ( bits >>> shift ) & 0xFFFF );
    }
}
