package io.github.touchfalls.authsecured.bootstrap;

import io.github.touchfalls.authsecured.application.SecurityService;
import io.github.touchfalls.authsecured.application.SessionService;
import io.github.touchfalls.authsecured.ports.PlatformLogger;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MaintenanceServiceTest {
    @Test
    void runsBothCleanupsOffCallerThreadAndStopsAfterClose() {
        SessionService sessions = mock(SessionService.class);
        SecurityService security = mock(SecurityService.class);
        PlatformLogger logger = mock(PlatformLogger.class);
        AtomicReference<Thread> cleanupThread = new AtomicReference<>();
        when(sessions.cleanupExpiredSessions()).thenAnswer(invocation -> {
            cleanupThread.set(Thread.currentThread());
            return CompletableFuture.completedFuture(null);
        });
        when(security.purgeAuditLogs(30, 30)).thenReturn(CompletableFuture.completedFuture(null));

        MaintenanceService service = new MaintenanceService(sessions, security, logger, 60, 30);
        service.runIfDue(1_000L).join();
        assertNotSame(Thread.currentThread(), cleanupThread.get());
        service.runIfDue(2_000L).join();
        verify(sessions, times(1)).cleanupExpiredSessions();
        verify(security, times(1)).purgeAuditLogs(30, 30);
        service.close();
        service.runIfDue(100_000L).join();
        verify(sessions, times(1)).cleanupExpiredSessions();
    }
}
