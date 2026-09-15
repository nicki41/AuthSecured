package com.example.authsecured.fabric.command;

import net.minecraft.commands.CommandSourceStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Bridges the numeric permission API used by Minecraft 1.21 and the typed
 * permission API introduced by newer server versions.
 */
final class FabricPermissionChecker {

    private static final PermissionCheck[] CHECKS = {
            source -> true,
            createCheck(1),
            createCheck(2),
            createCheck(3),
            createCheck(4)
    };

    private FabricPermissionChecker() {
    }

    static boolean hasPermission(CommandSourceStack source, int level) {
        return CHECKS[Math.max(0, Math.min(level, 4))].test(source);
    }

    private static PermissionCheck createCheck(int level) {
        try {
            Method legacy = CommandSourceStack.class.getMethod("hasPermission", int.class);
            return source -> invokeBoolean(legacy, source, level);
        } catch (NoSuchMethodException ignored) {
            return createModernCheck(level);
        }
    }

    private static PermissionCheck createModernCheck(int level) {
        try {
            Method permissions = CommandSourceStack.class.getMethod("permissions");
            Class<?> permissionsClass = Class.forName("net.minecraft.server.permissions.Permissions");
            Class<?> permissionClass = Class.forName("net.minecraft.server.permissions.Permission");
            String fieldName = switch (level) {
                case 1 -> "COMMANDS_MODERATOR";
                case 2 -> "COMMANDS_GAMEMASTER";
                case 3 -> "COMMANDS_ADMIN";
                default -> "COMMANDS_OWNER";
            };
            Field permissionField = permissionsClass.getField(fieldName);
            Object requiredPermission = permissionField.get(null);
            Class<?> permissionSetClass = Class.forName("net.minecraft.server.permissions.PermissionSet");
            Method hasPermission = permissionSetClass.getMethod("hasPermission", permissionClass);
            return source -> {
                try {
                    Object permissionSet = permissions.invoke(source);
                    return invokeBoolean(hasPermission, permissionSet, requiredPermission);
                } catch (ReflectiveOperationException exception) {
                    return false;
                }
            };
        } catch (ReflectiveOperationException exception) {
            return source -> false;
        }
    }

    private static boolean invokeBoolean(Method method, Object target, Object argument) {
        try {
            return (boolean) method.invoke(target, argument);
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    @FunctionalInterface
    private interface PermissionCheck {
        boolean test(CommandSourceStack source);
    }
}
