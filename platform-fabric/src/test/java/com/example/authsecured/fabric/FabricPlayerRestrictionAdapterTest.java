package com.example.authsecured.fabric;

import com.example.authsecured.domain.auth.AuthState;
import com.example.authsecured.fabric.adapter.FabricPlayerRestrictionAdapter;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricPlayerRestrictionAdapterTest {

    @Test
    void tracksAuthenticationLifecycle() {
        FabricPlayerRestrictionAdapter adapter = new FabricPlayerRestrictionAdapter();
        UUID uuid = UUID.randomUUID();

        assertFalse(adapter.isAuthenticated(uuid));
        adapter.setPlayerUnauthenticated(uuid, null);
        assertEquals(AuthState.AUTH_REQUIRED, adapter.getAuthState(uuid));
        adapter.setPlayerAuthenticated(uuid);
        assertTrue(adapter.isAuthenticated(uuid));
        adapter.removePlayer(uuid);
        assertFalse(adapter.isAuthenticated(uuid));
    }
}
