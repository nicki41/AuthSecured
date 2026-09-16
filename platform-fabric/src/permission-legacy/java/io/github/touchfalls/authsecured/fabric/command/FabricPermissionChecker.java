package io.github.touchfalls.authsecured.fabric.command;

import net.minecraft.commands.CommandSourceStack;

final class FabricPermissionChecker {
    private FabricPermissionChecker() {}

    static boolean hasPermission(CommandSourceStack source, int level) {
        return source.hasPermission(Math.max(0, Math.min(level, 4)));
    }
}
