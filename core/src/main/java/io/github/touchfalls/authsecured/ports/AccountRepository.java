package io.github.touchfalls.authsecured.ports;

import io.github.touchfalls.authsecured.domain.account.Account;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

public interface AccountRepository {
    CompletableFuture<Optional<Account>> findByUuid(UUID uuid);
    CompletableFuture<Optional<Account>> findByUsernameNormalized(String usernameNormalized);
    /**
     * @param registrationIpHash the hashed IP the account registered from, stored so
     *                           {@link #countAccountsByIpHash} works even with audit
     *                           logging disabled or its retention period elapsed; null
     *                           if the IP was unavailable.
     */
    CompletableFuture<Account> save(Account account, byte[] registrationIpHash);
    CompletableFuture<Boolean> incrementFailedAttemptsAtomic(Long accountId, int maxAttempts,
                                                              long lockDurationSeconds, Instant now);
    CompletableFuture<Boolean> resetFailedAttemptsAtomic(Long accountId, Instant now);
    /** Updates only the password, clearing any pending temporary-password state. Leaves lock state untouched. */
    CompletableFuture<Boolean> updatePasswordHash(Long accountId, String newPasswordHash, Instant now);
    /** Clears lock and failed-attempt state. Leaves the password hash untouched. */
    CompletableFuture<Boolean> unlock(Long accountId, Instant now);
    /** Sets a temporary password and clears lock/failed-attempt state in one write. */
    CompletableFuture<Boolean> setTemporaryPassword(Long accountId, String passwordHash, Instant expiresAt, Instant now);
    CompletableFuture<Boolean> delete(UUID uuid);
    CompletableFuture<Long> countAccountsByIpHash(byte[] ipHash);
}
