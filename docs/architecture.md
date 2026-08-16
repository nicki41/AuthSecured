# AuthSecured Architecture Overview

```text
Paper / Bukkit adapter              Fabric server adapter
          │                                  │
          └──────────► platform-common ◄─────┘
                             │
                             ▼
            core application and domain services
                             │
                             ▼
       database adapters (SQLite/PostgreSQL) and Redis
```

1. **Domain Layer**: Contains immutable domain models (`Account`, `Session`, `AuthState`, `LoginAttempt`, `SecurityEvent`).
2. **Ports Layer**: Java interfaces defining storage and security contracts.
3. **Application Layer**: Business orchestration rules and rate-limiting enforcement.
4. **Infrastructure Layer**: Concrete persistence with HikariCP, Flyway, BouncyCastle, and Redis.
5. **Platform Layer**: Paper listeners/Bukkit commands and Fabric
   events/Brigadier commands translate server APIs into the same core ports.

`platform-common` owns dependency construction so both runtimes receive the same
password policy, rate limits, session semantics, localization, and persistence.
Platform modules contain no duplicated authentication business rules.
