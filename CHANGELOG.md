# Changelog

All notable changes to this project will be documented in this file.

## [1.2.1] - 2026-09-15

### Security
- Existing accounts always complete Argon2 verification before lock and
  temporary-password expiry responses, reducing account-state enumeration.
- Failed login counters are updated atomically in SQLite and PostgreSQL.
- Registration maps database uniqueness conflicts to an already-registered
  result, closing the concurrent registration race.
- Password changes reject the current password using a constant-time comparison.
- Temporary passwords contain only unambiguous letters and digits.
- Redis calls use a dedicated executor with timeout fallback instead of using
  the shared asynchronous pool.

### Added
- Temporary passwords are clickable and copyable for in-game administrators on
  Paper and Fabric.
- Discord and Telegram settings now include explicit channel switches, direct
  values, environment-variable values, and explanatory comments.
- Existing configuration files without a notifications section are migrated
  atomically on startup.

### Fixed
- Non-persistent mode clears stored database sessions before accepting session
  operations, preventing stale persistent sessions from surviving a mode change.
- Invalid but syntactically correct configuration values no longer replace the
  active configuration during `/authadmin reload`.
- Paper remains fail-closed and denies new logins if initialization fails.
- Paper asynchronous join callbacks ignore disconnected or replaced players.
- Database session and audit access consistently closes JDBC resources.

### Changed
- In-memory rate-limit counters use immutable values and atomic map updates.
- All new player and administrator messages are translated in English, Russian,
  Spanish, Italian, and French.

## [1.2.0] - 2026-09-15

### Added
- Configurable authentication deadline with automatic kick on Paper and Fabric.
- `/authadmin status`, `/authadmin doctor`, runtime session timeout management,
  audit viewing, tab completion, and operation-specific permissions.
- Expiring temporary password reset that forces an immediate permanent password
  change and revokes existing sessions.
- Optional privacy-safe Discord webhook and Telegram bot alerts for failed
  logins, with per-player cooldown.
- Backward-compatible SQLite and PostgreSQL account schema migrations.

### Fixed
- `/authadmin session settimeout` now changes the live timeout used for newly
  created sessions.
- Paper blocks its built-in raw command log before formatting and also replaces
  password-bearing command event text before normal dispatch.

### Changed
- Shared platform-neutral admin operations reduce duplicated Paper and Fabric
  command logic.
- Administrative errors and audit views no longer expose raw exception details,
  IP addresses, credentials, webhook URLs, or bot tokens.
- Infrastructure failures hide JDBC, Redis, YAML, and webhook exception details
  so credentials cannot leak through diagnostic messages.

## [1.1.1] - 2026-08-26

### Added
- Always-on redaction of password arguments from Fabric command diagnostics,
  profiler labels, and debug exception logs.
- Validation bounds for security, database, Redis, rate-limit, and session
  numeric settings, plus PostgreSQL `ssl-mode` support.

### Fixed
- Fabric now stays fail-closed when core initialization fails, blocks flight
  according to `restrictions.allow-flight`, and ignores stale asynchronous join
  callbacks after a disconnect or rapid reconnect.
- Invalid YAML reloads retain the last valid configuration snapshot.

### Changed
- Configuration lookups now use an immutable flattened snapshot, reducing work
  in high-frequency movement and packet restriction checks.
- `config.yml` now documents every active option, accepted values, units,
  reload behavior, environment variables, and security-sensitive defaults.

## [1.1.0] - 2026-08-17

### Added
- Restored Fabric as a production platform backed by the same core and database
  modules as the Paper-compatible plugin.
- Exact Fabric builds for every Minecraft release from 1.21 through 26.2, with
  version-specific mappings, Fabric API dependencies, metadata, and Java levels.
- Brigadier authentication/admin commands, lifecycle integration, reconnect
  handling, and pre-authentication restrictions for Fabric dedicated servers.
- A full Fabric version-matrix build script and shared `platform-common`
  dependency graph.

### Fixed
- Paper now uses the same restriction manager instance for listeners, commands,
  and the core platform adapter.
- Registration confirmation, localized command result keys, logout revocation,
  session IP verification, and non-persistent session behavior.
- SQLite native loading in shaded Paper and Fabric release artifacts.
- Database integration tests are now executed by the canonical Gradle test task.

