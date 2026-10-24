package io.github.touchfalls.authsecured.paper.listener;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerCommandListenerTest {

    @Test
    void commandListBeforeLoginKeepsOnlyAllowListedLabels() {
        List<String> labels = new ArrayList<>(List.of(
                "login", "l", "register", "Reg", "tp", "minecraft:tp", "authsecured:login", "msg"));

        PlayerCommandListener.retainAllowedCommands(labels, Set.of("/login", "/l", "/register", "/reg"));

        assertEquals(List.of("login", "l", "register", "Reg"), labels);
    }
}
