package com.parkcode.parkcord.shield;

import java.util.List;
import java.util.Set;

/**
 * Everything Shield decides with, as one value that a reload swaps as a whole. All durations are seconds, except
 * {@code fastChatMillis}.
 * <p>
 * Nothing is checked here: the ranges belong to the configuration loader, and the tests of the stages pass
 * nonsense on purpose.
 *
 * @param enabled false makes every check allow and count nothing
 * @param mode whether a refusal is carried out or only counted
 * @param exemptions the addresses that are never limited and never blocked
 * @param connectionsLimit new connections from one address key within {@code connectionsSeconds}; 0 = off
 * @param connectionsSeconds the window of {@code connectionsLimit}
 * @param pingsLimit server list pings from one address key within {@code pingsSeconds}; 0 = off
 * @param pingsSeconds the window of {@code pingsLimit}
 * @param newAccountsPerIp accounts online from one address key that were not known when they joined; 0 = off
 * @param nameBlacklist a name is refused when, without digits and underscores, it equals one of these
 * @param afterStrikes this many strikes within {@code strikeSeconds} block the address key
 * @param strikeSeconds the window of {@code afterStrikes}
 * @param blockSeconds how long such a block lasts
 * @param attackConnectionsPerSecond more than this, {@code attackTriggerSeconds} in a row, switches attack mode
 * on; 0 = never by itself
 * @param attackTriggerSeconds how many seconds in a row the rate must be above the limit
 * @param attackStartGraceSeconds attack mode does not switch on by itself this long after start
 * @param attackCalmSeconds this long below the limit switches attack mode off again
 * @param reconnectCheck while attack mode is on, a player who is not known must connect twice
 * @param fastChatMillis chat sooner than this after joining, from an account that was not known, is refused;
 * 0 = off
 * @param sameMessageAccounts the same chat text from this many such accounts within {@code sameMessageSeconds} is
 * refused; 0 = off
 * @param sameMessageSeconds the window of {@code sameMessageAccounts}
 * @param sameMessageMinLength shorter chat text is not compared
 */
public record ShieldSettings( boolean enabled,
        Mode mode,
        Exemptions exemptions,
        int connectionsLimit,
        int connectionsSeconds,
        int pingsLimit,
        int pingsSeconds,
        int newAccountsPerIp,
        Set<String> nameBlacklist,
        int afterStrikes,
        int strikeSeconds,
        int blockSeconds,
        int attackConnectionsPerSecond,
        int attackTriggerSeconds,
        int attackStartGraceSeconds,
        int attackCalmSeconds,
        boolean reconnectCheck,
        int fastChatMillis,
        int sameMessageAccounts,
        int sameMessageSeconds,
        int sameMessageMinLength )
{

    /**
     * What Shield does with a refusal: {@link #ENFORCE} carries it out, {@link #OBSERVE} allows and only counts
     * what would have been refused.
     */
    public enum Mode
    {

        ENFORCE,
        OBSERVE
    }

    /**
     * The values of a configuration file that sets nothing.
     *
     * @return the defaults of the design, with an empty whitelist and the private ranges exempt
     */
    public static ShieldSettings defaults()
    {
        return new ShieldSettings( true, Mode.ENFORCE, new Exemptions( List.of(), true ), 30, 10, 20, 10, 10,
                Set.of( "mcstorm", "mcdown", "mcbot", "dropbot", "kingbot" ), 10, 60, 300, 60, 3, 120, 30, true, 1000, 5, 10, 8 );
    }
}
