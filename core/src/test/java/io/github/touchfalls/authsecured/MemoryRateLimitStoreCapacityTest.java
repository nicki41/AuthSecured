package io.github.touchfalls.authsecured;

import io.github.touchfalls.authsecured.infrastructure.redis.MemoryRateLimitStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MemoryRateLimitStoreCapacityTest {
    @Test
    void rejectsNewKeysAtCapacityWithoutEvictingExistingLimits() {
        MemoryRateLimitStore store = new MemoryRateLimitStore(2);
        assertEquals(1, store.incrementAndGet("alice", 600).join());
        assertEquals(1, store.incrementAndGet("bob", 600).join());
        assertEquals(Integer.MAX_VALUE, store.incrementAndGet("attacker", 600).join());
        assertEquals(2, store.incrementAndGet("alice", 600).join());
        store.setLock("a", 600).join();
        store.setLock("b", 600).join();
        assertTrue(store.isLocked("new-key").join());
    }
}
