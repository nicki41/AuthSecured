package io.github.touchfalls.authsecured.ports;

import io.github.touchfalls.authsecured.domain.account.Account;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

public interface AccountRepository {
    CompletableFuture<Optional<Account>> findByUuid(UUID uuid);
    CompletableFuture<Optional<Account>> findByUsernameNormalized(String usernameNormalized);
    CompletableFuture<Account> save(Account account);
    CompletableFuture<Boolean> update(Account account);
    CompletableFuture<Boolean> incrementFailedAttemptsAtomic(Long accountId, int maxAttempts,
                                                              long lockDurationSeconds, Instant now);
    CompletableFuture<Boolean> resetFailedAttemptsAtomic(Long accountId, Instant now);
    CompletableFuture<Boolean> delete(UUID uuid);
    CompletableFuture<Long> countAccountsByIpHash(byte[] ipHash);
}
