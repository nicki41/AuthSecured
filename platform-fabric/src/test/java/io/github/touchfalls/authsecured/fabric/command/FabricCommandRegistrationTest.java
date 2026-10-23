package io.github.touchfalls.authsecured.fabric.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class FabricCommandRegistrationTest {
    @Test
    void createsClipboardComponentForCurrentMinecraftApi() {
        assertNotNull(FabricClipboardText.create("Temporary password: ", "SafePassword23"));
    }
}
