package com.example.authsecured;

import com.example.authsecured.application.*;
import com.example.authsecured.domain.account.Account;
import com.example.authsecured.domain.auth.AuthResult;
import com.example.authsecured.ports.AccountRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceSecurityTest {
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
