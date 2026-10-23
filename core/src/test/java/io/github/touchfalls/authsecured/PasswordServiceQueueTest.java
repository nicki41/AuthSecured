package io.github.touchfalls.authsecured;

import io.github.touchfalls.authsecured.application.PasswordService;
import io.github.touchfalls.authsecured.ports.PasswordHasher;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordServiceQueueTest {

    @Test
    void floodOfSlowJobsIsRejectedInsteadOfQueuingWithoutLimit() throws InterruptedException {
        CountDownLatch release = new CountDownLatch(1);
        PasswordHasher blockingHasher = new PasswordHasher() {
            @Override public String hash(char[] password) { return await(); }
            @Override public boolean verify(char[] password, String expectedHash) { await() ; return false; }
            @Override public String getDummyHash() { return "dummy"; }
            private String await() {
                try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
                return "done";
            }
        };
        // 2 workers, queue capacity = 2 * 16 = 32; submitting far more than 2 + 32 must
        // reject the overflow instead of growing the queue (and its held passwords) forever.
        PasswordService service = new PasswordService(blockingHasher, 1, 128, 2);
        try {
            List<CompletableFuture<String>> futures = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                futures.add(service.hashPasswordAsync(("pw" + i).toCharArray()));
            }
            boolean anyRejected = futures.stream().anyMatch(CompletableFuture::isCompletedExceptionally);
            assertTrue(anyRejected, "overflow beyond workers + queue capacity must be rejected, not queued forever");
        } finally {
            release.countDown();
            service.shutdown();
        }
    }

    @Test
    void normalLoadIsNotRejected() {
        PasswordHasher hasher = new PasswordHasher() {
            @Override public String hash(char[] password) { return "hash"; }
            @Override public boolean verify(char[] password, String expectedHash) { return true; }
            @Override public String getDummyHash() { return "dummy"; }
        };
        PasswordService service = new PasswordService(hasher, 1, 128, 2);
        try {
            assertFalse(service.hashPasswordAsync("pw".toCharArray()).join().isEmpty());
        } finally {
            service.shutdown();
        }
    }
}
