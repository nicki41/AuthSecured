# AuthSecured 1.2.3 Security Update

Recommended for every server. This release closes pre-login bypasses found in a security audit.

## Added

- Fabric support for Minecraft 26.3, using Fabric API 0.164.0+26.3.

## Security fixes

- `/changepassword` can no longer be used to guess passwords before logging in. It now goes
  through the same rate limits, failed-attempt counter, account lock and temporary-password
  expiry checks as `/login`. Before login it only works to finish a temporary-password reset.
- Fabric: players who have not logged in can no longer run commands through the signed-command
  packet (`/msg`, `/me`, or any command sent by a modified client). Every command is checked a
  second time right before it runs.
- A locked account now gives the same answer for right and wrong passwords, so the lock cannot
  be used to confirm a guessed password.
- The built-in (non-Redis) rate limiter can no longer be flooded into locking every player out.
- Paper: if the plugin fails to start for any reason, new logins stay blocked instead of the
  server running without authentication.

## Changes

- Players who have not logged in or registered cannot move at all: no walking, jumping,
  riding, gliding, ender pearls, portals or spectator teleports. Looking around still works.
  Controlled by `restrictions.freeze-player` (enabled by default).

## Fixes

- PostgreSQL: security audit events are stored correctly again. They were rejected before, which
  also disabled the `max-accounts-per-ip` registration limit.
- Spigot: all pre-login restrictions now load, and `/authadmin resetpassword` no longer needs
  Paper-only APIs.

## Notes

- No configuration changes are required. Paper and Fabric remain separate downloads; each
  Fabric file targets one exact Minecraft version and requires Fabric API.
