package com.parkcode.parkcord.shield;

import com.google.common.base.CharMatcher;
import com.google.common.net.InetAddresses;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The addresses Shield never counts, strikes or blocks: loopback always, the private ranges when asked, and the
 * whitelist of the configuration (single addresses and CIDR ranges).
 * <p>
 * Matching is on the whole address: a single IPv6 address in the whitelist is that one address, not its /64.
 * Two objects are equal when they treat the private ranges alike, hold the same whitelist ranges in the same
 * order and report the same problems.
 */
public final class Exemptions
{

    // the entry parser uses it, so it comes before the ranges that the parser builds
    private static final CharMatcher DIGITS = CharMatcher.inRange( '0', '9' );
    private static final List<Range> LOOPBACK = ranges( List.of( "127.0.0.0/8", "::1" ), new ArrayList<>() );
    // the documentation ranges and the benchmark range 198.18.0.0/15 are not private: the loopback run needs them
    private static final List<Range> PRIVATE = ranges( List.of( "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "100.64.0.0/10", "169.254.0.0/16", "fc00::/7", "fe80::/10" ), new ArrayList<>() );

    private final boolean privateExempt;
    private final List<Range> whitelist;
    private final List<String> problems;

    /**
     * Reads the whitelist. Never throws: an entry that is not an address or a CIDR range is left out and named
     * in {@link #problems()}, and the other entries still apply.
     *
     * @param whitelist IP literals and CIDR ranges such as {@code 203.0.113.7} or {@code 2001:db8:1::/48}; null
     * counts as an empty list
     * @param privateExempt whether the private, carrier NAT and link-local ranges are exempt
     */
    public Exemptions(List<String> whitelist, boolean privateExempt)
    {
        List<String> found = new ArrayList<>();
        this.whitelist = ranges( ( whitelist == null ) ? List.of() : whitelist, found );
        this.problems = List.copyOf( found );
        this.privateExempt = privateExempt;
    }

    /**
     * Whether Shield leaves an address alone.
     *
     * @param address the address of the connection, or null
     * @return true for loopback, for a private range when those are exempt, for a whitelisted address, and when
     * there is no address to decide on (null, or neither IPv4 nor IPv6)
     */
    public boolean exempt(InetAddress address)
    {
        Address bits = Address.of( address );
        if ( bits == null )
        {
            return true;
        }
        return contains( LOOPBACK, bits ) || ( privateExempt && contains( PRIVATE, bits ) ) || contains( whitelist, bits );
    }

    /**
     * What was wrong with the whitelist.
     *
     * @return one line for each entry that was left out, in the order of the whitelist; unmodifiable
     */
    public List<String> problems()
    {
        return problems;
    }

    @Override
    public boolean equals(Object other)
    {
        return other instanceof Exemptions that && privateExempt == that.privateExempt && whitelist.equals( that.whitelist ) && problems.equals( that.problems );
    }

    @Override
    public int hashCode()
    {
        return Objects.hash( privateExempt, whitelist, problems );
    }

    private static boolean contains(List<Range> ranges, Address address)
    {
        for ( Range range : ranges )
        {
            if ( range.contains( address ) )
            {
                return true;
            }
        }
        return false;
    }

    private static List<Range> ranges(List<String> entries, List<String> problems)
    {
        List<Range> ranges = new ArrayList<>();
        for ( String entry : entries )
        {
            String problem = read( entry, ranges );
            if ( problem != null )
            {
                problems.add( problem );
            }
        }
        return List.copyOf( ranges );
    }

    // adds the range of one entry, or returns what is wrong with the entry
    private static String read(String entry, List<Range> ranges)
    {
        String trimmed = ( entry == null ) ? "" : entry.trim();
        if ( trimmed.isEmpty() )
        {
            return "empty whitelist entry";
        }
        int slash = trimmed.indexOf( '/' );
        String literal = ( slash < 0 ) ? trimmed : trimmed.substring( 0, slash );
        if ( !AddressKey.literal( literal ) )
        {
            return "whitelist entry '" + trimmed + "' is not an address or CIDR range";
        }
        Address start = Address.of( InetAddresses.forString( literal ) );
        int longest = ( start.v6() ) ? 128 : 32;
        int prefix = ( slash < 0 ) ? longest : prefixLength( trimmed.substring( slash + 1 ) );
        if ( prefix < 0 || prefix > longest )
        {
            return "whitelist entry '" + trimmed + "': the prefix length must be between 0 and " + longest;
        }
        Range range = Range.of( start, ( start.v6() ) ? prefix : prefix + 96 );
        if ( !range.startsAtItsFirstAddress() )
        {
            // refused, not masked: 203.0.113.7/2 would otherwise exempt a quarter of all addresses without a word
            return "whitelist entry '" + trimmed + "': the address has bits set beyond /" + prefix;
        }
        ranges.add( range );
        return null;
    }

    // one to three ASCII digits as a number, or -1
    private static int prefixLength(String text)
    {
        return ( !text.isEmpty() && text.length() <= 3 && DIGITS.matchesAllOf( text ) ) ? Integer.parseInt( text ) : -1;
    }

    // An address as 128 bits. An IPv4 address and an IPv4-mapped one are family IPv4 with the value in the last
    // 32 bits of low, so that a range of one family never contains an address of the other.
    private record Address( boolean v6, long high, long low )
    {

        static Address of(InetAddress address)
        {
            AddressKey key = AddressKey.of( address );
            if ( key == null )
            {
                return null;
            }
            if ( key.v6() )
            {
                return new Address( true, key.bits(), ByteBuffer.wrap( address.getAddress() ).getLong( 8 ) );
            }
            return new Address( false, 0, key.bits() );
        }
    }

    // the addresses that agree with start in the bits the two masks select
    private record Range( Address start, long highMask, long lowMask )
    {

        // length: how many of the 128 bits are the prefix; for IPv4 the prefix length plus 96
        static Range of(Address start, int length)
        {
            // a long is shifted by the count modulo 64: -1L << 64 is -1L, not 0, so a mask without bits has its own branch
            long highMask = ( length >= 64 ) ? -1L : ( length == 0 ) ? 0 : -1L << ( 64 - length );
            long lowMask = ( length <= 64 ) ? 0 : ( length == 128 ) ? -1L : -1L << ( 128 - length );
            return new Range( start, highMask, lowMask );
        }

        boolean startsAtItsFirstAddress()
        {
            return ( start.high() & ~highMask ) == 0 && ( start.low() & ~lowMask ) == 0;
        }

        boolean contains(Address address)
        {
            return start.v6() == address.v6() && ( ( start.high() ^ address.high() ) & highMask ) == 0 && ( ( start.low() ^ address.low() ) & lowMask ) == 0;
        }
    }
}
