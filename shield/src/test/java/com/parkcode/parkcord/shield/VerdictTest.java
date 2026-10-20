package com.parkcode.parkcord.shield;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

public class VerdictTest
{

    @Test
    public void testConstants()
    {
        assertEquals( "[ALLOW, BLOCKED, TOO_MANY_CONNECTIONS, TOO_MANY_PINGS, NAME_REFUSED, TOO_MANY_ACCOUNTS, RECONNECT, FAST_CHAT, SAME_MESSAGE]", Arrays.toString( Verdict.values() ) );
    }

    @Test
    public void testAllowed()
    {
        for ( Verdict verdict : Verdict.values() )
        {
            // no default branch: a constant added to Verdict without a decision here does not compile
            boolean expected = switch ( verdict )
            {
                case ALLOW -> true;
                case BLOCKED, TOO_MANY_CONNECTIONS, TOO_MANY_PINGS -> false;
                case NAME_REFUSED, TOO_MANY_ACCOUNTS, RECONNECT -> false;
                case FAST_CHAT, SAME_MESSAGE -> false;
            };
            assertEquals( expected, verdict.allowed(), verdict.name() );
        }
    }
}
