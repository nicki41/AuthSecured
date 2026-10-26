# AuthSecured 1.2.6

Includes the 1.2.5 Fabric compatibility fix plus seven community contributions from @nicki41.

## What's new

- `/authadmin doctor` now also reports IP-unbound sessions on an offline-mode server, Argon2
  settings below the OWASP minimum, and unencrypted PostgreSQL connections to a remote host.
- Paper: players who have not logged in only see (and can tab-complete) the commands in
  `restrictions.allowed-commands`, so a player joining under an operator's name can no longer
  browse that operator's commands before logging in.
- The Argon2 and database worker threads have descriptive names, which makes thread dumps and
  profiler output (spark) readable.

## Contributors

A big thank you to [@nicki41](https://github.com/nicki41) for seven pull requests in this
release: the new doctor checks (#8), hiding commands before login on Paper (#9), the worker
thread names (#10), re-hashing on login (#7), command alias protection (#4), ending
authentication on admin reset/unregister (#3) and the GitHub Actions build (#5).

- `/authadmin resetpassword` and `/authadmin unregister` now also log out the account owner if
  they are online (Paper and Fabric), so a compromised account loses access immediately.
- Paper: password commands whose aliases are taken by another plugin (for example `/l`) are
  still redacted from the console log and intercepted before login.
- Passwords hashed with older, weaker Argon2 settings are automatically re-hashed with your
  current settings the next time the player logs in.

## Fabric compatibility

- The bundled SQLite driver is 3.49.1.0 (since 1.2.5). It cannot be relocated and takes
  precedence over other mods' copies; the older 3.53.x made Ledger crash on startup with an
  `ALTER TABLE players MODIFY COLUMN` error.

## Notes

- No configuration changes are required.
- Paper and Fabric remain separate downloads; each Fabric file targets one exact Minecraft
  version and requires Fabric API.
