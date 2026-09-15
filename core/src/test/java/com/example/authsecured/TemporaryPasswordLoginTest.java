package com.example.authsecured;

import com.example.authsecured.application.*;
import com.example.authsecured.domain.account.Account;
import com.example.authsecured.domain.auth.AuthResult;
import com.example.authsecured.domain.security.SecurityEventType;
import com.example.authsecured.ports.AccountRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TemporaryPasswordLoginTest {

    @Test
    void acceptedTemporaryPasswordDoesNotCreateSession() {
        AccountRepository accounts = mock(AccountRepository.class);
        PasswordService passwords = mock(PasswordService.class);
        RateLimitService limits = permissiveLimits();
        SessionService sessions = mock(SessionService.class);
        SecurityService security = auditSink();
        UUID uuid = UUID.randomUUID();
        Account account = Account.createNew(uuid, "Player", "player", "temporary-hash");
        account.setTemporaryPassword("temporary-hash", Instant.now().plusSeconds(300));
        when(accounts.findByUuid(uuid)).thenReturn(CompletableFuture.completedFuture(Optional.of(account)));
        when(accounts.update(account)).thenReturn(CompletableFuture.completedFuture(true));
        when(passwords.verifyPasswordAsync(any(char[].class), eq("temporary-hash")))
                .thenReturn(CompletableFuture.completedFuture(true));

        LoginService service = new LoginService(
                accounts, passwords, limits, sessions, security, 5, 900);

        assertEquals(AuthResult.PASSWORD_CHANGE_REQUIRED,
                service.login(uuid, "Player", "temporary".toCharArray(), new byte[]{1}).join());
        verify(sessions, never()).createSession(anyLong(), any(), any(), anyString());
    }

    @Test
    void expiredTemporaryPasswordIsRejectedBeforeHashVerification() {
        AccountRepository accounts = mock(AccountRepository.class);
        PasswordService passwords = mock(PasswordService.class);
        UUID uuid = UUID.randomUUID();
        Account account = Account.createNew(uuid, "Player", "player", "temporary-hash");
        account.setTemporaryPassword("temporary-hash", Instant.now().minusSeconds(1));
        when(accounts.findByUuid(uuid)).thenReturn(CompletableFuture.completedFuture(Optional.of(account)));

        LoginService service = new LoginService(accounts, passwords, permissiveLimits(),
                mock(SessionService.class), auditSink(), 5, 900);

        assertEquals(AuthResult.TEMPORARY_PASSWORD_EXPIRED,
                service.login(uuid, "Player", "temporary".toCharArray(), new byte[]{1}).join());
        verify(passwords, never()).verifyPasswordAsync(any(), anyString());
    }

    private static RateLimitService permissiveLimits() {
        RateLimitService limits = mock(RateLimitService.class);
        when(limits.isIpRateLimited(any())).thenReturn(CompletableFuture.completedFuture(false));
        when(limits.isAccountRateLimited(anyString())).thenReturn(CompletableFuture.completedFuture(false));
        when(limits.recordSuccessfulLogin(anyString())).thenReturn(CompletableFuture.completedFuture(null));
        return limits;
    }

    private static SecurityService auditSink() {
        SecurityService security = mock(SecurityService.class);
        when(security.logLoginAttempt(any(), any(), anyBoolean(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(security.logSecurityEvent(any(), any(SecurityEventType.class), any(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        return security;
    }
}
