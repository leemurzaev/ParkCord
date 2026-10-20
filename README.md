# ParkCord

**A BungeeCord fork with a built-in anti-bot and anti-attack layer ("Shield").**

ParkCord tracks upstream [SpigotMC/BungeeCord](https://github.com/SpigotMC/BungeeCord) and adds its own modules on top. It is a work in progress, built by ParkDawg for the NullCraft network.

## What ParkCord adds

- **Java 21 baseline** for the whole build (`maven.compiler.release` 21).
- **`shield` module**: the decision layer for connections, logins and chat lines. Every class takes address keys, names and a clock and returns a verdict. It has no Netty, BungeeCord, file or logging dependencies, takes time from an injected Guava `Ticker`, and is thread safe.
  - `AddressKey`: normalises IPv4/IPv6 addresses into the keys that limits are counted on.
  - `Exemptions`: addresses and players that are never limited (loopback is always allowed).
  - `RateWindow` / `WindowTable`: sliding per-address rate windows for connections and pings.
  - `StrikeTable` and `BlockList`: strikes for addresses that break limits, and temporary blocks.
  - `AttackMode`: switches on when the connection rate stays above the limit for several seconds in a row, and off again after a calm period. It has a start grace and an operator override, and counting a connection takes no lock.
  - `PlayerKeys`, `Verdict` and `ShieldSettings`: player identity keys, the decision type and the settings.
- **Unit tests** for the Shield classes (`mvn -pl shield test`).

## Status

Milestone 1 (foundation and Shield) is in progress. The Shield decision layer and its tests are done. Wiring it into the proxy's connection pipeline is the next step.

## Building

Requires JDK 21+ and Maven.

```bash
mvn package
```

The proxy jar is built by the `bootstrap` module, the same as upstream BungeeCord.

---

*Everything below is the original BungeeCord README. ParkCord keeps BungeeCord's license (see [LICENSE](LICENSE)).*

---

BungeeCord
==========
Layer 7 proxy designed to link Minecraft servers.
--------------------------------------------------

BungeeCord is a sophisticated proxy and API designed mainly to teleport players between multiple Minecraft servers. It is the latest incarnation of similar software written by the author from 2011-present.

Information
-----------
BungeeCord is maintained by [SpigotMC](https://www.spigotmc.org/) and has its own [discussion thread](https://www.spigotmc.org/go/bungeecord) with plenty of helpful information and links.

### Security warning

As your Minecraft servers have to run without authentication (online-mode=false) for BungeeCord to work, this poses a new security risk. Users may connect to your servers directly, under any username they wish to use. The kick "If you wish to use IP forwarding, please enable it in your BungeeCord config as well!" does not protect your Spigot servers.

To combat this, you need to restrict access to these servers for example with a firewall (please see [firewall guide](https://www.spigotmc.org/wiki/firewall-guide/)).

Source
------
Source code is currently available on [GitHub](https://www.spigotmc.org/go/bungeecord-git).

Binaries
--------
Precompiled binaries are available for end users on [Jenkins](https://www.spigotmc.org/go/bungeecord-dl).

(c) 2012-2026 SpigotMC Pty. Ltd.
