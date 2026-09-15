# AuthSecured 1.2.1 Security Walkthrough

## Authentication flow

Existing accounts always pass through password hash verification before the
service returns an account lock or expired temporary-password result. A wrong
password returns the same invalid-password result regardless of those states.
Failed-attempt increments and successful-attempt resets are single SQL updates,
so concurrent requests cannot overwrite each other's counters.

Registration keeps the quick pre-checks for normal feedback, but database unique
constraints are the final authority. SQLite and PostgreSQL uniqueness failures
use a dedicated exception that is converted to `ALREADY_REGISTERED`.

Password changes compare the verified old password with the requested new value
in constant time. The service does not hash or store an unchanged password.

## Temporary password reset

`/authadmin resetpassword <player>` generates an expiring password from
unambiguous ASCII letters and digits only. It contains no punctuation, spaces,
or command-sensitive characters. The highlighted value is clickable in the
in-game Paper and Fabric administrator chat and copies to the clipboard with a
left click. The reset still revokes active sessions and requires a permanent
password change before authentication completes.

## Configuration and notifications

Discord and Telegram each have an `enabled` switch. Direct credential fields are
available for simple setups, while environment-variable fields remain the safer
production option. The global `notifications.login-failures.enabled` switch and
cooldown still apply to both channels. An older configuration with no
notifications section is extended through an atomic file replacement.

Configuration reload parses and validates a separate snapshot first. Invalid
language, security ranges, timeouts, ports, permission levels, or boolean values
cause the reload to fail while the previous snapshot remains active.

## Storage and concurrency

Non-persistent session mode deletes old database sessions before session work is
accepted. JDBC operations in the session and audit repositories use
try-with-resources for connections, statements, and result sets. Redis work runs
on two dedicated daemon threads, has a five-second timeout, and uses the
thread-safe in-memory fallback without blocking the server thread.

## Platform safety

Paper callbacks return to the main server thread before using player APIs and
verify that the player object is still the current online connection. If Paper
initialization fails, a minimal login listener remains active and denies all new
connections. Fabric already keeps restrictions active while initialization is
incomplete or failed.

## Verification

The verification suite covers timing-safe locked-account behavior, atomic failed
attempts, duplicate registration handling, unchanged password rejection,
non-persistent session cleanup, safe temporary-password characters, config
migration, rejected invalid reloads, and JDBC pool release. The release build is
also compiled for every supported Fabric Minecraft version.
