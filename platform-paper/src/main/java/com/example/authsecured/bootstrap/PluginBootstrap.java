package com.example.authsecured.bootstrap;

import com.example.authsecured.paper.adapter.PaperAuthPlatform;
import com.example.authsecured.paper.adapter.PaperPlayerRestrictionAdapter;
import com.example.authsecured.paper.command.*;
import com.example.authsecured.paper.listener.*;
import com.example.authsecured.paper.player.PlayerRestrictionManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class PluginBootstrap {

    private final JavaPlugin plugin;
    private DependencyContainer container;
    private PlayerRestrictionManager restrictionManager;

    public PluginBootstrap(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        this.restrictionManager = new PlayerRestrictionManager();
        PaperPlayerRestrictionAdapter restrictionAdapter = new PaperPlayerRestrictionAdapter(restrictionManager);
        PaperAuthPlatform platform = new PaperAuthPlatform(plugin, restrictionAdapter);

        this.container = new DependencyContainer(platform);
        this.container.init();

        registerListeners();
        registerCommands();

        plugin.getLogger().info("AuthSecured plugin successfully enabled!");
    }

    public void disable() {
        if (container != null) {
            container.close();
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
                plugin
        ), plugin);

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
        registerCmd("login", new LoginCommand(container.getAuthService(), restrictionManager, container.getConfigManager(), container.getIpHashService(), plugin));
        registerCmd("register", new RegisterCommand(container.getAuthService(), restrictionManager, container.getConfigManager(), container.getIpHashService(), plugin));
        registerCmd("changepassword", new ChangePasswordCommand(container.getAuthService(), restrictionManager, container.getConfigManager(), container.getIpHashService(), plugin));
        registerCmd("authadmin", new AuthAdminCommand(container.getAuthService(), container.getConfigManager(), plugin));
        registerCmd("logout", new LogoutCommand(container.getAuthService(), restrictionManager, container.getConfigManager()));
        registerCmd("authstatus", new AuthStatusCommand(restrictionManager));
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
