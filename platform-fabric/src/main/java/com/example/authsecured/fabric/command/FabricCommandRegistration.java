package com.example.authsecured.fabric.command;

import com.example.authsecured.fabric.AuthSecuredFabricMod;
import com.example.authsecured.ports.UnifiedCommandExecutor;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;

public final class FabricCommandRegistration {

    private FabricCommandRegistration() {
    }

    public static void register(AuthSecuredFabricMod mod) {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("register")
                    .then(Commands.argument("password", StringArgumentType.string())
                            .then(Commands.argument("confirmPassword", StringArgumentType.string())
                                    .executes(context -> {
                                        ServerPlayer player = context.getSource().getPlayerOrException();
                                        if (!ready(mod, player)) return 0;
                                        String password = StringArgumentType.getString(context, "password");
                                        String confirmation = StringArgumentType.getString(context, "confirmPassword");
                                        executor(mod).executeRegister(player.getUUID(), player.getName().getString(),
                                                password.toCharArray(), confirmation.toCharArray(),
                                                mod.getPlayerAddress(player.getUUID()));
                                        return 1;
                                    }))));

            dispatcher.register(Commands.literal("reg").redirect(dispatcher.getRoot().getChild("register")));

            dispatcher.register(Commands.literal("login")
                    .then(Commands.argument("password", StringArgumentType.string())
                            .executes(context -> {
                                ServerPlayer player = context.getSource().getPlayerOrException();
                                if (!ready(mod, player)) return 0;
                                executor(mod).executeLogin(player.getUUID(), player.getName().getString(),
                                        StringArgumentType.getString(context, "password").toCharArray(),
                                        mod.getPlayerAddress(player.getUUID()));
                                return 1;
                            })));

            dispatcher.register(Commands.literal("l").redirect(dispatcher.getRoot().getChild("login")));

            dispatcher.register(Commands.literal("changepassword")
                    .then(Commands.argument("oldPassword", StringArgumentType.string())
                            .then(Commands.argument("newPassword", StringArgumentType.string())
                                    .executes(context -> {
                                        ServerPlayer player = context.getSource().getPlayerOrException();
                                        if (!ready(mod, player)) return 0;
                                        executor(mod).executeChangePassword(player.getUUID(),
                                                StringArgumentType.getString(context, "oldPassword").toCharArray(),
                                                StringArgumentType.getString(context, "newPassword").toCharArray(),
                                                mod.getPlayerAddress(player.getUUID()));
                                        return 1;
                                    }))));

            dispatcher.register(Commands.literal("cp").redirect(dispatcher.getRoot().getChild("changepassword")));

            dispatcher.register(Commands.literal("logout").executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                if (!ready(mod, player)) return 0;
                executor(mod).executeLogout(player.getUUID());
                return 1;
            }));

            dispatcher.register(Commands.literal("authstatus").executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                if (!ready(mod, player)) return 0;
                executor(mod).executeAuthStatus(player.getUUID());
                return 1;
            }));

            registerAdminCommands(dispatcher, mod);
        });
    }

    private static void registerAdminCommands(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher,
                                              AuthSecuredFabricMod mod) {
        dispatcher.register(Commands.literal("authadmin")
                .requires(source -> mod.isInitialized() && FabricPermissionChecker.isAdmin(source))
                .then(Commands.literal("reload").executes(context -> {
                    mod.getContainer().reloadConfig();
                    context.getSource().sendSystemMessage(Component.literal("§aAuthSecured configuration reloaded."));
                    return 1;
                }))
                .then(Commands.literal("unlock")
                        .then(Commands.argument("player", StringArgumentType.word()).executes(context -> {
                            String player = StringArgumentType.getString(context, "player");
                            mod.getContainer().getAuthService().unlockAccount(player).thenAccept(success ->
                                    context.getSource().getServer().execute(() -> context.getSource().sendSystemMessage(
                                            Component.literal(success ? "§aAccount unlocked." : "§cAccount not found."))));
                            return 1;
                        })))
                .then(Commands.literal("unregister")
                        .then(Commands.argument("player", StringArgumentType.word()).executes(context -> {
                            String player = StringArgumentType.getString(context, "player");
                            mod.getContainer().getAuthService().unregister(player).thenAccept(success ->
                                    context.getSource().getServer().execute(() -> context.getSource().sendSystemMessage(
                                            Component.literal(success ? "§aAccount unregistered." : "§cAccount not found."))));
                            return 1;
                        })))
                .then(Commands.literal("resetpassword")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .then(Commands.argument("newPassword", StringArgumentType.string()).executes(context -> {
                                    char[] password = StringArgumentType.getString(context, "newPassword").toCharArray();
                                    String player = StringArgumentType.getString(context, "player");
                                    mod.getContainer().getAuthService().resetPassword(player, password)
                                            .whenComplete((success, throwable) -> {
                                                Arrays.fill(password, ' ');
                                                context.getSource().getServer().execute(() -> context.getSource().sendSystemMessage(
                                                        Component.literal(throwable == null && success
                                                                ? "§aPassword reset." : "§cPassword reset failed.")));
                                            });
                                    return 1;
                                })))));
    }

    private static UnifiedCommandExecutor executor(AuthSecuredFabricMod mod) {
        return mod.getContainer().getCommandExecutor();
    }

    private static boolean ready(AuthSecuredFabricMod mod, ServerPlayer player) {
        if (mod.isInitialized()) {
            return true;
        }
        player.sendSystemMessage(Component.literal("§cAuthSecured is not initialized."));
        return false;
    }
}
