package io.github.touchfalls.authsecured;

import io.github.touchfalls.authsecured.domain.account.Account;
import io.github.touchfalls.authsecured.domain.account.AccountStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AccountLockStateTest {
    @Test
    void handlesEveryStatusAndLockExpiryCombination() {
        Instant now = Instant.now();
        for (AccountStatus status : AccountStatus.values()) {
            for (Instant until : new Instant[]{null, now.minusSeconds(1), now.plusSeconds(1)}) {
                Account account = new Account(1L, UUID.randomUUID(), "Player", "player",
                        "hash", status, 0, until, now, now, null, now);
                boolean expected = status == AccountStatus.SUSPENDED
                        || (status == AccountStatus.LOCKED && until == null)
                        || (until != null && now.isBefore(until));
                assertEquals(expected, account.isLocked(now), status + " / " + until);
            }
        }
    }
}
