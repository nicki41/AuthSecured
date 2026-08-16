package com.example.authsecured.fabric.command;

import net.minecraft.commands.CommandSourceStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Bridges the numeric permission API used by Minecraft 1.21 and the typed
 * permission API introduced by newer server versions.
 */
final class FabricPermissionChecker {

    private FabricPermissionChecker() {
    }

    static boolean isAdmin(CommandSourceStack source) {
        try {
            Method legacy = source.getClass().getMethod("hasPermission", int.class);
            return (boolean) legacy.invoke(source, 4);
        } catch (ReflectiveOperationException ignored) {
            return hasModernPermission(source);
        }
    }

    private static boolean hasModernPermission(CommandSourceStack source) {
        try {
            Object permissionSet = source.getClass().getMethod("permissions").invoke(source);
            Class<?> permissionsClass = Class.forName("net.minecraft.server.permissions.Permissions");
            Class<?> permissionClass = Class.forName("net.minecraft.server.permissions.Permission");
            Field adminField = permissionsClass.getField("COMMANDS_ADMIN");
            Object adminPermission = adminField.get(null);
            Class<?> permissionSetClass = Class.forName("net.minecraft.server.permissions.PermissionSet");
            Method hasPermission = permissionSetClass.getMethod("hasPermission", permissionClass);
            return (boolean) hasPermission.invoke(permissionSet, adminPermission);
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }
}
