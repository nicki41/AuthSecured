package com.example.authsecured;

import com.example.authsecured.application.*;
import com.example.authsecured.domain.auth.AuthResult;
import com.example.authsecured.ports.AccountRepository;
import com.example.authsecured.ports.DuplicateAccountException;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RegistrationServiceSecurityTest {
    @Test
    void databaseDuplicateIsReportedAsAlreadyRegistered() {
        AccountRepository accounts = mock(AccountRepository.class);
        PasswordService passwords = mock(PasswordService.class);
        RateLimitService limits = mock(RateLimitService.class);
        SecurityService security = mock(SecurityService.class);
        when(accounts.countAccountsByIpHash(any())).thenReturn(CompletableFuture.completedFuture(0L));
        when(limits.isRegistrationAllowed(any(), eq(0L))).thenReturn(CompletableFuture.completedFuture(true));
        when(accounts.findByUsernameNormalized(anyString()))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(accounts.findByUuid(any())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(passwords.validatePolicy(any())).thenReturn(true);
        when(passwords.hashPasswordAsync(any())).thenReturn(CompletableFuture.completedFuture("hash"));
        when(accounts.save(any())).thenReturn(CompletableFuture.failedFuture(
                new DuplicateAccountException("duplicate", new IllegalStateException())));

        RegistrationService service = new RegistrationService(accounts, passwords, limits, security);
        assertEquals(AuthResult.ALREADY_REGISTERED,
                service.register(UUID.randomUUID(), "Player", "safePassword12".toCharArray(), new byte[]{1}).join());
    }
}
