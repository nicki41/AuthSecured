package io.github.touchfalls.authsecured;

import io.github.touchfalls.authsecured.infrastructure.redis.MemoryRateLimitStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MemoryRateLimitStoreCapacityTest {

    @Test
    void newKeysAtCapacityAreCountedNormallyInsteadOfLockedImmediately() {
        MemoryRateLimitStore store = new MemoryRateLimitStore(2);
        assertEquals(1, store.incrementAndGet("alice", 600).join());
        assertEquals(1, store.incrementAndGet("bob", 60).join());
        assertEquals(1, store.incrementAndGet("newcomer", 600).join());
        // The soonest-expiring counter ("bob") was evicted; "alice" keeps counting.
        assertEquals(2, store.incrementAndGet("alice", 600).join());
    }

    @Test
    void floodingWithFakeKeysCannotWipeAVictimWithRealFailures() {
        MemoryRateLimitStore store = new MemoryRateLimitStore(3);
        // "victim" already failed twice; its count (2) is higher than any single new key (1).
        store.incrementAndGet("victim", 600).join();
        store.incrementAndGet("victim", 600).join();
        store.incrementAndGet("filler", 600).join();

        // An attacker floods the table with one-shot fake keys; they evict each other, not "victim".
        for (int i = 0; i < 50; i++) {
            store.incrementAndGet("attacker-" + i, 600).join();
        }

        assertEquals(3, store.incrementAndGet("victim", 600).join());
    }

    @Test
    void fullLockTableDoesNotLockUnrelatedKeys() {
        MemoryRateLimitStore store = new MemoryRateLimitStore(2);
        store.setLock("a", 600).join();
        store.setLock("b", 600).join();

        assertFalse(store.isLocked("legitimate-player").join());
        assertTrue(store.isLocked("a").join());
    }

    @Test
    void newLockAtCapacityEvictsTheSoonestExpiringLock() {
        MemoryRateLimitStore store = new MemoryRateLimitStore(2);
        store.setLock("short", 10).join();
        store.setLock("long", 600).join();
        store.setLock("fresh", 600).join();

        assertFalse(store.isLocked("short").join());
        assertTrue(store.isLocked("long").join());
        assertTrue(store.isLocked("fresh").join());
    }
}
