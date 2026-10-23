package io.github.touchfalls.authsecured.application;

import io.github.touchfalls.authsecured.domain.account.Account;
import io.github.touchfalls.authsecured.domain.auth.AuthResult;
import io.github.touchfalls.authsecured.domain.security.SecurityEventType;
import io.github.touchfalls.authsecured.ports.AccountRepository;
import io.github.touchfalls.authsecured.ports.SecurityNotifier;
import io.github.touchfalls.authsecured.ports.PlatformLogger;
import io.github.touchfalls.authsecured.util.FailureDiagnostics;
import io.github.touchfalls.authsecured.util.UsernameNormalizer;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class LoginService {

    /**
     * Outcome of a password check. {@code account} is present only when the
     * supplied password was verified and the account may continue the flow:
     * {@link AuthResult#SUCCESS} or {@link AuthResult#PASSWORD_CHANGE_REQUIRED}.
     */
    public record CredentialCheck(AuthResult result, Account account, String usernameNormalized) {
        public boolean accepted() {
            return account != null
                    && (result == AuthResult.SUCCESS || result == AuthResult.PASSWORD_CHANGE_REQUIRED);
        }
    }

    private final AccountRepository accountRepository;
    private final PasswordService passwordService;
    private final RateLimitService rateLimitService;
    private final SessionService sessionService;
    private final SecurityService securityService;
    private final int maxFailedAttempts;
    private final long lockDurationSeconds;
    private final SecurityNotifier securityNotifier;
    private final PlatformLogger logger;

    public LoginService(AccountRepository accountRepository, PasswordService passwordService,
                        RateLimitService rateLimitService, SessionService sessionService,
                        SecurityService securityService, int maxFailedAttempts, long lockDurationSeconds) {
        this(accountRepository, passwordService, rateLimitService, sessionService, securityService,
                maxFailedAttempts, lockDurationSeconds, SecurityNotifier.NOOP, null);
    }

    public LoginService(AccountRepository accountRepository, PasswordService passwordService,
                        RateLimitService rateLimitService, SessionService sessionService,
                        SecurityService securityService, int maxFailedAttempts, long lockDurationSeconds,
                        SecurityNotifier securityNotifier) {
        this(accountRepository, passwordService, rateLimitService, sessionService, securityService,
                maxFailedAttempts, lockDurationSeconds, securityNotifier, null);
    }

    public LoginService(AccountRepository accountRepository, PasswordService passwordService,
                        RateLimitService rateLimitService, SessionService sessionService,
                        SecurityService securityService, int maxFailedAttempts, long lockDurationSeconds,
                        SecurityNotifier securityNotifier, PlatformLogger logger) {
        this.accountRepository = accountRepository;
        this.passwordService = passwordService;
        this.rateLimitService = rateLimitService;
        this.sessionService = sessionService;
        this.securityService = securityService;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockDurationSeconds = lockDurationSeconds;
        this.securityNotifier = securityNotifier == null ? SecurityNotifier.NOOP : securityNotifier;
        this.logger = logger;
    }

    public CompletableFuture<AuthResult> login(UUID playerUuid, String username, char[] password, byte[] ipHash) {
        if (ipHash == null || ipHash.length == 0) {
            if (logger != null) {
                logger.severe("Login rejected because the client IP is unavailable. Check proxy forwarding.");
            }
            return CompletableFuture.completedFuture(AuthResult.INTERNAL_ERROR);
        }
        return verifyCredentials(playerUuid, username, password, ipHash)
                .thenCompose(check -> {
                    if (!check.accepted()) {
                        return CompletableFuture.completedFuture(check.result());
                    }
                    Account account = check.account();
                    Instant now = Instant.now();
                    if (check.result() == AuthResult.PASSWORD_CHANGE_REQUIRED) {
                        return recordSuccessfulAuthentication(account, check.usernameNormalized(), now)
                                .thenCompose(v -> securityService.logLoginAttempt(account.getId(), ipHash, true,
                                        "TEMPORARY_PASSWORD_ACCEPTED"))
                                .thenApply(v -> AuthResult.PASSWORD_CHANGE_REQUIRED);
                    }
                    return recordSuccessfulAuthentication(account, check.usernameNormalized(), now)
                            .thenCompose(v -> sessionService.createSession(
                                    account.getId(), playerUuid, ipHash, "default-server"))
                            .thenCompose(session -> securityService.logLoginAttempt(account.getId(), ipHash, true, "SUCCESS"))
                            .thenCompose(v -> securityService.logSecurityEvent(account.getId(), SecurityEventType.LOGIN_SUCCESS, ipHash, "{}"))
                            .thenApply(v -> AuthResult.SUCCESS);
                }).exceptionally(ex -> {
                    if (logger != null) logger.severe("Login failed internally ("
                            + FailureDiagnostics.causeTypes(ex) + "). Sensitive details are hidden.");
                    return AuthResult.INTERNAL_ERROR;
                });
    }

    /**
     * Verifies a password with every brute-force protection that applies to
     * {@code /login}: IP and account rate limits, the persistent failed-attempt
     * counter, account locks and temporary-password expiry. Failures are audited
     * and reported to the security notifier. Callers must not treat the password
     * as proven unless {@link CredentialCheck#accepted()} is true.
     *
     * <p>A locked account always yields {@link AuthResult#ACCOUNT_LOCKED}, for
     * correct and wrong passwords alike, after the same Argon2 work, so the lock
     * cannot be used as an oracle that confirms a guessed password.</p>
     *
     * @param username player name used for account rate limits; when blank the
     *                 stored account name is used
     */
    public CompletableFuture<CredentialCheck> verifyCredentials(UUID playerUuid, String username,
                                                                char[] password, byte[] ipHash) {
        if (playerUuid == null || password == null || ipHash == null || ipHash.length == 0) {
            return CompletableFuture.completedFuture(new CredentialCheck(AuthResult.INTERNAL_ERROR, null, ""));
        }
        String requestedName = UsernameNormalizer.normalize(username);
        String alertName = username == null || username.isBlank() ? requestedName : username;

        return rateLimitService.isIpRateLimited(ipHash).thenCompose(ipLimited -> {
            if (ipLimited) {
                securityNotifier.loginFailure(alertName, "IP_RATE_LIMITED");
                return securityService.logLoginAttempt(null, ipHash, false, "IP_RATE_LIMITED")
                        .thenApply(ignored -> rejected(AuthResult.RATE_LIMITED, requestedName));
            }
            return accountRepository.findByUuid(playerUuid).thenCompose(optAccount -> {
                Account account = optAccount.orElse(null);
                String normalized = account != null && requestedName.isBlank()
                        ? account.getUsernameNormalized() : requestedName;
                return rateLimitService.isAccountRateLimited(normalized).thenCompose(accountLimited -> {
                    if (accountLimited) {
                        securityNotifier.loginFailure(alertName, "ACCOUNT_RATE_LIMITED");
                        return securityService.logLoginAttempt(account == null ? null : account.getId(),
                                        ipHash, false, "ACCOUNT_RATE_LIMITED")
                                .thenApply(ignored -> rejected(AuthResult.RATE_LIMITED, normalized));
                    }
                    if (account == null) {
                        // Timing protection: verify a dummy hash when the account does not exist.
                        return passwordService.verifyDummyAsync(password)
                                .thenCompose(ignored -> rateLimitService.recordFailedLoginAttempt(normalized, ipHash))
                                .thenCompose(ignored -> securityService.logLoginAttempt(
                                        null, ipHash, false, "INVALID_CREDENTIALS"))
                                .thenApply(ignored -> {
                                    securityNotifier.loginFailure(alertName, "INVALID_CREDENTIALS");
                                    return rejected(AuthResult.INVALID_PASSWORD, normalized);
                                });
                    }
                    return passwordService.verifyPasswordAsync(password, account.getPasswordHash())
                            .thenCompose(valid -> evaluate(account, valid, normalized, alertName, ipHash));
                });
            });
        });
    }

    private CompletableFuture<CredentialCheck> evaluate(Account account, boolean valid, String normalized,
                                                        String alertName, byte[] ipHash) {
        Instant now = Instant.now();
        if (account.isLocked(now)) {
            // Identical handling for correct and wrong passwords: the response
            // must not reveal whether a guess was right while the lock is active.
            securityNotifier.loginFailure(alertName, "ACCOUNT_LOCKED");
            return rateLimitService.recordFailedLoginAttempt(normalized, ipHash)
                    .thenCompose(v -> securityService.logLoginAttempt(account.getId(), ipHash, false, "ACCOUNT_LOCKED"))
                    .thenApply(v -> rejected(AuthResult.ACCOUNT_LOCKED, normalized));
        }
        if (!valid) {
            securityNotifier.loginFailure(alertName, "INVALID_PASSWORD");
            return accountRepository.incrementFailedAttemptsAtomic(
                            account.getId(), maxFailedAttempts, lockDurationSeconds, now)
                    .thenCompose(updated -> updated
                            ? CompletableFuture.completedFuture(null)
                            : CompletableFuture.failedFuture(new IllegalStateException("Account disappeared")))
                    .thenCompose(v -> rateLimitService.recordFailedLoginAttempt(normalized, ipHash))
                    .thenCompose(v -> securityService.logLoginAttempt(account.getId(), ipHash, false, "INVALID_PASSWORD"))
                    .thenApply(v -> rejected(AuthResult.INVALID_PASSWORD, normalized));
        }
        if (account.isTemporaryPasswordExpired(now)) {
            return securityService.logLoginAttempt(account.getId(), ipHash, false, "TEMPORARY_PASSWORD_EXPIRED")
                    .thenCompose(v -> securityService.logSecurityEvent(
                            account.getId(), SecurityEventType.TEMPORARY_PASSWORD_EXPIRED, ipHash, "{}"))
                    .thenApply(v -> rejected(AuthResult.TEMPORARY_PASSWORD_EXPIRED, normalized));
        }
        AuthResult result = account.isPasswordChangeRequired()
                ? AuthResult.PASSWORD_CHANGE_REQUIRED : AuthResult.SUCCESS;
        return CompletableFuture.completedFuture(new CredentialCheck(result, account, normalized));
    }

    /** Clears the persistent failed-attempt counter and the rate-limit state after a proven password. */
    public CompletableFuture<Void> recordSuccessfulAuthentication(Account account, String usernameNormalized,
                                                                  Instant now) {
        String normalized = usernameNormalized == null || usernameNormalized.isBlank()
                ? account.getUsernameNormalized() : usernameNormalized;
        return accountRepository.resetFailedAttemptsAtomic(account.getId(), now)
                .thenCompose(updated -> updated
                        ? rateLimitService.recordSuccessfulLogin(normalized)
                        : CompletableFuture.failedFuture(new IllegalStateException("Account disappeared")));
    }

    private static CredentialCheck rejected(AuthResult result, String normalized) {
        return new CredentialCheck(result, null, normalized);
    }
}
