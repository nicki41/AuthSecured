# AuthSecured 1.1.0 — Fabric support

AuthSecured can now run as either a Paper-compatible plugin or a Fabric
dedicated-server mod while sharing the same authentication core and database.

## Added

- Fabric support for every Minecraft release from 1.21 through 26.2.
- Dedicated Fabric artifacts for each exact Minecraft version.
- Fabric implementations of registration, login, password change, logout,
  authentication status, and administrator commands.
- Pre-authentication restrictions for movement, chat, commands, inventory,
  interactions, block breaking/placing, combat, item pickup/drop, and hunger.
- Shared platform bootstrap so Paper and Fabric use identical password policy,
  localization, rate limiting, sessions, SQLite/PostgreSQL, and Redis behavior.
- Full version-matrix build and compatibility checks.

## Fixed

- Session IP verification now rejects reconnects from a different address.
- Non-persistent sessions remain in memory and no longer survive restarts.
- Logout revokes the active session.
- `/register` validates password confirmation.
- Paper listeners and commands now share one restriction-state manager.
- Database integration tests are included in the standard build.

## Installation notes

- Paper, Purpur, Spigot, or Leaf: use `authsecured-paper-1.1.0.jar` in
  `plugins/`.
- Fabric: use the `authsecured-fabric-1.1.0+mc<version>.jar` matching the server
  version exactly and install the matching Fabric API in `mods/`.
- Minecraft 1.21.x requires Java 21; Minecraft 26.x requires Java 25 or newer.
- Do not install both platform artifacts on the same server.

Authors: Antigravity, touchfalls.
