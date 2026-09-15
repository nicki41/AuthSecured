package com.example.authsecured.bootstrap;

import com.example.authsecured.application.AuthService;
import com.example.authsecured.application.LoginService;
import com.example.authsecured.application.PasswordService;
import com.example.authsecured.application.RateLimitService;
import com.example.authsecured.application.RegistrationService;
import com.example.authsecured.application.SecurityService;
import com.example.authsecured.application.SessionService;
import com.example.authsecured.application.UnifiedCommandServiceImpl;
import com.example.authsecured.application.AuthenticationTimeoutService;
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
import com.example.authsecured.infrastructure.notification.WebhookSecurityNotifier;
import com.example.authsecured.ports.AccountRepository;
import com.example.authsecured.ports.AuditRepository;
import com.example.authsecured.ports.AuthPlatform;
import com.example.authsecured.ports.PasswordHasher;
import com.example.authsecured.ports.PlatformLogger;
import com.example.authsecured.ports.RateLimitStore;
import com.example.authsecured.ports.SessionRepository;
import com.example.authsecured.ports.UnifiedCommandExecutor;
import com.example.authsecured.ports.SecurityNotifier;
import redis.clients.jedis.JedisPool;

import java.io.File;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Builds the platform-neutral authentication graph shared by Paper and Fabric.
 */
public final class DependencyContainer implements AutoCloseable {

    private final AuthPlatform platform;
    private JedisPool jedisPool;
    private ExecutorService redisExecutor;
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private IpHashService ipHashService;
    private PasswordService passwordService;
    private SessionService sessionService;
    private AuthService authService;
    private SecurityService securityService;
    private AuthenticationTimeoutService authenticationTimeoutService;
    private UnifiedCommandExecutor commandExecutor;
    private String rateLimitBackend = "memory";
    private boolean ipHashSecretConfigured;

    public DependencyContainer(AuthPlatform platform) {
        this.platform = platform;
    }

