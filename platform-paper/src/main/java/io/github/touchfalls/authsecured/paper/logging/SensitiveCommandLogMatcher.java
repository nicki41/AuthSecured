package io.github.touchfalls.authsecured.paper.logging;

import java.util.Locale;
import io.github.touchfalls.authsecured.util.SecretRedactor;

final class SensitiveCommandLogMatcher {

    private SensitiveCommandLogMatcher() {
    }

    static boolean isSensitiveCommand(String rawCommand) {
        if (rawCommand == null) return false;
        String command = rawCommand.stripLeading();
        if (command.startsWith("/")) command = command.substring(1);
        int end = 0;
        while (end < command.length() && !Character.isWhitespace(command.charAt(end))) end++;
        if (end == 0) return false;
        String label = command.substring(0, end).toLowerCase(Locale.ROOT);
        int namespace = label.lastIndexOf(':');
        if (namespace >= 0) label = label.substring(namespace + 1);
        return SecretRedactor.isPasswordCommand(label);
    }
}
