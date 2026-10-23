package io.github.touchfalls.authsecured.infrastructure.redis;

import io.github.touchfalls.authsecured.ports.RateLimitStore;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Bounded in-memory rate-limit store.
 *
 * <p>At capacity the counter with the lowest hit count is evicted (ties broken by
 * soonest expiry), so a flood of single-shot fake keys is evicted before a key
 * that has accumulated real failures. Overflow must never turn into "everything
 * is locked": an attacker who can create many keys (for example by rotating
 * offline-mode names or IPv6 addresses) would otherwise lock every player out.</p>
 */
public class MemoryRateLimitStore implements RateLimitStore {

    private static final int DEFAULT_MAX_ENTRIES = 10_000;
    private static final long CLEANUP_INTERVAL_SECONDS = 60;

    private record Counter(int count, long expireAtEpochSecond) {}

    private final Map<String, Counter> counters = new HashMap<>();
    private final Map<String, Long> locks = new HashMap<>();
    private final int maxEntries;
    private long nextCleanupEpochSecond;

    public MemoryRateLimitStore() { this(DEFAULT_MAX_ENTRIES); }

    public MemoryRateLimitStore(int maxEntries) {
        if (maxEntries < 1) throw new IllegalArgumentException("maxEntries must be positive");
        this.maxEntries = maxEntries;
    }

    @Override
    public synchronized CompletableFuture<Integer> incrementAndGet(String key, long windowSeconds) {
        long now = Instant.now().getEpochSecond();
        cleanExpired(now);
        Counter existing = counters.get(key);
        Counter updated;
        if (existing == null || existing.expireAtEpochSecond() < now) {
            if (existing == null && counters.size() >= maxEntries) {
                evictSoonestCounter();
            }
            updated = new Counter(1, now + windowSeconds);
        } else {
            updated = new Counter(existing.count() == Integer.MAX_VALUE
                    ? Integer.MAX_VALUE : existing.count() + 1, existing.expireAtEpochSecond());
        }
        counters.put(key, updated);
        return CompletableFuture.completedFuture(updated.count());
    }

    @Override
    public synchronized CompletableFuture<Boolean> isLocked(String lockKey) {
        long now = Instant.now().getEpochSecond();
        cleanExpired(now);
        Long expire = locks.get(lockKey);
        if (expire == null) return CompletableFuture.completedFuture(false);
        if (expire < now) {
            locks.remove(lockKey);
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public synchronized CompletableFuture<Void> setLock(String lockKey, long lockSeconds) {
        long now = Instant.now().getEpochSecond();
        cleanExpired(now);
        if (!locks.containsKey(lockKey) && locks.size() >= maxEntries) {
            evictSoonestLock();
        }
        locks.put(lockKey, now + lockSeconds);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public synchronized CompletableFuture<Void> reset(String key) {
        counters.remove(key);
        locks.remove(key);
        return CompletableFuture.completedFuture(null);
    }

    private void cleanExpired(long now) {
        if (now < nextCleanupEpochSecond) return;
        nextCleanupEpochSecond = now + CLEANUP_INTERVAL_SECONDS;
        counters.entrySet().removeIf(entry -> entry.getValue().expireAtEpochSecond() < now);
        locks.entrySet().removeIf(entry -> entry.getValue() < now);
    }

    // shortcut: a count tie can still evict a key with one real failure; upgrade
    // to per-IP buckets if that becomes an exploitable gap at the configured capacity.
    private void evictSoonestCounter() {
        String victim = null;
        int lowestCount = Integer.MAX_VALUE;
        long soonest = Long.MAX_VALUE;
        for (Map.Entry<String, Counter> entry : counters.entrySet()) {
            Counter value = entry.getValue();
            if (value.count() < lowestCount
                    || (value.count() == lowestCount && value.expireAtEpochSecond() < soonest)) {
                lowestCount = value.count();
                soonest = value.expireAtEpochSecond();
                victim = entry.getKey();
            }
        }
        if (victim != null) counters.remove(victim);
    }

    private void evictSoonestLock() {
        String victim = null;
        long soonest = Long.MAX_VALUE;
        for (Map.Entry<String, Long> entry : locks.entrySet()) {
            if (entry.getValue() < soonest) {
                soonest = entry.getValue();
                victim = entry.getKey();
            }
        }
        if (victim != null) locks.remove(victim);
    }
}