    public void init() {
        File dataFolder = platform.getDataFolder();
        PlatformLogger logger = platform.getLogger();

        this.configManager = new ConfigManager(dataFolder, logger);
        if (!this.configManager.loadConfigurations()) {
            throw new IllegalStateException("AuthSecured configuration is invalid");
        }

        String ipHashSecret = configManager.getString("security.ip-hashing.secret-env", "");
        this.ipHashSecretConfigured = ipHashSecret != null && !ipHashSecret.isBlank();
        if (!ipHashSecretConfigured) {
            logger.warning("AUTHSECURED_IP_SECRET is not set; configure a private value before production use.");
        }
        this.ipHashService = new IpHashService(ipHashSecret);

        String dbType = configManager.getString("database.type", "sqlite").toLowerCase(Locale.ROOT);
        if (!Set.of("sqlite", "postgresql", "postgres").contains(dbType)) {
            logger.warning("Unsupported database.type '" + dbType + "'; using sqlite.");
            dbType = "sqlite";
        }
        String host = configManager.getString("database.postgresql.host", "localhost");
        int port = configManager.getInt("database.postgresql.port", 5432, 1, 65535);
        String database = configManager.getString("database.postgresql.database", "authsecured");
        String username = configManager.getString("database.postgresql.username", "authsecured");
        String password = configManager.getString("database.postgresql.password-env", "");
        String sslMode = configManager.getString("database.postgresql.ssl-mode",
                configManager.getBoolean("database.postgresql.ssl", false) ? "require" : "prefer");
        int poolSize = configManager.getInt("database.postgresql.pool-size", 10, 1, 64);
        String sqliteFile = new File(dataFolder,
                configManager.getString("database.sqlite.file", "auth.db")).getAbsolutePath();

        this.databaseManager = new DatabaseManager(logger, dbType, host, port, database, username,
                password, sqliteFile, poolSize, sslMode);

        boolean postgres = databaseManager.isPostgres();
        AccountRepository accountRepository = postgres
                ? new PostgresAccountRepository(databaseManager.getDataSource(), databaseManager.getDbExecutor())
                : new SqliteAccountRepository(databaseManager.getDataSource(), databaseManager.getDbExecutor());
        SessionRepository sessionRepository = new DatabaseSessionRepository(
                databaseManager.getDataSource(), databaseManager.getDbExecutor(), postgres);
        AuditRepository auditRepository = new DatabaseAuditRepository(
                databaseManager.getDataSource(), databaseManager.getDbExecutor(), postgres, logger);

        PasswordHasher passwordHasher = new Argon2PasswordHasher(
                configManager.getInt("security.password.argon2.memory-kib", 65536, 8192, 1_048_576),
                configManager.getInt("security.password.argon2.iterations", 3, 1, 20),
                configManager.getInt("security.password.argon2.parallelism", 2, 1, 32),
                configManager.getInt("security.password.argon2.hash-length", 32, 16, 64),
                configManager.getInt("security.password.argon2.salt-length", 16, 16, 64));

        RateLimitStore rateLimitStore = createRateLimitStore(logger);
        int minLength = configManager.getInt("security.password.min-length", 10, 8, 128);
        int maxLength = configManager.getInt("security.password.max-length", 128, minLength, 1024);
        this.passwordService = new PasswordService(passwordHasher, minLength, maxLength, 4);

        int maxAccountAttempts = configManager.getInt(
                "security.rate-limit.login.per-account.max-attempts", 5, 1, 10_000);
        long accountWindowSeconds = configManager.getInt(
                "security.rate-limit.login.per-account.window-seconds", 600, 1, 86_400);
        long accountLockSeconds = configManager.getInt(
                "security.rate-limit.login.per-account.lock-seconds", 900, 1, 604_800);
        int maxIpAttempts = configManager.getInt(
                "security.rate-limit.login.per-ip.max-attempts", 20, 1, 100_000);
        long ipWindowSeconds = configManager.getInt(
                "security.rate-limit.login.per-ip.window-seconds", 600, 1, 86_400);
        long ipLockSeconds = configManager.getInt(
                "security.rate-limit.login.per-ip.lock-seconds", 1800, 1, 604_800);

        RateLimitService rateLimitService = new RateLimitService(
                rateLimitStore,
                maxAccountAttempts,
                accountWindowSeconds,
                accountLockSeconds,
                maxIpAttempts,
                ipWindowSeconds,
                ipLockSeconds,
                configManager.getInt("security.registration.max-accounts-per-ip", 3, 1, 1_000),
                configManager.getInt("security.registration.max-attempts-per-hour", 10, 1, 100_000));

        long sessionTimeoutSeconds = configManager.getInt(
                "session.timeout-seconds", 1800, 1, 2_592_000);
        this.sessionService = new SessionService(
                sessionRepository,
                configManager.getBoolean("session.enabled", true),
                configManager.getBoolean("session.persistent", true),
                sessionTimeoutSeconds,
                configManager.getBoolean("session.verify-ip", true));
        this.sessionService.initialize().join();

        this.securityService = new SecurityService(
                auditRepository, configManager.getBoolean("logging.audit.enabled", true));
        SecurityNotifier securityNotifier = createSecurityNotifier(logger);
        RegistrationService registrationService = new RegistrationService(
                accountRepository, passwordService, rateLimitService, securityService);
        LoginService loginService = new LoginService(
                accountRepository,
                passwordService,
                rateLimitService,
                sessionService,
                securityService,
                maxAccountAttempts,
                accountLockSeconds,
                securityNotifier);

        this.authenticationTimeoutService = new AuthenticationTimeoutService(
                configManager.getBoolean("auth-timeout.enabled", true),
                configManager.getInt("auth-timeout.timeout-seconds", 60, 5, 3_600));

        this.authService = new AuthService(registrationService, loginService, passwordService,
                sessionService, securityService, accountRepository);
        this.commandExecutor = new UnifiedCommandServiceImpl(authService, platform,
                configManager.getLocalizationManager(), ipHashService, authenticationTimeoutService);
    }

