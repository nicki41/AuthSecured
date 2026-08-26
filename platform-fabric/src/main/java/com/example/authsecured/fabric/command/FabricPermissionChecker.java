package com.example.authsecured.fabric.command;

import net.minecraft.commands.CommandSourceStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Bridges the numeric permission API used by Minecraft 1.21 and the typed
 * permission API introduced by newer server versions.
 */
final class FabricPermissionChecker {

    private static final PermissionCheck ADMIN_CHECK = createAdminCheck();

    private FabricPermissionChecker() {
    }

    static boolean isAdmin(CommandSourceStack source) {
        return ADMIN_CHECK.test(source);
    }

    private static PermissionCheck createAdminCheck() {
        try {
            Method legacy = CommandSourceStack.class.getMethod("hasPermission", int.class);
            return source -> invokeBoolean(legacy, source, 4);
        } catch (NoSuchMethodException ignored) {
            return createModernCheck();
        }
    }

    private static PermissionCheck createModernCheck() {
        try {
            Method permissions = CommandSourceStack.class.getMethod("permissions");
            Class<?> permissionsClass = Class.forName("net.minecraft.server.permissions.Permissions");
            Class<?> permissionClass = Class.forName("net.minecraft.server.permissions.Permission");
            Field adminField = permissionsClass.getField("COMMANDS_ADMIN");
            Object adminPermission = adminField.get(null);
            Class<?> permissionSetClass = Class.forName("net.minecraft.server.permissions.PermissionSet");
            Method hasPermission = permissionSetClass.getMethod("hasPermission", permissionClass);
            return source -> {
                try {
                    Object permissionSet = permissions.invoke(source);
                    return invokeBoolean(hasPermission, permissionSet, adminPermission);
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
