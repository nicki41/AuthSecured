package com.example.authsecured.bootstrap;

import com.example.authsecured.application.AuthService;
import com.example.authsecured.application.LoginService;
import com.example.authsecured.application.PasswordService;
import com.example.authsecured.application.RateLimitService;
import com.example.authsecured.application.RegistrationService;
import com.example.authsecured.application.SecurityService;
import com.example.authsecured.application.SessionService;
import com.example.authsecured.application.UnifiedCommandServiceImpl;
import com.example.authsecured.infrastructure.config.ConfigManager;
import com.example.authsecured.infrastructure.database.DatabaseAuditRepository;
import com.example.authsecured.infrastructure.database.DatabaseManager;
import com.example.authsecured.infrastructure.database.DatabaseSessionRepository;
import com.example.authsecured.infrastructure.database.PostgresAccountRepository;
import com.example.authsecured.infrastructure.database.SqliteAccountRepository;
import com.example.authsecured.infrastructure.redis.MemoryRateLimitStore;
import com.example.authsecured.infrastructure.redis.RedisRateLimitStore;
import com.example.authsecured.infrastructure.security.Argon2PasswordHasher;
import com.example.authsecured.infrastructure.security.IpHashService;
import com.example.authsecured.ports.AccountRepository;
import com.example.authsecured.ports.AuditRepository;
import com.example.authsecured.ports.AuthPlatform;
import com.example.authsecured.ports.PasswordHasher;
import com.example.authsecured.ports.PlatformLogger;
import com.example.authsecured.ports.RateLimitStore;
import com.example.authsecured.ports.SessionRepository;
import com.example.authsecured.ports.UnifiedCommandExecutor;
import redis.clients.jedis.JedisPool;

import java.io.File;

/**
 * Builds the platform-neutral authentication graph shared by Paper and Fabric.
 */
public final class DependencyContainer implements AutoCloseable {

    private final AuthPlatform platform;
    private JedisPool jedisPool;
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private IpHashService ipHashService;
    private PasswordService passwordService;
    private SessionService sessionService;
    private AuthService authService;
    private UnifiedCommandExecutor commandExecutor;

    public DependencyContainer(AuthPlatform platform) {
        this.platform = platform;
    }

