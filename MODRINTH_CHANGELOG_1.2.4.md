# AuthSecured 1.2.4 Security & Reliability Update

Recommended for every server. This release closes the remaining issues from a follow-up
security and quality audit.

## Security fixes

- The built-in (non-Redis) rate limiter could be flooded with cheap fake login names to wipe
  out a real account's failed-attempt count. It now evicts the least-attacked entry first,
  so an attack can no longer erase a victim's protection.
- The per-account-per-IP registration limit is now read from the accounts table directly
  instead of the audit log, so it keeps working even with audit logging disabled or its
  retention period passed.
- After a lock expires, one more wrong password no longer immediately re-locks the account
  for another full lockout period.
- IPv6 addresses are rounded to their /64 network block before rate limiting and before the
  per-IP registration cap, closing a bypass through address rotation within one network.
- Fabric: if a player disconnects and reconnects (under the same name) while a login,
  register or password-change request is still being checked, the old request's result can
  no longer be applied to the new connection.
- `/authadmin resetpassword` now refuses to run from the server console or RCON, since those
  would otherwise write the generated password to the server log.
- Discord/Telegram failed-login alerts no longer fire for attempts already blocked by a rate
  limit, and the alert cooldown tracking can no longer be permanently jammed by an attacker
  rotating through many fake names.

## Reliability fixes

- Password-change, account-unlock and temporary-password operations now update only their
  own fields in the database instead of the whole account row, removing a rare race where a
  slow request could overwrite a faster one's lockout state.
- Redis's attempt counter and its expiry are now set in one atomic step, so a dropped
  connection can no longer leave a counter that never expires.
- `/authadmin unlock` and issuing a temporary password now also clear the player's rate-limit
  lock, so the action takes effect right away instead of leaving them rate-limited.
- The password-hashing queue is now bounded, so a flood of login attempts is rejected once the
  server is busy instead of growing memory use without limit.
- A failed database migration no longer leaves the connection pool open.
- `/logout` now ends every active session for the account, not only the most recent one.
- Fabric's `/login`, `/register` and `/changepassword` now accept the same password
  characters Paper already does, with no need to quote special characters.

## Maintenance

- Jackson (pulled in by the database migration library) is now properly relocated inside
  both the Paper and Fabric jars, preventing a conflict with other plugins/mods that bundle a
  different version.
- Paper's commands now share the exact same authentication code Fabric uses, instead of a
  separate copy — removing the kind of duplication that caused the pre-login
  `/changepassword` issue fixed in 1.2.3.
- `/authadmin doctor` now warns when the IP-hashing secret is a per-server generated file but
  the server is using a shared Redis or PostgreSQL backend, since every server sharing that
  backend then needs the identical secret.

## Notes

- No configuration changes are required.
- Paper and Fabric remain separate downloads; each Fabric file targets one exact Minecraft
  version and requires Fabric API.
