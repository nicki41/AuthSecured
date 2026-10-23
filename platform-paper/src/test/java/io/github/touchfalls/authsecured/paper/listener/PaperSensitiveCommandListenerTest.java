package io.github.touchfalls.authsecured.paper.listener;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PaperSensitiveCommandListenerTest {

    @Test
    void recognizesNamespacedSensitiveCommandWithoutRetainingPrefix() {
        PaperSensitiveCommandListener.ParsedCommand parsed =
                PaperSensitiveCommandListener.parse("/authsecured:login VerySecret");

        assertNotNull(parsed);
        assertEquals("login", parsed.label());
        assertArrayEquals(new String[]{"VerySecret"}, parsed.arguments());
    }

    @Test
    void ignoresMessagesThatAreNotCommands() {
        assertNull(PaperSensitiveCommandListener.parse("login VerySecret"));
    }

    @Test
    void preLoginAllowListAppliesToPasswordCommands() {
        java.util.List<String> allowed = java.util.List.of("/login", "/L", "/register");
        assertTrue(PaperSensitiveCommandListener.isAllowed("login", allowed));
        assertTrue(PaperSensitiveCommandListener.isAllowed("l", allowed));
        assertFalse(PaperSensitiveCommandListener.isAllowed("changepassword", allowed));
        assertFalse(PaperSensitiveCommandListener.isAllowed("cp", allowed));
    }
}