    private RateLimitStore createRateLimitStore(PlatformLogger logger) {
        if (!configManager.getBoolean("redis.enabled", false)) {
            this.rateLimitBackend = "memory";
            return new MemoryRateLimitStore();
        }

        String redisUri = configManager.getString("redis.uri-env", "");
        String redisHost = configManager.getString("redis.host", "localhost");
        int redisPort = configManager.getInt("redis.port", 6379, 1, 65535);
        try {
            this.jedisPool = redisUri != null && !redisUri.isBlank()
                    ? new JedisPool(redisUri)
                    : new JedisPool(redisHost, redisPort);
            this.rateLimitBackend = "redis";
            this.redisExecutor = Executors.newFixedThreadPool(2, runnable -> {
                Thread thread = new Thread(runnable, "authsecured-redis");
                thread.setDaemon(true);
                return thread;
            });
            return new RedisRateLimitStore(jedisPool, logger, redisExecutor);
        } catch (RuntimeException exception) {
            logger.warning("Failed to initialize Redis pool; falling back to memory. Connection details are hidden.");
            this.rateLimitBackend = "memory-fallback";
            return new MemoryRateLimitStore();
        }
    }

    private SecurityNotifier createSecurityNotifier(PlatformLogger logger) {
        if (!configManager.getBoolean("notifications.login-failures.enabled", false)) {
            return SecurityNotifier.NOOP;
        }
        String discord = configManager.getBoolean("notifications.discord.enabled", true)
                ? firstNonBlank(configManager.getString("notifications.discord.webhook-url", ""),
                configManager.getString("notifications.discord.webhook-url-env", "")) : "";
        String telegramToken = configManager.getBoolean("notifications.telegram.enabled", true)
                ? firstNonBlank(configManager.getString("notifications.telegram.bot-token", ""),
                configManager.getString("notifications.telegram.bot-token-env", "")) : "";
        String telegramChat = configManager.getBoolean("notifications.telegram.enabled", true)
                ? firstNonBlank(configManager.getString("notifications.telegram.chat-id", ""),
                configManager.getString("notifications.telegram.chat-id-env", "")) : "";
        return new WebhookSecurityNotifier(discord, telegramToken, telegramChat,
                configManager.getInt("notifications.login-failures.cooldown-seconds", 300, 10, 86_400),
                logger);
    }

    public boolean reloadConfig() {
        if (configManager != null) {
            if (!configManager.loadConfigurations()) return false;
            if (sessionService != null) {
                sessionService.setTimeoutSeconds(configManager.getInt(
                        "session.timeout-seconds", (int) sessionService.getTimeoutSeconds(), 1, 2_592_000));
            }
            if (authenticationTimeoutService != null) {
                authenticationTimeoutService.configure(
                        configManager.getBoolean("auth-timeout.enabled", authenticationTimeoutService.isEnabled()),
                        configManager.getInt("auth-timeout.timeout-seconds",
                                (int) authenticationTimeoutService.getTimeoutSeconds(), 5, 3_600));
            }
            return true;
        }
        return false;
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
        if (redisExecutor != null) {
            redisExecutor.shutdownNow();
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

    public AuthenticationTimeoutService getAuthenticationTimeoutService() {
        return authenticationTimeoutService;
    }

    public SecurityService getSecurityService() {
        return securityService;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public String getRateLimitBackend() {
        return rateLimitBackend;
    }

    public boolean isIpHashSecretConfigured() {
        return ipHashSecretConfigured;
    }

    private static String firstNonBlank(String primary, String fallback) {
        return primary != null && !primary.isBlank() ? primary : (fallback == null ? "" : fallback);
    }
}
