package io.github.touchfalls.authsecured.fabric;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the shared decision methods that every pre-login Fabric mixin
 * ({@code ServerGamePacketListenerImplMixin}, {@code CommandsMixin},
 * {@code ItemEntityMixin}, {@code FoodDataMixin}) calls before cancelling a
 * packet. Mixins apply bytecode transforms and need a running Minecraft
 * server to exercise directly; these methods are the testable decision logic
 * behind every one of them, including the fail-closed state before
 * {@code DependencyContainer} has finished initializing.
 */
class AuthSecuredFabricModDecisionTest {

    @Test
    void uninitializedModFailsClosedForRestrictedPlayers() {
        AuthSecuredFabricMod mod = new AuthSecuredFabricMod();
        UUID uuid = UUID.randomUUID();
        mod.getRestrictionAdapter().setPlayerUnauthenticated(uuid, null);

        // No container yet (server still starting): every restriction falls back to its
        // caller-supplied default, and commands are blocked entirely rather than allowed.
        assertTrue(mod.isRestricted(uuid, "restrictions.block-chat", true));
        assertFalse(mod.isRestricted(uuid, "restrictions.allow-flight", false));
        assertTrue(mod.shouldBlockFlight(uuid));
        assertTrue(mod.isCommandBlocked(uuid, "/login"));
        assertTrue(mod.isCommandBlocked(uuid, "/op"));
    }

    @Test
    void authenticatedPlayerBypassesEveryRestrictionEvenUninitialized() {
        AuthSecuredFabricMod mod = new AuthSecuredFabricMod();
        UUID uuid = UUID.randomUUID();
        mod.getRestrictionAdapter().setPlayerAuthenticated(uuid);

        assertFalse(mod.isRestricted(uuid, "restrictions.block-chat", true));
        assertFalse(mod.shouldBlockFlight(uuid));
        assertFalse(mod.isCommandBlocked(uuid, "/anything"));
    }
}
