package com.example.authsecured;

import com.example.authsecured.domain.account.Account;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TemporaryPasswordAccountTest {

    @Test
    void permanentPasswordChangeClearsTemporaryState() {
        Account account = Account.createNew(UUID.randomUUID(), "Player", "player", "old-hash");
        Instant expiry = Instant.now().plusSeconds(300);

        account.setTemporaryPassword("temporary-hash", expiry);

        assertTrue(account.isPasswordChangeRequired());
        assertFalse(account.isTemporaryPasswordExpired(Instant.now()));
        assertTrue(account.isTemporaryPasswordExpired(expiry));

        account.updatePassword("new-hash", Instant.now());
        assertFalse(account.isPasswordChangeRequired());
        assertNull(account.getTemporaryPasswordExpiresAt());
    }
}
