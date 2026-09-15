package com.example.authsecured.application;

import com.example.authsecured.domain.account.Account;
import com.example.authsecured.domain.auth.AuthResult;
import com.example.authsecured.domain.security.SecurityEventType;
import com.example.authsecured.ports.AccountRepository;
import com.example.authsecured.ports.SecurityNotifier;
import com.example.authsecured.util.UsernameNormalizer;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class LoginService {

    private final AccountRepository accountRepository;
    private final PasswordService passwordService;
    private final RateLimitService rateLimitService;
    private final SessionService sessionService;
    private final SecurityService securityService;
    private final int maxFailedAttempts;
    private final long lockDurationSeconds;
    private final SecurityNotifier securityNotifier;

    public LoginService(AccountRepository accountRepository, PasswordService passwordService,
                        RateLimitService rateLimitService, SessionService sessionService,
                        SecurityService securityService, int maxFailedAttempts, long lockDurationSeconds) {
        this(accountRepository, passwordService, rateLimitService, sessionService, securityService,
                maxFailedAttempts, lockDurationSeconds, SecurityNotifier.NOOP);
    }

    public LoginService(AccountRepository accountRepository, PasswordService passwordService,
                        RateLimitService rateLimitService, SessionService sessionService,
                        SecurityService securityService, int maxFailedAttempts, long lockDurationSeconds,
                        SecurityNotifier securityNotifier) {
        this.accountRepository = accountRepository;
        this.passwordService = passwordService;
        this.rateLimitService = rateLimitService;
        this.sessionService = sessionService;
        this.securityService = securityService;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockDurationSeconds = lockDurationSeconds;
        this.securityNotifier = securityNotifier == null ? SecurityNotifier.NOOP : securityNotifier;
    }

    public CompletableFuture<AuthResult> login(UUID playerUuid, String username, char[] password, byte[] ipHash) {
        String normalized = UsernameNormalizer.normalize(username);

        return rateLimitService.isIpRateLimited(ipHash)
                .thenCompose(ipLimited -> {
                    if (ipLimited) {
                        securityNotifier.loginFailure(username, "IP_RATE_LIMITED");
                        return securityService.logLoginAttempt(null, ipHash, false, "IP_RATE_LIMITED")
                                .thenApply(ignored -> AuthResult.RATE_LIMITED);
                    }

                    return rateLimitService.isAccountRateLimited(normalized)
                            .thenCompose(accLimited -> {
                                if (accLimited) {
                                    securityNotifier.loginFailure(username, "ACCOUNT_RATE_LIMITED");
                                    return securityService.logLoginAttempt(null, ipHash, false,
                                                    "ACCOUNT_RATE_LIMITED")
                                            .thenApply(ignored -> AuthResult.RATE_LIMITED);
                                }

                                return accountRepository.findByUuid(playerUuid)
                                        .thenCompose(optAccount -> {
                                            if (optAccount.isEmpty()) {
                                                // Timing protection: verify dummy hash if account not found
                                                return passwordService.verifyDummyAsync(password)
                                                        .thenCompose(ignored -> rateLimitService
                                                                .recordFailedLoginAttempt(normalized, ipHash))
                                                        .thenCompose(ignored -> securityService.logLoginAttempt(
                                                                null, ipHash, false, "INVALID_CREDENTIALS"))
                                                        .thenApply(ignored -> {
                                                            securityNotifier.loginFailure(username, "INVALID_CREDENTIALS");
                                                            return AuthResult.INVALID_PASSWORD;
                                                        });
                                            }

                                            Account account = optAccount.get();
                                            Instant now = Instant.now();
                                            return passwordService.verifyPasswordAsync(password, account.getPasswordHash())
                                                    .thenCompose(valid -> {
                                                        if (!valid) {
                                                            securityNotifier.loginFailure(username, "INVALID_PASSWORD");
                                                            return accountRepository.incrementFailedAttemptsAtomic(
                                                                            account.getId(), maxFailedAttempts,
                                                                            lockDurationSeconds, now)
                                                                    .thenCompose(updated -> updated
                                                                            ? CompletableFuture.completedFuture(null)
                                                                            : CompletableFuture.failedFuture(
                                                                                    new IllegalStateException("Account disappeared")))
                                                                    .thenCompose(v -> rateLimitService.recordFailedLoginAttempt(normalized, ipHash))
                                                                    .thenCompose(v -> securityService.logLoginAttempt(account.getId(), ipHash, false, "INVALID_PASSWORD"))
                                                                    .thenApply(v -> AuthResult.INVALID_PASSWORD);
                                                        }

                                                        // Account state is checked only after Argon2 verification so
                                                        // response time does not reveal lock or temporary-password state.
                                                        if (account.isLocked(now)) {
                                                            securityNotifier.loginFailure(username, "ACCOUNT_LOCKED");
                                                            return securityService.logLoginAttempt(account.getId(), ipHash,
                                                                            false, "ACCOUNT_LOCKED")
                                                                    .thenApply(v -> AuthResult.ACCOUNT_LOCKED);
                                                        }
                                                        if (account.isTemporaryPasswordExpired(now)) {
                                                            return securityService.logLoginAttempt(account.getId(), ipHash,
                                                                            false, "TEMPORARY_PASSWORD_EXPIRED")
                                                                    .thenCompose(v -> securityService.logSecurityEvent(
                                                                            account.getId(), SecurityEventType.TEMPORARY_PASSWORD_EXPIRED,
                                                                            ipHash, "{}"))
                                                                    .thenApply(v -> AuthResult.TEMPORARY_PASSWORD_EXPIRED);
                                                        }

                                                        if (account.isPasswordChangeRequired()) {
                                                            return accountRepository.resetFailedAttemptsAtomic(account.getId(), now)
                                                                    .thenCompose(updated -> updated
                                                                            ? rateLimitService.recordSuccessfulLogin(normalized)
                                                                            : CompletableFuture.failedFuture(
                                                                                    new IllegalStateException("Account disappeared")))
                                                                    .thenCompose(v -> securityService.logLoginAttempt(account.getId(), ipHash, true,
                                                                            "TEMPORARY_PASSWORD_ACCEPTED"))
                                                                    .thenApply(v -> AuthResult.PASSWORD_CHANGE_REQUIRED);
                                                        }
                                                        return accountRepository.resetFailedAttemptsAtomic(account.getId(), now)
                                                                .thenCompose(updated -> updated
                                                                        ? rateLimitService.recordSuccessfulLogin(normalized)
                                                                        : CompletableFuture.failedFuture(
                                                                                new IllegalStateException("Account disappeared")))
                                                                .thenCompose(v -> sessionService.createSession(
                                                                        account.getId(), playerUuid, ipHash, "default-server"))
                                                                .thenCompose(session -> securityService.logLoginAttempt(account.getId(), ipHash, true, "SUCCESS"))
                                                                .thenCompose(v -> securityService.logSecurityEvent(account.getId(), SecurityEventType.LOGIN_SUCCESS, ipHash, "{}"))
                                                                .thenApply(v -> AuthResult.SUCCESS);
                                                    });
                                        });
                            });
                }).exceptionally(ex -> AuthResult.INTERNAL_ERROR);
    }
}
