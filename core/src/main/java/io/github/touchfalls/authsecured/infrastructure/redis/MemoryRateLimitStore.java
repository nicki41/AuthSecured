package io.github.touchfalls.authsecured.infrastructure.redis;

import io.github.touchfalls.authsecured.ports.RateLimitStore;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

public class MemoryRateLimitStore implements RateLimitStore {

    private static final int DEFAULT_MAX_ENTRIES = 10_000;
    private static final long CLEANUP_INTERVAL_SECONDS = 60;

    private record Counter(int count, long expireAtEpochSecond) {}

    private final Map<String, Counter> counters = new ConcurrentHashMap<>();
    private final Map<String, Long> locks = new ConcurrentHashMap<>();
    private final AtomicLong nextCleanupEpochSecond = new AtomicLong();
    private final int maxEntries;

    public MemoryRateLimitStore() { this(DEFAULT_MAX_ENTRIES); }

    public MemoryRateLimitStore(int maxEntries) {
        if (maxEntries < 1) throw new IllegalArgumentException("maxEntries must be positive");
        this.maxEntries = maxEntries;
    }

    @Override
    public synchronized CompletableFuture<Integer> incrementAndGet(String key, long windowSeconds) {
        long now = Instant.now().getEpochSecond();
        cleanExpired(now);
        if (!counters.containsKey(key) && counters.size() >= maxEntries) {
            return CompletableFuture.completedFuture(Integer.MAX_VALUE);
        }
        Counter updated = counters.compute(key, (k, existing) ->
                existing == null || existing.expireAtEpochSecond() < now
                        ? new Counter(1, now + windowSeconds)
                        : new Counter(existing.count() == Integer.MAX_VALUE
                        ? Integer.MAX_VALUE : existing.count() + 1, existing.expireAtEpochSecond()));
        return CompletableFuture.completedFuture(updated.count());
    }

    @Override
    public CompletableFuture<Boolean> isLocked(String lockKey) {
        long now = Instant.now().getEpochSecond();
        Long expire = locks.get(lockKey);
        if (expire == null) return CompletableFuture.completedFuture(locks.size() >= maxEntries);
        if (expire < now) {
            locks.remove(lockKey, expire);
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public synchronized CompletableFuture<Void> setLock(String lockKey, long lockSeconds) {
        long expireAt = Instant.now().getEpochSecond() + lockSeconds;
        cleanExpired(Instant.now().getEpochSecond());
        if (locks.containsKey(lockKey) || locks.size() < maxEntries) locks.put(lockKey, expireAt);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> reset(String key) {
        counters.remove(key);
        locks.remove(key);
        return CompletableFuture.completedFuture(null);
    }

    private void cleanExpired(long now) {
        if (now < nextCleanupEpochSecond.get()) return;
        nextCleanupEpochSecond.set(now + CLEANUP_INTERVAL_SECONDS);
        counters.entrySet().removeIf(entry -> entry.getValue().expireAtEpochSecond() < now);
        locks.entrySet().removeIf(entry -> entry.getValue() < now);
    }
}
