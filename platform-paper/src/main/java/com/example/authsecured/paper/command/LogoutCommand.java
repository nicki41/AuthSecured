package com.example.authsecured.paper.command;

import com.example.authsecured.application.AuthService;
import com.example.authsecured.application.AuthenticationTimeoutService;
import com.example.authsecured.infrastructure.config.ConfigManager;
import com.example.authsecured.paper.player.PlayerRestrictionManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LogoutCommand implements CommandExecutor {

    private final AuthService authService;
    private final PlayerRestrictionManager restrictionManager;
    private final ConfigManager configManager;
    private final AuthenticationTimeoutService authenticationTimeoutService;

    public LogoutCommand(AuthService authService, PlayerRestrictionManager restrictionManager,
                         ConfigManager configManager, AuthenticationTimeoutService authenticationTimeoutService) {
        this.authService = authService;
        this.restrictionManager = restrictionManager;
        this.configManager = configManager;
        this.authenticationTimeoutService = authenticationTimeoutService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage(
                    "messages.only-players", "&cOnly players can use this command."));
            return true;
        }

        if (!restrictionManager.isAuthenticated(player.getUniqueId())) {
            player.sendMessage(configManager.getMessage("messages.must-auth", "&cYou are not authenticated!"));
            return true;
        }

        restrictionManager.setPlayerUnauthenticated(player.getUniqueId(), player.getLocation());
        authenticationTimeoutService.start(player.getUniqueId());
        authService.getSessionService().revokeActiveSessionForPlayer(player.getUniqueId());
        player.sendMessage(configManager.getMessage("messages.logged-out", "&aYou have been logged out."));
        return true;
    }
}
