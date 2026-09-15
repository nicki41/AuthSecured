# AuthSecured 1.2.0

## New administration tools

- Added `/authadmin status` and `/authadmin doctor`.
- Added recent audit viewing with an optional player filter.
- Added tab completion and separate permissions for every admin operation.
- Fixed `/authadmin session settimeout`: it now changes the live timeout used by
  newly created sessions.

## Authentication and security

- Added a configurable login and registration deadline with automatic kick.
- Paper now blocks its built-in raw log records for `/login`, `/register`,
  `/changepassword`, and aliases before the password can reach console output.
  The later command event is also replaced with `[REDACTED]` before dispatch.
- Password reset now generates an expiring temporary password and revokes all
  sessions. The player must immediately choose a permanent password.
- Added optional privacy-safe failed-login alerts for Discord webhooks and
  Telegram bots. Alerts never include passwords, raw IP addresses, or tokens.

## Compatibility

- Paper-compatible server JAR: Minecraft 1.21 through 26.2.
- Fabric: exact JARs for every supported version from 1.21 through 26.2.
- Existing SQLite and PostgreSQL databases are upgraded automatically.