### Changed
- Minimum supported Minecraft version is 1.21; current maximum is 26.2.
- Build moved to Gradle 9.5.1, Shadow 9.6.1, and Fabric Loom 1.17.19.
- Shared platform-independent bootstrapping moved out of the Paper module.

## [1.0.3] - 2026-08-01

### Added
- Cleaned up `.vscode/settings.json` and enabled automatic Gradle project synchronization (`java.import.gradle.enabled`) for Java IDEs.

### Removed
- Complete removal of Fabric module (`platform-fabric`), Fabric Loader dependencies, Fabric API listeners, Brigadier registrations, and Mixin configurations (architectural decision to maintain Fabric as a standalone repository).
- Removed legacy root `.project` and `.classpath` files that caused non-project file warnings in IDEs.

### Changed
- Transitioned repository build system to a Paper/Purpur-focused architecture (`:core`, `:database`, `:platform-paper`).
- Updated `build_and_test.sh` to compile core/database/paper modules and generate single `authsecured-paper-1.0.3.jar` artifact.
- Cleaned up docstrings across core ports [AuthPlatform], [PlatformLogger], [PlayerRestrictionAdapter]

## [1.0.2] - 2026-08-01

### Added
- Specialized Paper, Purpur, Spigot, and Leaf high-performance Minecraft server architecture.
- Multi-language localization system with 5 language bundles: English (`messages_en.yml`), Russian (`messages_ru.yml`), Spanish (`messages_es.yml`), Italian (`messages_it.yml`), and French (`messages_fr.yml`).
- Chat message color customization (`&0`-`&f`, `&l`, `&o`, etc. -> `§`), customizable command usage labels, and automatic English fallback.
- Fully customizable session configuration (`session.timeout-seconds`, `session.enabled`, `session.verify-ip`, `session.persistent`).
- Admin session management subcommands (`/authadmin session gettimeout` and `/authadmin session settimeout <seconds>`).
- Granular unauthenticated player action restrictions in `config.yml` (`freeze-player`, `allow-flight`, `block-chat`, `block-commands`, `allowed-commands`, `block-inventory`, `block-interactions`, `block-block-break`, `block-block-place`, `block-damage-taken`, `block-damage-dealt`, `block-item-drop`, `block-item-pickup`, `block-hunger`).

### Fixed
- Added exception handling for asynchronous operations, listeners, and administrative commands.
- Safe password character array clearing in memory on both success and error execution paths.

### Changed
- Updated release version to `1.0.2`.

## [1.0.1] - 2026-08-01

### Added
- Standard `.gitignore` configuring ignores for build directories (`.gradle/`, `build/`), IDE workspace settings (`.idea/`, `*.iml`), system files, and local SQLite runtime databases.

### Fixed
- Fixed SQL column mapping mismatch (`server_id` vs `serverId`) in `DatabaseSessionRepository`.
- Fixed `RedisRateLimitStore` initialization in `DependencyContainer` to instantiate `JedisPool` when Redis is enabled, and gracefully close connection pool on shutdown.
- Fixed session invalidation in `/logout` command to revoke active database sessions.
- Fixed potential `NullPointerException` race condition in `MemoryRateLimitStore` and added expiration key cleanup to prevent memory leaks.
- Fixed `countAccountsByIpHash` in SQLite & PostgreSQL repositories to accurately count registered accounts from `security_events` (`REGISTER_SUCCESS`).
- Fixed potential `NullPointerException` when calling `player.getAddress()` across commands by adding safe IP fallback (`0.0.0.0`).
- Fixed `/authadmin reload` behavior to reload YAML configurations and refresh active settings.

### Changed
- Removed duplicate Groovy build files (`build.gradle`, `settings.gradle`) in favor of clean Gradle Kotlin DSL (`build.gradle.kts`, `settings.gradle.kts`).
- Updated release version to `1.0.1`.

## [1.0.0] - 2026-08-01

### Added
- Initial release of AuthSecured plugin.
- Separated domain, application, persistence, and server-platform modules.
- Argon2id password hashing with BouncyCastle provider.
- Dual database support: PostgreSQL & SQLite with Flyway migrations.
- Rate limiting for per-account & per-IP abuse prevention.
- Pre-auth restriction system for Paper & Purpur.
- Integration test suite for SQLite and unit test suite.
