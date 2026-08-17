# AuthSecured 1.1.0

AuthSecured is one authentication codebase delivered as either a server plugin
or a Fabric server mod. It supports Minecraft 1.21 through the current stable
release, 26.2.

## Platforms

- Paper, Purpur, Spigot, and Leaf: install `authsecured-paper-1.1.0.jar` in
  `plugins/`.
- Fabric: install the JAR whose `+mc...` suffix exactly matches the Minecraft
  version in `mods/`, together with the matching Fabric API release.
- Minecraft 1.21.x requires Java 21. Minecraft 26.x requires Java 25 or newer.

The complete target list and Fabric API mapping are documented in
[SUPPORTED_VERSIONS.md](SUPPORTED_VERSIONS.md).

## Features

- Argon2id password hashing through Bouncy Castle.
- SQLite by default, with PostgreSQL support and HikariCP connection pooling.
- Account and IP rate limiting through Redis or an in-memory fallback.
- Optional reconnect sessions with persistent/non-persistent storage and IP
  verification.
- English, Russian, Spanish, Italian, and French messages.
- Pre-authentication restrictions for movement, chat, commands, inventory,
  interaction, block actions, combat, item pickup/drop, and hunger.
- Shared domain and application services across Paper and Fabric, with only the
  server API adapters kept platform-specific.

## Build

Requirements: Bash and a JDK capable of running Gradle 9.5.1. Gradle toolchains
select Java 21 for Minecraft 1.21.x and Java 25-compatible bytecode for 26.x.

```bash
./build_and_test.sh
```

This runs every unit/integration test and builds:

- `build/libs/authsecured-paper-1.1.0.jar`
- `build/libs/authsecured-fabric-1.1.0+mc26.2.jar`

Build every supported Fabric target:

```bash
./build_fabric_matrix.sh
```

Build one Fabric target:

```bash
./gradlew :platform-fabric:clean :platform-fabric:build -Pminecraft_version=1.21.11
```

## Installation

1. Stop the server.
2. Copy the correct artifact to `plugins/` (Paper-compatible server) or `mods/`
   (Fabric server). Fabric also requires Fabric API for the exact game version.
3. Start once to generate `config/authsecured/` on Fabric or
   `plugins/AuthSecured/` on Paper.
4. Set `AUTHSECURED_IP_SECRET` to a long random secret. Set database and Redis
   environment variables when those integrations are enabled.
5. Review online-mode bypass and all pre-authentication restrictions before
   exposing the server publicly.

Do not install both artifacts on the same server. They contain the same core but
target different server APIs.

## Commands

| Command | Usage | Permission |
| --- | --- | --- |
| `/register` | `/register <password> <confirmation>` | everyone |
| `/login` | `/login <password>` | everyone |
| `/changepassword` | `/changepassword <old> <new>` | everyone |
| `/logout` | `/logout` | everyone |
| `/authstatus` | `/authstatus` | everyone |
| `/authadmin reload` | reload configuration | `authsecured.admin` / operator |
| `/authadmin unlock` | `/authadmin unlock <player>` | `authsecured.admin` / operator |
| `/authadmin unregister` | `/authadmin unregister <player>` | `authsecured.admin` / operator |
| `/authadmin resetpassword` | `/authadmin resetpassword <player> <new>` | `authsecured.admin` / operator |

Paper permission plugins may grant `authsecured.admin`; Fabric uses the vanilla
administrator permission level.

## Configuration and deployment

The generated configuration contains database, Redis, password policy, session,
online-mode bypass, localization, and restriction settings. See
[docs/deployment.md](docs/deployment.md) for production notes and
[docs/architecture.md](docs/architecture.md) for module boundaries.

## License

[MIT](LICENSE)

Author: touchfalls.
