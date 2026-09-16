package io.github.touchfalls.authsecured;

import io.github.touchfalls.authsecured.application.*;
import io.github.touchfalls.authsecured.domain.account.Account;
import io.github.touchfalls.authsecured.domain.auth.AuthResult;
import io.github.touchfalls.authsecured.ports.AccountRepository;
import io.github.touchfalls.authsecured.ports.PlatformLogger;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceSecurityTest {

    @Test
    void passwordChangeFailureLogsOnlyCauseType() {
        AccountRepository accounts = mock(AccountRepository.class);
        PasswordService passwords = mock(PasswordService.class);
        PlatformLogger logger = mock(PlatformLogger.class);
        when(passwords.validatePolicy(any())).thenReturn(true);
        when(accounts.findByUuid(any())).thenReturn(CompletableFuture.failedFuture(
                new IllegalStateException("password=secret")));
        AuthService service = new AuthService(mock(RegistrationService.class), mock(LoginService.class),
                passwords, mock(SessionService.class), mock(SecurityService.class), accounts, logger);
        assertEquals(AuthResult.INTERNAL_ERROR, service.changePassword(UUID.randomUUID(),
                "oldPassword12".toCharArray(), "newPassword12".toCharArray(), new byte[]{1}).join());
        org.mockito.ArgumentCaptor<String> message = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(logger).severe(message.capture());
        org.junit.jupiter.api.Assertions.assertFalse(message.getValue().contains("password=secret"));
    }
    @Test
    void rejectsChangingPasswordToTheCurrentPassword() {
        AccountRepository accounts = mock(AccountRepository.class);
        PasswordService passwords = mock(PasswordService.class);
        UUID uuid = UUID.randomUUID();
        Account account = Account.createNew(uuid, "Player", "player", "hash");
        when(passwords.validatePolicy(any())).thenReturn(true);
        when(passwords.verifyPasswordAsync(any(), eq("hash")))
                .thenReturn(CompletableFuture.completedFuture(true));
        when(accounts.findByUuid(uuid)).thenReturn(CompletableFuture.completedFuture(Optional.of(account)));
        AuthService service = new AuthService(mock(RegistrationService.class), mock(LoginService.class),
                passwords, mock(SessionService.class), mock(SecurityService.class), accounts);
        char[] same = "samePassword12".toCharArray();

        assertEquals(AuthResult.PASSWORD_UNCHANGED,
                service.changePassword(uuid, same, same.clone(), new byte[]{1}).join());
        verify(passwords, never()).hashPasswordAsync(any());
        verify(accounts, never()).update(any());
    }
}
