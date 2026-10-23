package io.github.touchfalls.authsecured.paper.command;

import io.github.touchfalls.authsecured.infrastructure.config.ConfigManager;
import io.github.touchfalls.authsecured.ports.UnifiedCommandExecutor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Parses {@code /login}'s arguments; all authentication logic, state changes
 * and messaging live in {@link UnifiedCommandExecutor}, shared with Fabric.
 */
public class LoginCommand implements CommandExecutor {

    private final UnifiedCommandExecutor commandExecutor;
    private final ConfigManager configManager;

    public LoginCommand(UnifiedCommandExecutor commandExecutor, ConfigManager configManager) {
        this.commandExecutor = commandExecutor;
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage(
                    "messages.only-players", "&cOnly players can use this command."));
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(configManager.getMessage("messages.login-usage", "&cUsage: /login <password>"));
            return true;
        }

        String ipAddress = (player.getAddress() != null && player.getAddress().getAddress() != null)
                ? player.getAddress().getAddress().getHostAddress() : null;
        commandExecutor.executeLogin(player.getUniqueId(), player.getName(), args[0].toCharArray(), ipAddress);
        return true;
    }
}
