package com.example.authsecured.paper.command;

import com.example.authsecured.infrastructure.config.ConfigManager;
import com.example.authsecured.paper.player.PlayerRestrictionManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AuthStatusCommand implements CommandExecutor {

    private final PlayerRestrictionManager restrictionManager;
    private final ConfigManager configManager;

    public AuthStatusCommand(PlayerRestrictionManager restrictionManager, ConfigManager configManager) {
        this.restrictionManager = restrictionManager;
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage(
                    "messages.only-players", "&cOnly players can use this command."));
            return true;
        }

        boolean authenticated = restrictionManager.isAuthenticated(player.getUniqueId());
        if (authenticated) {
            player.sendMessage(configManager.getMessage(
                    "messages.auth-status-authenticated", "&aAuthentication status: authenticated."));
        } else {
            player.sendMessage(configManager.getMessage(
                    "messages.auth-status-required", "&cAuthentication status: login required."));
        }
        return true;
    }
}
