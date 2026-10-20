package com.parkcode.parkcord.shield;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

/**
 * The key Shield knows a player by: 128 bits derived from the name alone, so that it exists at the login request
 * in every mode and protocol version. It is not the player's UUID.
 */
public final class PlayerKeys
{

    private static final String PREFIX = "ParkCord:";

    private PlayerKeys()
    {
    }

    /**
     * The key of a player name. Upper and lower case give the same key.
     *
     * @param name the name as the client sent it; null counts as the empty name
     * @return the key, never null
     */
    public static UUID of(String name)
    {
        String lower = ( name == null ) ? "" : name.toLowerCase( Locale.ROOT );
        return UUID.nameUUIDFromBytes( ( PREFIX + lower ).getBytes( StandardCharsets.UTF_8 ) );
    }
}
