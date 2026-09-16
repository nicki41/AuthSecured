package io.github.touchfalls.authsecured.paper.listener;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;
import io.github.touchfalls.authsecured.util.SecretRedactor;

import java.util.Arrays;
import java.util.Locale;

/**
 * Executes password-bearing player commands before normal dispatch. Canceling
 * the original event prevents Paper's command logger from receiving the secret.
 */
public final class PaperSensitiveCommandListener implements Listener {

    private final JavaPlugin plugin;

    public PaperSensitiveCommandListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSensitiveCommand(PlayerCommandPreprocessEvent event) {
        ParsedCommand parsed = parse(event.getMessage());
        if (parsed == null || !SecretRedactor.isPasswordCommand(parsed.label())) {
            return;
        }

        PluginCommand command = resolvePasswordCommand(parsed.label());
        CommandExecutor executor = command == null ? null : command.getExecutor();
        if (executor == null) {
            return;
        }

        event.setMessage("/" + parsed.label() + " [REDACTED]");
        event.setCancelled(true);
        PluginCommand selectedCommand = command;
        plugin.getServer().getScheduler().runTask(plugin, () -> executor.onCommand(
                event.getPlayer(), selectedCommand, parsed.label(), parsed.arguments()));
    }

    private PluginCommand resolvePasswordCommand(String label) {
        for (String canonical : java.util.List.of("login", "register", "changepassword")) {
            PluginCommand command = plugin.getCommand(canonical);
            if (command != null && (canonical.equals(label)
                    || command.getAliases().stream().anyMatch(alias -> alias.equalsIgnoreCase(label)))) {
                return command;
            }
        }
        return null;
    }

    static ParsedCommand parse(String message) {
        if (message == null || !message.startsWith("/")) return null;
        String body = message.substring(1).strip();
        if (body.isEmpty()) return null;
        String[] parts = body.split("\\s+");
        String label = parts[0].toLowerCase(Locale.ROOT);
        int namespace = label.indexOf(':');
        if (namespace >= 0) label = label.substring(namespace + 1);
        return new ParsedCommand(label, Arrays.copyOfRange(parts, 1, parts.length));
    }

    record ParsedCommand(String label, String[] arguments) {
    }
}
