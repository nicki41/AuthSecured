# AuthSecured 1.2.1 Security Update

## Security fixes

- Argon2 verification now runs before account lock and temporary-password expiry
  responses for existing accounts.
- Failed login attempts use atomic SQLite and PostgreSQL updates.
- Concurrent duplicate registration is handled through database constraints.
- Changing a password to the same value is rejected with a constant-time check.
- Temporary passwords now use safe letters and digits only.
- Redis operations run on a dedicated executor and fall back safely on timeout.

## Administration improvements

- In-game administrators can copy a temporary password with a left click.
- Discord and Telegram have clear channel switches and configurable direct or
  environment-based credentials in `config.yml`.
- Older configuration files automatically receive the missing notification
  section without overwriting existing settings.
- Invalid reloads keep the previous valid configuration active.

## Reliability

- Non-persistent mode clears stale stored sessions at startup.
- Paper denies new logins if initialization fails.
- Database connections, statements, and result sets are closed consistently.
- New messages are translated into all five bundled languages.
