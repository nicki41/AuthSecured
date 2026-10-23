# AuthSecured 1.2.2 Security Update

## Security fixes

- A private `AUTHSECURED_IP_SECRET` is now required. Servers without it keep authentication blocked until it is configured.
- Unknown player IP addresses no longer share one rate-limit identity or create IP-bound reconnect sessions.
- Malformed Argon2id hashes cannot request excessive memory or work factors.
- SQLite foreign keys now enforce session deletion when an account is removed.
- Optional Redis fallback is visible in `/authadmin doctor`. Set `redis.required: true` to stop authentication when shared Redis is unavailable.
- Discord alerts accept official HTTPS webhook endpoints only.

## Reliability and maintenance

- Expired sessions and old audit entries are cleaned periodically outside the server thread.
- Configure `logging.audit.retention-days` and `logging.audit.cleanup-interval-seconds` in `config.yml`.
- SQLite now records its schema version and safely upgrades existing databases.
- Internal errors produce useful, privacy-safe diagnostics.

## Packaging

- Updated dependencies after a vulnerability scan.
- Reduced shaded JAR size and isolated bundled JSON libraries.
- Paper and Fabric remain separate downloads; each Fabric file targets one exact Minecraft version.
