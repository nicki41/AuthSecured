# Deployment Guide

## Production Network Topology

```text
Internet ──► Proxy (optional) ──► Paper/Purpur plugin or Fabric server mod
                                               │
                                               └──► SQLite or PostgreSQL + optional Redis
```

## Platform installation

- Paper-compatible server: copy `authsecured-paper-1.2.0.jar` to `plugins/`.
- Fabric server: copy the exactly matching `authsecured-fabric-1.2.0+mc*.jar`
  and Fabric API to `mods/`.
- Use Java 21 for Minecraft 1.21.x and Java 25 or newer for Minecraft 26.x.
- Never load both AuthSecured artifacts in one server process.

## Recommended Environment Variables
- `AUTHSECURED_DB_PASSWORD`: PostgreSQL database password
- `AUTHSECURED_IP_SECRET`: long random server secret for IP HMAC hashing; keep it
  stable or existing IP-bound sessions will be invalidated
- `AUTHSECURED_REDIS_URI`: Optional Redis connection string
- `AUTHSECURED_DISCORD_WEBHOOK`: Optional Discord webhook for failed-login alerts
- `AUTHSECURED_TELEGRAM_BOT_TOKEN`: Optional Telegram bot token
- `AUTHSECURED_TELEGRAM_CHAT_ID`: Telegram destination used with the bot token

Keep every credential in the server environment. Do not paste database
passwords, webhook URLs, or Telegram bot tokens directly into `config.yml`.
Enable alerts with `notifications.login-failures.enabled` and restart after
changing notification endpoints.

For proxied offline-mode backends, restrict direct backend access and configure
the proxy's secure player forwarding. Otherwise UUIDs and client addresses are
not trustworthy. Back up the database before upgrades and test the selected
platform artifact on a staging server first.
