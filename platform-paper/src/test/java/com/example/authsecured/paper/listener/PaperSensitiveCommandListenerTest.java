package com.example.authsecured.paper.listener;

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
}
