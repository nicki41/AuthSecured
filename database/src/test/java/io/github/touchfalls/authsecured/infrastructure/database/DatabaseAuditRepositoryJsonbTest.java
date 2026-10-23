package io.github.touchfalls.authsecured.infrastructure.database;

import io.github.touchfalls.authsecured.domain.security.SecurityEvent;
import io.github.touchfalls.authsecured.domain.security.SecurityEventType;
import io.github.touchfalls.authsecured.ports.PlatformLogger;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DatabaseAuditRepositoryJsonbTest {

    @Test
    void postgresMetadataIsBoundAsUntypedJsonInsteadOfVarchar() throws Exception {
        PreparedStatement statement = recordEvent(true);
        verify(statement).setObject(4, "{}", Types.OTHER);
        verify(statement, never()).setString(eq(4), anyString());
    }

    @Test
    void sqliteMetadataStaysText() throws Exception {
        PreparedStatement statement = recordEvent(false);
        verify(statement).setString(4, "{}");
    }

    private static PreparedStatement recordEvent(boolean postgres) throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            new DatabaseAuditRepository(dataSource, executor, postgres, mock(PlatformLogger.class))
                    .recordSecurityEvent(new SecurityEvent(null, 1L, SecurityEventType.LOGIN_SUCCESS,
                            new byte[]{1}, "{}", Instant.now()))
                    .join();
        } finally {
            executor.shutdownNow();
        }
        return statement;
    }
}
