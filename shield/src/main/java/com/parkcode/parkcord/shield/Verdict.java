package com.parkcode.parkcord.shield;

/**
 * What Shield decided: {@link #ALLOW}, or the reason for a refusal.
 */
public enum Verdict
{

    ALLOW,
    BLOCKED,
    TOO_MANY_CONNECTIONS,
    TOO_MANY_PINGS,
    NAME_REFUSED,
    TOO_MANY_ACCOUNTS,
    RECONNECT,
    FAST_CHAT,
    SAME_MESSAGE;

    /**
     * Whether the connection, the login or the chat line goes on.
     *
     * @return true for {@link #ALLOW} only
     */
    public boolean allowed()
    {
        return this == ALLOW;
    }
}
