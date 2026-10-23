package io.github.touchfalls.authsecured.bootstrap;

import io.github.touchfalls.authsecured.application.SecurityService;
import io.github.touchfalls.authsecured.application.SessionService;
import io.github.touchfalls.authsecured.ports.PlatformLogger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Starts database maintenance from platform ticks without doing cleanup on the server thread. */
public final class MaintenanceService implements AutoCloseable {
    private final SessionService sessions;
    private final SecurityService security;
    private final PlatformLogger logger;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "authsecured-maintenance");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicLong nextRunMillis = new AtomicLong();
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile int intervalSeconds;
    private volatile int retentionDays;

    public MaintenanceService(SessionService sessions, SecurityService security,
                              PlatformLogger logger, int intervalSeconds, int retentionDays) {
        this.sessions = sessions;
        this.security = security;
        this.logger = logger;
        configure(intervalSeconds, retentionDays);
    }

    public void configure(int intervalSeconds, int retentionDays) {
        if (intervalSeconds < 60 || intervalSeconds > 86_400 || retentionDays < 1 || retentionDays > 3_650) {
            throw new IllegalArgumentException("Invalid maintenance interval or audit retention");
        }
        this.intervalSeconds = intervalSeconds;
        this.retentionDays = retentionDays;
    }

    public CompletableFuture<Void> runIfDue(long nowMillis) {
        if (closed.get() || nowMillis < nextRunMillis.get() || !running.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(null);
        }
        nextRunMillis.set(nowMillis + intervalSeconds * 1_000L);
        int days = retentionDays;
        return CompletableFuture.runAsync(() -> {
            sessions.cleanupExpiredSessions().join();
            security.purgeAuditLogs(days, days).join();
        }, executor).whenComplete((ignored, error) -> {
            running.set(false);
            if (error != null) {
                logger.severe("AuthSecured maintenance failed. Database details are hidden.");
            }
        });
    }

    @Override
    public void close() {
        closed.set(true);
        executor.shutdownNow();
    }
}
