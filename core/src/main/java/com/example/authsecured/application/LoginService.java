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
                        securityService.logLoginAttempt(null, ipHash, false, "IP_RATE_LIMITED");
                        return CompletableFuture.completedFuture(AuthResult.RATE_LIMITED);
                    }

                    return rateLimitService.isAccountRateLimited(normalized)
                            .thenCompose(accLimited -> {
                                if (accLimited) {
                                    securityNotifier.loginFailure(username, "ACCOUNT_RATE_LIMITED");
                                    securityService.logLoginAttempt(null, ipHash, false, "ACCOUNT_RATE_LIMITED");
                                    return CompletableFuture.completedFuture(AuthResult.RATE_LIMITED);
                                }

                                return accountRepository.findByUuid(playerUuid)
                                        .thenCompose(optAccount -> {
                                            if (optAccount.isEmpty()) {
                                                // Timing protection: verify dummy hash if account not found
                                                return passwordService.verifyDummyAsync(password)
                                                        .thenCompose(ignored -> {
                                                            securityNotifier.loginFailure(username, "INVALID_CREDENTIALS");
                                                            securityService.logLoginAttempt(null, ipHash, false, "INVALID_CREDENTIALS");
                                                            rateLimitService.recordFailedLoginAttempt(normalized, ipHash);
                                                            return CompletableFuture.completedFuture(AuthResult.INVALID_PASSWORD);
                                                        });
                                            }

                                            Account account = optAccount.get();
                                            Instant now = Instant.now();

                                            if (account.isLocked(now)) {
                                                securityNotifier.loginFailure(username, "ACCOUNT_LOCKED");
                                                securityService.logLoginAttempt(account.getId(), ipHash, false, "ACCOUNT_LOCKED");
                                                return CompletableFuture.completedFuture(AuthResult.ACCOUNT_LOCKED);
                                            }

                                            if (account.isTemporaryPasswordExpired(now)) {
                                                securityService.logLoginAttempt(account.getId(), ipHash, false,
                                                        "TEMPORARY_PASSWORD_EXPIRED");
                                                securityService.logSecurityEvent(account.getId(),
                                                        SecurityEventType.TEMPORARY_PASSWORD_EXPIRED, ipHash, "{}");
                                                return CompletableFuture.completedFuture(AuthResult.TEMPORARY_PASSWORD_EXPIRED);
                                            }

                                            return passwordService.verifyPasswordAsync(password, account.getPasswordHash())
                                                    .thenCompose(valid -> {
                                                        if (!valid) {
                                                            securityNotifier.loginFailure(username, "INVALID_PASSWORD");
                                                            account.recordFailedAttempt(maxFailedAttempts, lockDurationSeconds, now);
                                                            return accountRepository.update(account)
                                                                    .thenCompose(v -> rateLimitService.recordFailedLoginAttempt(normalized, ipHash))
                                                                    .thenCompose(v -> securityService.logLoginAttempt(account.getId(), ipHash, false, "INVALID_PASSWORD"))
                                                                    .thenApply(v -> AuthResult.INVALID_PASSWORD);
                                                        }

                                                        account.recordSuccessfulLogin(now);
                                                        if (account.isPasswordChangeRequired()) {
                                                            return accountRepository.update(account)
                                                                    .thenCompose(v -> rateLimitService.recordSuccessfulLogin(normalized))
                                                                    .thenCompose(v -> securityService.logLoginAttempt(account.getId(), ipHash, true,
                                                                            "TEMPORARY_PASSWORD_ACCEPTED"))
                                                                    .thenApply(v -> AuthResult.PASSWORD_CHANGE_REQUIRED);
                                                        }
                                                        return accountRepository.update(account)
                                                                .thenCompose(v -> rateLimitService.recordSuccessfulLogin(normalized))
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
