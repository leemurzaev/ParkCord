/**
 * Shield: what ParkCord decides about a connection, a login or a chat line.
 * <p>
 * Everything here takes address keys, names and a clock and returns a verdict. No Netty, no BungeeCord classes,
 * no file access and no logging; time comes from an injected Guava {@code Ticker}; every class is thread safe.
 */
package com.parkcode.parkcord.shield;
