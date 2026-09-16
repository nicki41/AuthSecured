package io.github.touchfalls.authsecured.ports;

import io.github.touchfalls.authsecured.domain.auth.AuthState;
import java.util.UUID;

/**
 * Port for managing pre-authentication player restrictions.
 * Implemented by each platform adapter.
 */
public interface PlayerRestrictionAdapter {
    void setPlayerAuthenticated(UUID playerUuid);
    void setPlayerUnauthenticated(UUID playerUuid, Object location);
    void removePlayer(UUID playerUuid);
    boolean isAuthenticated(UUID playerUuid);
    AuthState getAuthState(UUID playerUuid);
}
