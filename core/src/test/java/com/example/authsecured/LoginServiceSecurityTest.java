package com.example.authsecured;

import com.example.authsecured.application.*;
import com.example.authsecured.domain.account.Account;
import com.example.authsecured.domain.auth.AuthResult;
import com.example.authsecured.ports.AccountRepository;
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