    public void init() {
        File dataFolder = platform.getDataFolder();
        PlatformLogger logger = platform.getLogger();

        this.configManager = new ConfigManager(dataFolder, logger);
        this.configManager.loadConfigurations();

        String ipHashSecret = configManager.getString("security.ip-hashing.secret-env", "");
        if (ipHashSecret == null || ipHashSecret.isBlank()) {
            logger.warning("AUTHSECURED_IP_SECRET is not set; configure a private value before production use.");
        }
        this.ipHashService = new IpHashService(ipHashSecret);

        String dbType = configManager.getString("database.type", "sqlite");
        String host = configManager.getString("database.postgresql.host", "localhost");
        int port = configManager.getInt("database.postgresql.port", 5432);
        String database = configManager.getString("database.postgresql.database", "authsecured");
        String username = configManager.getString("database.postgresql.username", "authsecured");
        String password = configManager.getString("database.postgresql.password-env", "");
        int poolSize = configManager.getInt("database.postgresql.pool-size", 10);
        String sqliteFile = new File(dataFolder,
                configManager.getString("database.sqlite.file", "auth.db")).getAbsolutePath();

        this.databaseManager = new DatabaseManager(logger, dbType, host, port, database, username,
                password, sqliteFile, poolSize);

        boolean postgres = databaseManager.isPostgres();
        AccountRepository accountRepository = postgres
                ? new PostgresAccountRepository(databaseManager.getDataSource(), databaseManager.getDbExecutor())
                : new SqliteAccountRepository(databaseManager.getDataSource(), databaseManager.getDbExecutor());
        SessionRepository sessionRepository = new DatabaseSessionRepository(
                databaseManager.getDataSource(), databaseManager.getDbExecutor(), postgres);
        AuditRepository auditRepository = new DatabaseAuditRepository(
                databaseManager.getDataSource(), databaseManager.getDbExecutor(), postgres, logger);

        PasswordHasher passwordHasher = new Argon2PasswordHasher(
                configManager.getInt("security.password.argon2.memory-kib", 65536),
                configManager.getInt("security.password.argon2.iterations", 3),
                configManager.getInt("security.password.argon2.parallelism", 2),
                configManager.getInt("security.password.argon2.hash-length", 32),
                configManager.getInt("security.password.argon2.salt-length", 16));

        RateLimitStore rateLimitStore = createRateLimitStore(logger);
        int minLength = configManager.getInt("security.password.min-length", 10);
        int maxLength = configManager.getInt("security.password.max-length", 128);
        this.passwordService = new PasswordService(passwordHasher, minLength, maxLength, 4);

        int maxAccountAttempts = configManager.getInt(
                "security.rate-limit.login.per-account.max-attempts", 5);
        long accountWindowSeconds = configManager.getInt(
                "security.rate-limit.login.per-account.window-seconds", 600);
        long accountLockSeconds = configManager.getInt(
                "security.rate-limit.login.per-account.lock-seconds", 900);
        int maxIpAttempts = configManager.getInt("security.rate-limit.login.per-ip.max-attempts", 20);
        long ipWindowSeconds = configManager.getInt(
                "security.rate-limit.login.per-ip.window-seconds", 600);
        long ipLockSeconds = configManager.getInt("security.rate-limit.login.per-ip.lock-seconds", 1800);

        RateLimitService rateLimitService = new RateLimitService(
                rateLimitStore,
                maxAccountAttempts,
                accountWindowSeconds,
                accountLockSeconds,
                maxIpAttempts,
                ipWindowSeconds,
                ipLockSeconds,
                configManager.getInt("security.registration.max-accounts-per-ip", 3),
                configManager.getInt("security.registration.max-attempts-per-hour", 10));

        long sessionTimeoutSeconds = configManager.getInt("session.timeout-seconds", 1800);
        if (sessionTimeoutSeconds <= 0) {
            sessionTimeoutSeconds = configManager.getInt("session.timeout-minutes", 30) * 60L;
        }
        this.sessionService = new SessionService(
                sessionRepository,
                configManager.getBoolean("session.enabled", true),
                configManager.getBoolean("session.persistent", true),
                sessionTimeoutSeconds,
                configManager.getBoolean("session.verify-ip", true));

        SecurityService securityService = new SecurityService(
                auditRepository, configManager.getBoolean("logging.audit.enabled", true));
        RegistrationService registrationService = new RegistrationService(
                accountRepository, passwordService, rateLimitService, securityService);
        LoginService loginService = new LoginService(
                accountRepository,
                passwordService,
                rateLimitService,
                sessionService,
                securityService,
                maxAccountAttempts,
                accountLockSeconds);

        this.authService = new AuthService(registrationService, loginService, passwordService,
                sessionService, securityService, accountRepository);
        this.commandExecutor = new UnifiedCommandServiceImpl(authService, platform,
                configManager.getLocalizationManager(), ipHashService);
    }

    private RateLimitStore createRateLimitStore(PlatformLogger logger) {
        if (!configManager.getBoolean("redis.enabled", false)) {
            return new MemoryRateLimitStore();
        }

        String redisUri = configManager.getString("redis.uri-env", "");
        String redisHost = configManager.getString("redis.host", "localhost");
        int redisPort = configManager.getInt("redis.port", 6379);
        try {
            this.jedisPool = redisUri != null && !redisUri.isBlank()
                    ? new JedisPool(redisUri)
                    : new JedisPool(redisHost, redisPort);
            return new RedisRateLimitStore(jedisPool, logger);
        } catch (RuntimeException exception) {
            logger.warning("Failed to initialize Redis pool, falling back to memory: "
                    + exception.getMessage());
            return new MemoryRateLimitStore();
        }
    }

    public void reloadConfig() {
        if (configManager != null) {
            configManager.loadConfigurations();
        }
    }

    @Override
    public void close() {
        if (passwordService != null) {
            passwordService.shutdown();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
        }
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public AuthService getAuthService() {
        return authService;
    }

    public UnifiedCommandExecutor getCommandExecutor() {
        return commandExecutor;
    }

    public IpHashService getIpHashService() {
        return ipHashService;
    }

    public SessionService getSessionService() {
        return sessionService;
    }
}
