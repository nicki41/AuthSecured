package com.example.authsecured.paper.logging;

import java.util.Locale;
import java.util.Set;

final class SensitiveCommandLogMatcher {

    private static final Set<String> SENSITIVE_COMMANDS = Set.of(
            "login", "l", "register", "reg", "changepassword", "cp"
    );

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
        return SENSITIVE_COMMANDS.contains(label);
    }
}
