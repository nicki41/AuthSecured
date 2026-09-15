# Security Policy

## Threat model and guarantees

AuthSecured is designed around these guarantees:

1. **Password storage**: Passwords are hashed with Argon2id and a unique random salt.
2. **Timing protection**: Unknown accounts use dummy Argon2id verification. Existing accounts verify the supplied password before exposing lock or temporary-password state.
3. **Thread safety**: Password hashing, JDBC, Redis, and webhook work runs outside the Minecraft server thread.
4. **Data leakage protection**: Passwords, hashes, session tokens, raw IP addresses, webhook URLs, and bot tokens are excluded from logs and audit records.
5. **IP privacy**: Stored IP identifiers use HMAC-SHA256 with the private `AUTHSECURED_IP_SECRET` value.
6. **Fail-closed startup**: Paper and Fabric keep unauthenticated access blocked if security initialization fails.
7. **Database consistency**: Registration uniqueness and failed-login counters are enforced atomically by the database.

## Reporting Security Vulnerabilities

Do not report vulnerabilities in public issues. Submit reports privately to the project maintainer.
