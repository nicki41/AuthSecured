package io.github.touchfalls.authsecured.paper.logging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperSensitiveCommandLogFilterTest {

    @Test
    void blocksEveryPasswordBearingCommandAndAlias() {
        for (String command : new String[]{
                "/login secret", "/l secret", "/register secret secret",
                "/reg secret secret", "/changepassword old new", "/cp old new",
                "/authsecured:login secret", "/AUTHSECURED:REGISTER secret secret"
        }) {
            assertTrue(SensitiveCommandLogMatcher.isSensitiveCommand(command), command);
        }
    }

    @Test
    void allowsUnrelatedCommandsAndMessages() {
        assertFalse(SensitiveCommandLogMatcher.isSensitiveCommand("/help"));
        assertTrue(SensitiveCommandLogMatcher.isSensitiveCommand(" /login secret"));
        assertFalse(SensitiveCommandLogMatcher.isSensitiveCommand("/loginhistory"));
    }
}
