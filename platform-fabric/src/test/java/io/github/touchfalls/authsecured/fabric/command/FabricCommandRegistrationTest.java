package io.github.touchfalls.authsecured.fabric.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class FabricCommandRegistrationTest {
    @Test
    void createsClipboardComponentForCurrentMinecraftApi() {
        assertNotNull(FabricClipboardText.create("Temporary password: ", "SafePassword23"));
    }

    @Test
    void firstTokenMatchesPapersArgsZeroParsing() {
        assertEquals("pass", FabricCommandRegistration.firstToken("pass"));
        assertEquals("pass", FabricCommandRegistration.firstToken("pass word"));
        assertEquals("p@ss!word", FabricCommandRegistration.firstToken("p@ss!word"));
    }

    @Test
    void splitTokensMatchesPapersArgsZeroAndOneParsing() {
        assertArrayEquals(new String[]{"one", "two"}, FabricCommandRegistration.splitTokens("one two"));
        // A third token is silently ignored, exactly like Paper ignores args[2+].
        assertArrayEquals(new String[]{"one", "two"}, FabricCommandRegistration.splitTokens("one two three"));
        assertArrayEquals(new String[]{"p@ss!", "w0rd#"}, FabricCommandRegistration.splitTokens("p@ss! w0rd#"));
        assertNull(FabricCommandRegistration.splitTokens("onlyOneToken"));
    }
}
