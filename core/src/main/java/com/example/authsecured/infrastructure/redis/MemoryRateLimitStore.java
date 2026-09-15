package com.example.authsecured.infrastructure.redis;

import com.example.authsecured.ports.RateLimitStore;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

public class MemoryRateLimitStore implements RateLimitStore {

    private record Counter(int count, long expireAtEpochSecond) {}

    private final Map<String, Counter> counters = new ConcurrentHashMap<>();
    private final Map<String, Long> locks = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<Integer> incrementAndGet(String key, long windowSeconds) {
        long now = Instant.now().getEpochSecond();
        cleanExpired(now);
        Counter updated = counters.compute(key, (k, existing) ->
                existing == null || existing.expireAtEpochSecond() < now
                        ? new Counter(1, now + windowSeconds)
                        : new Counter(existing.count() + 1, existing.expireAtEpochSecond()));
        return CompletableFuture.completedFuture(updated.count());
    }

    @Override
    public CompletableFuture<Boolean> isLocked(String lockKey) {
        long now = Instant.now().getEpochSecond();
        Long expire = locks.get(lockKey);
        if (expire == null) return CompletableFuture.completedFuture(false);
        if (expire < now) {
            locks.remove(lockKey, expire);
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public CompletableFuture<Void> setLock(String lockKey, long lockSeconds) {
        long expireAt = Instant.now().getEpochSecond() + lockSeconds;
        locks.put(lockKey, expireAt);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> reset(String key) {
        counters.remove(key);
        locks.remove(key);
        return CompletableFuture.completedFuture(null);
    }

    private void cleanExpired(long now) {
        if (counters.size() > 100) {
            counters.entrySet().removeIf(entry -> entry.getValue().expireAtEpochSecond() < now);
        }
        if (locks.size() > 100) {
            locks.entrySet().removeIf(entry -> entry.getValue() < now);
        }
    }
}
