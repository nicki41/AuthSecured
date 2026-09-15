package com.example.authsecured.bootstrap;

import com.example.authsecured.paper.adapter.PaperAuthPlatform;
import com.example.authsecured.paper.adapter.PaperPlayerRestrictionAdapter;
import com.example.authsecured.paper.command.*;
import com.example.authsecured.paper.listener.*;
import com.example.authsecured.paper.logging.PaperSensitiveCommandLogFilter;
import com.example.authsecured.paper.player.PlayerRestrictionManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;

public class PluginBootstrap {

    private final JavaPlugin plugin;
    private DependencyContainer container;
    private PlayerRestrictionManager restrictionManager;
    private PaperSensitiveCommandLogFilter sensitiveCommandLogFilter;

    public PluginBootstrap(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        this.sensitiveCommandLogFilter = PaperSensitiveCommandLogFilter.install();
        this.restrictionManager = new PlayerRestrictionManager();
        PaperPlayerRestrictionAdapter restrictionAdapter = new PaperPlayerRestrictionAdapter(restrictionManager);
        PaperAuthPlatform platform = new PaperAuthPlatform(plugin, restrictionAdapter);

        this.container = new DependencyContainer(platform);
        this.container.init();

        registerCommands();
        registerListeners();
        registerAuthenticationTimeoutTask();

        plugin.getLogger().info("AuthSecured plugin successfully enabled!");
    }

    public void disable() {
        if (container != null) {
            container.close();
        }
        if (sensitiveCommandLogFilter != null) {
            sensitiveCommandLogFilter.close();
            sensitiveCommandLogFilter = null;
        }
        plugin.getLogger().info("AuthSecured plugin disabled.");
    }

    private void registerListeners() {
        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new PlayerConnectionListener(
                container.getAuthService(),
                restrictionManager,
                container.getConfigManager(),
                container.getIpHashService(),
                container.getAuthenticationTimeoutService(),
                plugin
        ), plugin);

        pm.registerEvents(new PaperSensitiveCommandListener(plugin), plugin);

        pm.registerEvents(new PlayerAuthRestrictionListener(
                restrictionManager,
                container.getConfigManager()
        ), plugin);

        pm.registerEvents(new PlayerCommandListener(
                restrictionManager,
                container.getConfigManager()
        ), plugin);

        pm.registerEvents(new PlayerInteractionListener(
                restrictionManager
        ), plugin);
    }

    private void registerCommands() {
        registerCmd("login", new LoginCommand(container.getAuthService(), restrictionManager, container.getConfigManager(), container.getIpHashService(), container.getAuthenticationTimeoutService(), plugin));
        registerCmd("register", new RegisterCommand(container.getAuthService(), restrictionManager, container.getConfigManager(), container.getIpHashService(), container.getAuthenticationTimeoutService(), plugin));
        registerCmd("changepassword", new ChangePasswordCommand(container.getAuthService(), restrictionManager, container.getConfigManager(), container.getIpHashService(), container.getAuthenticationTimeoutService(), plugin));
        AuthAdminCommand adminCommand = new AuthAdminCommand(container, plugin);
        registerCmd("authadmin", adminCommand);
        PluginCommand authAdmin = plugin.getCommand("authadmin");
        if (authAdmin != null) {
            authAdmin.setTabCompleter(adminCommand);
        }
        registerCmd("logout", new LogoutCommand(container.getAuthService(), restrictionManager, container.getConfigManager(), container.getAuthenticationTimeoutService()));
        registerCmd("authstatus", new AuthStatusCommand(restrictionManager, container.getConfigManager()));
    }

    private void registerAuthenticationTimeoutTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
                if (!restrictionManager.isAuthenticated(player.getUniqueId())) {
                    container.getAuthenticationTimeoutService().ensureStarted(player.getUniqueId());
                }
            }
            for (java.util.UUID uuid : container.getAuthenticationTimeoutService().drainExpired(Instant.now())) {
                if (!restrictionManager.isAuthenticated(uuid) && Bukkit.getPlayer(uuid) != null) {
                    container.getSecurityService().logSecurityEvent(
                            null,
                            com.example.authsecured.domain.security.SecurityEventType.AUTH_TIMEOUT,
                            null,
                            "authentication deadline expired"
                    );
                    org.bukkit.entity.Player player = Bukkit.getPlayer(uuid);
                    if (player != null) {
                        player.kickPlayer(container.getConfigManager().getMessage(
                                "messages.authentication-timeout-kick",
                                "&cAuthentication time expired. Reconnect and try again."
                        ));
                    }
                }
            }
        }, 20L, 20L);
    }

    private void registerCmd(String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand cmd = plugin.getCommand(name);
        if (cmd != null) {
            cmd.setExecutor(executor);
        } else {
            plugin.getLogger().warning("Could not register command '/" + name + "': Command missing from plugin.yml");
        }
    }
}
