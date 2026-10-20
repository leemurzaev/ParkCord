package com.parkcode.parkcord.shield;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class PlayerKeysTest
{

    // one row: kind, first word, second word, separated by one space
    private static final String TABLE = """
            same Notch notch
            same NOTCH nOtCh
            same jeb_ JEB_
            differ notch jeb_
            differ notch notch_
            differ Notch N0tch
            key Notch 7e585628-8917-38cb-85c2-a0123912c0f4
            key jeb_ 417bf43a-ff01-318c-bf80-648761defd49
            """;

    /**
     * One row of the table.
     *
     * @param kind "same", "differ" or "key"
     * @param first a player name
     * @param second a player name, or for "key" the key the first name must have
     */
    private record Row( String kind, String first, String second )
    {

        Row
        {
            if ( kind.isEmpty() || first.isEmpty() || second.isEmpty() )
            {
                throw new IllegalArgumentException( "a row is three words separated by one space" );
            }
        }

        static Row parse(String line)
        {
            String[] words = line.split( " ", -1 );
            return new Row( words[0], words[1], words[2] );
        }

        boolean holds()
        {
            return switch ( kind )
            {
                case "same" -> PlayerKeys.of( first ).equals( PlayerKeys.of( second ) );
                case "differ" -> !PlayerKeys.of( first ).equals( PlayerKeys.of( second ) );
                case "key" -> PlayerKeys.of( first ).equals( UUID.fromString( second ) );
                default -> throw new IllegalArgumentException( "kind: " + kind );
            };
        }
    }

    // a name with lone surrogates does not print: the failure message shows its code units
    private static String show(Object name)
    {
        if ( name instanceof String text && !text.isEmpty() )
        {
            StringBuilder units = new StringBuilder();
            for ( char unit : text.toCharArray() )
            {
                units.append( Integer.toHexString( unit ) ).append( ' ' );
            }
            return units.toString();
        }
        return String.valueOf( name );
    }

    @Test
    public void testTable()
    {
        assertEquals( 8, TABLE.lines().count() );
        for ( String line : TABLE.lines().toList() )
        {
            assertTrue( Row.parse( line ).holds(), line );
        }
    }

    @Test
    public void testDerivation()
    {
        assertEquals( UUID.nameUUIDFromBytes( "ParkCord:notch".getBytes( StandardCharsets.UTF_8 ) ), PlayerKeys.of( "Notch" ) );
        assertEquals( UUID.fromString( "fd30aae4-8f0a-3388-8018-f28480869220" ), PlayerKeys.of( "" ) );
        assertEquals( PlayerKeys.of( "" ), PlayerKeys.of( null ) );
    }

    @Test
    public void testRootLocale()
    {
        Locale before = Locale.getDefault();
        Locale.setDefault( Locale.forLanguageTag( "tr-TR" ) );
        try
        {
            // under the Turkish rules "TITAN" lower-cases with a dotless i
            assertEquals( PlayerKeys.of( "titan" ), PlayerKeys.of( "TITAN" ) );
        } finally
        {
            Locale.setDefault( before );
        }
    }

    @Test
    public void testNeverThrows()
    {
        for ( String name : Arrays.asList( null, "", " ", "\uD800", "\uDC00\uD800", "x".repeat( 32767 ) ) )
        {
            assertDoesNotThrow( () -> PlayerKeys.of( name ), show( name ) );
        }

        Random random = new Random( 2102 );
        for ( int i = 0; i < 10000; i++ )
        {
            char[] units = new char[ random.nextInt( 41 ) ];
            for ( int j = 0; j < units.length; j++ )
            {
                units[j] = (char) random.nextInt( 0x10000 );
            }
            String name = new String( units );
            assertDoesNotThrow( () -> PlayerKeys.of( name ), show( name ) );
        }
    }
}
