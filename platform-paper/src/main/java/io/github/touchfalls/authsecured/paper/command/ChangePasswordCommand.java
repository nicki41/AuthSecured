package io.github.touchfalls.authsecured.paper.command;

import io.github.touchfalls.authsecured.application.AuthService;
import io.github.touchfalls.authsecured.application.AuthenticationTimeoutService;
import io.github.touchfalls.authsecured.infrastructure.config.ConfigManager;
import io.github.touchfalls.authsecured.infrastructure.security.IpHashService;
import io.github.touchfalls.authsecured.paper.player.PlayerRestrictionManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.UUID;

public class ChangePasswordCommand implements CommandExecutor {

    private final AuthService authService;
    private final PlayerRestrictionManager restrictionManager;
    private final ConfigManager configManager;
    private final IpHashService ipHashService;
    private final AuthenticationTimeoutService authenticationTimeoutService;
    private final Plugin plugin;

    public ChangePasswordCommand(AuthService authService, PlayerRestrictionManager restrictionManager,
                                 ConfigManager configManager, IpHashService ipHashService,
                                 AuthenticationTimeoutService authenticationTimeoutService, Plugin plugin) {
        this.authService = authService;
        this.restrictionManager = restrictionManager;
        this.configManager = configManager;
        this.ipHashService = ipHashService;
        this.authenticationTimeoutService = authenticationTimeoutService;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage(
                    "messages.only-players", "&cOnly players can use this command."));
            return true;
        }

        UUID uuid = player.getUniqueId();
        if (args.length < 2) {
            player.sendMessage(configManager.getMessage("messages.changepass-usage", "&cUsage: /changepassword <oldPassword> <newPassword>"));
            return true;
        }

        char[] oldPassword = args[0].toCharArray();
        char[] newPassword = args[1].toCharArray();
        String ipStr = (player.getAddress() != null && player.getAddress().getAddress() != null)
                ? player.getAddress().getAddress().getHostAddress()
                : null;
        byte[] ipHash = ipHashService.hashIp(ipStr);

        authService.changePassword(uuid, oldPassword, newPassword, ipHash).thenAccept(result -> {
            Arrays.fill(oldPassword, ' ');
            Arrays.fill(newPassword, ' ');
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                switch (result) {
                    case SUCCESS -> {
                            restrictionManager.setPlayerAuthenticated(uuid);
                            authenticationTimeoutService.clear(uuid);
                            player.sendMessage(configManager.getMessage("messages.changepass-success", "&aPassword changed successfully!"));
                    }
                    case INVALID_PASSWORD ->
                            player.sendMessage(configManager.getMessage("messages.old-password-invalid", "&cOld password is incorrect."));
                    case VALIDATION_FAILED ->
                            player.sendMessage(configManager.getMessage("messages.password-policy-failed", "&cNew password does not meet policy requirements."));
                    default ->
                            player.sendMessage(configManager.getMessage("messages.error", "&cAn internal error occurred."));
                }
            });
        }).exceptionally(ex -> {
            Arrays.fill(oldPassword, ' ');
            Arrays.fill(newPassword, ' ');
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    player.sendMessage(configManager.getMessage("messages.error", "&cAn internal error occurred."));
                }
            });
            plugin.getLogger().severe("Password change processing failed for " + player.getName() + ".");
            return null;
        });

        return true;
    }
}
