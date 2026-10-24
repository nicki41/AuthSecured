# AuthSecured 1.2.5 Fabric Compatibility Fix

Recommended for Fabric servers that also run Ledger. Paper is unchanged.

## Fixes

- Fabric: the bundled SQLite driver is now version 3.49.1.0 (was 3.53.4.0). The driver cannot
  be relocated and takes precedence over other mods' copies, and the newer one made Ledger
  crash on startup with an `ALTER TABLE players MODIFY COLUMN` error.

## Notes

- No configuration changes are required.
- Paper and Fabric remain separate downloads; each Fabric file targets one exact Minecraft
  version and requires Fabric API.
