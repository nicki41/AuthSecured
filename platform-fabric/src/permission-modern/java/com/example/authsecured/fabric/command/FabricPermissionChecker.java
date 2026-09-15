package com.example.authsecured.fabric.command;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.Permissions;

final class FabricPermissionChecker {
    private FabricPermissionChecker() {}

    static boolean hasPermission(CommandSourceStack source, int level) {
        int boundedLevel = Math.max(0, Math.min(level, 4));
        if (boundedLevel == 0) return true;
        Permission required = switch (boundedLevel) {
            case 1 -> Permissions.COMMANDS_MODERATOR;
            case 2 -> Permissions.COMMANDS_GAMEMASTER;
            case 3 -> Permissions.COMMANDS_ADMIN;
            default -> Permissions.COMMANDS_OWNER;
        };
        return source.permissions().hasPermission(required);
    }
}
