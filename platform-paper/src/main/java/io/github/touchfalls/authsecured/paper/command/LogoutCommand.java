package io.github.touchfalls.authsecured.paper.command;

import io.github.touchfalls.authsecured.infrastructure.config.ConfigManager;
import io.github.touchfalls.authsecured.ports.UnifiedCommandExecutor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Parses {@code /logout}; all state changes and messaging live in
 * {@link UnifiedCommandExecutor}, shared with Fabric.
 */
public class LogoutCommand implements CommandExecutor {

    private final UnifiedCommandExecutor commandExecutor;
    private final ConfigManager configManager;

    public LogoutCommand(UnifiedCommandExecutor commandExecutor, ConfigManager configManager) {
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
        commandExecutor.executeLogout(player.getUniqueId());
        return true;
    }
}
