package io.github.touchfalls.authsecured;

import io.github.touchfalls.authsecured.application.*;
import io.github.touchfalls.authsecured.domain.account.Account;
import io.github.touchfalls.authsecured.domain.auth.AuthResult;
import io.github.touchfalls.authsecured.ports.AccountRepository;
import io.github.touchfalls.authsecured.ports.PlatformLogger;
import io.github.touchfalls.authsecured.ports.SecurityNotifier;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LoginServiceSecurityTest {

    @Test
    void unknownIpIsRejectedBeforeRateLimitAuditOrSessionCreation() {
        RateLimitService limits = mock(RateLimitService.class);
        SessionService sessions = mock(SessionService.class);
        SecurityService security = mock(SecurityService.class);
        PlatformLogger logger = mock(PlatformLogger.class);
        LoginService service = new LoginService(mock(AccountRepository.class), mock(PasswordService.class),
                limits, sessions, security, 5, 900, SecurityNotifier.NOOP, logger);

        assertEquals(AuthResult.INTERNAL_ERROR,
                service.login(UUID.randomUUID(), "Player", "secret".toCharArray(), null).join());
        verifyNoInteractions(limits, sessions, security);
        verify(logger).severe(contains("client IP is unavailable"));
    }

    @Test
    void logsInternalFailureWithoutExceptionMessage() {
        AccountRepository accounts = mock(AccountRepository.class);
        PasswordService passwords = mock(PasswordService.class);
        RateLimitService limits = mock(RateLimitService.class);
        PlatformLogger logger = mock(PlatformLogger.class);
        when(limits.isIpRateLimited(any())).thenReturn(CompletableFuture.failedFuture(
                new IllegalStateException("password=secret ip=192.0.2.1")));
        LoginService service = new LoginService(accounts, passwords, limits,
                mock(SessionService.class), mock(SecurityService.class), 5, 900,
                SecurityNotifier.NOOP, logger);
        assertEquals(AuthResult.INTERNAL_ERROR,
                service.login(UUID.randomUUID(), "Player", "secret".toCharArray(), new byte[]{1}).join());
        org.mockito.ArgumentCaptor<String> message = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(logger).severe(message.capture());
        org.junit.jupiter.api.Assertions.assertFalse(message.getValue().contains("password=secret"));
        org.junit.jupiter.api.Assertions.assertFalse(message.getValue().contains("192.0.2.1"));
        org.junit.jupiter.api.Assertions.assertTrue(message.getValue().contains("IllegalStateException"));
    }

    @Test
    void wrongPasswordForLockedAccountDoesNotRevealLockState() {
        AccountRepository accounts = mock(AccountRepository.class);
        PasswordService passwords = mock(PasswordService.class);
        RateLimitService limits = mock(RateLimitService.class);
        SecurityService security = mock(SecurityService.class);
        UUID uuid = UUID.randomUUID();
        Account account = Account.createNew(uuid, "Player", "player", "hash");
        account.recordFailedAttempt(1, 900, Instant.now());

        when(limits.isIpRateLimited(any())).thenReturn(CompletableFuture.completedFuture(false));
        when(limits.isAccountRateLimited(anyString())).thenReturn(CompletableFuture.completedFuture(false));
        when(limits.recordFailedLoginAttempt(anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
        when(security.logLoginAttempt(any(), any(), anyBoolean(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(accounts.findByUuid(uuid)).thenReturn(CompletableFuture.completedFuture(Optional.of(account)));
        when(accounts.incrementFailedAttemptsAtomic(eq(account.getId()), eq(5), eq(900L), any()))
                .thenReturn(CompletableFuture.completedFuture(true));
        when(passwords.verifyPasswordAsync(any(), eq("hash")))
                .thenReturn(CompletableFuture.completedFuture(false));

        LoginService service = new LoginService(accounts, passwords, limits,
                mock(SessionService.class), security, 5, 900);

        assertEquals(AuthResult.INVALID_PASSWORD,
                service.login(uuid, "Player", "wrong".toCharArray(), new byte[]{1}).join());
        verify(passwords).verifyPasswordAsync(any(), eq("hash"));
        verify(accounts).incrementFailedAttemptsAtomic(eq(account.getId()), eq(5), eq(900L), any());
    }
}
