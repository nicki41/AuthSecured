package io.github.touchfalls.authsecured.paper.command;

import io.github.touchfalls.authsecured.infrastructure.config.ConfigManager;
import io.github.touchfalls.authsecured.ports.UnifiedCommandExecutor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Parses {@code /changepassword}'s arguments; all authentication logic, state
 * changes and messaging live in {@link UnifiedCommandExecutor}, shared with Fabric.
 */
public class ChangePasswordCommand implements CommandExecutor {

    private final UnifiedCommandExecutor commandExecutor;
    private final ConfigManager configManager;

    public ChangePasswordCommand(UnifiedCommandExecutor commandExecutor, ConfigManager configManager) {
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
        if (args.length < 2) {
            player.sendMessage(configManager.getMessage(
                    "messages.changepass-usage", "&cUsage: /changepassword <oldPassword> <newPassword>"));
            return true;
        }

        String ipAddress = (player.getAddress() != null && player.getAddress().getAddress() != null)
                ? player.getAddress().getAddress().getHostAddress() : null;
        commandExecutor.executeChangePassword(player.getUniqueId(), player.getName(),
                args[0].toCharArray(), args[1].toCharArray(), ipAddress);
        return true;
    }
}
