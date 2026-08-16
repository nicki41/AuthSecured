package com.example.authsecured.fabric;

import com.example.authsecured.bootstrap.DependencyContainer;
import com.example.authsecured.fabric.adapter.FabricAuthPlatform;
import com.example.authsecured.fabric.adapter.FabricLogger;
import com.example.authsecured.fabric.adapter.FabricPlayerRestrictionAdapter;
import com.example.authsecured.fabric.command.FabricCommandRegistration;
import com.example.authsecured.infrastructure.config.ConfigManager;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AuthSecuredFabricMod implements DedicatedServerModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("AuthSecured");
    private static volatile AuthSecuredFabricMod instance;

    private final FabricPlayerRestrictionAdapter restrictionAdapter = new FabricPlayerRestrictionAdapter();
    private final Map<UUID, String> playerAddresses = new ConcurrentHashMap<>();
    private FabricAuthPlatform platform;
    private DependencyContainer container;
    private volatile boolean initialized;

    @Override
    public void onInitializeServer() {
        instance = this;
        this.platform = new FabricAuthPlatform(new FabricLogger(LOGGER), restrictionAdapter);

        FabricCommandRegistration.register(this);
        registerLifecycleEvents();
        registerConnectionEvents();
        registerRestrictionEvents();
        LOGGER.info("AuthSecured Fabric adapter registered; core initialization will run during server startup.");
    }

    private void registerLifecycleEvents() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            platform.setServer(server);
            try {
                this.container = new DependencyContainer(platform);
                this.container.init();
                this.initialized = true;
                LOGGER.info("AuthSecured initialized for Fabric at {}",
                        platform.getDataFolder().getAbsolutePath());
            } catch (RuntimeException exception) {
                initialized = false;
                LOGGER.error("AuthSecured failed to initialize; players will remain restricted", exception);
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            initialized = false;
            if (container != null) {
                container.close();
                container = null;
            }
            playerAddresses.clear();
            LOGGER.info("AuthSecured Fabric adapter stopped.");
        });
    }

    private void registerConnectionEvents() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            UUID uuid = player.getUUID();
            String address = extractAddress(handler.getRemoteAddress());
            playerAddresses.put(uuid, address);
            restrictionAdapter.setPlayerUnauthenticated(uuid, null);

            if (!initialized) {
                player.sendSystemMessage(Component.literal("§cAuthSecured failed to initialize. Contact a server administrator."));
                return;
            }

            ConfigManager config = container.getConfigManager();
            if (platform.isOnlineMode() && config.getBoolean("auth.online-mode.bypass-password", true)) {
                restrictionAdapter.setPlayerAuthenticated(uuid);
                player.sendSystemMessage(Component.literal(config.getMessage("messages.auth-bypass",
                        "§aOnline-mode active: automatically authenticated!")));
                return;
            }

            byte[] ipHash = container.getIpHashService().hashIp(address);
            container.getSessionService().isValidActiveSession(uuid, ipHash)
                    .thenCompose(valid -> {
                        if (valid) {
                            platform.runTaskOnMainThread(() -> {
                                restrictionAdapter.setPlayerAuthenticated(uuid);
                                platform.sendMessage(uuid, config.getMessage("messages.session-restored",
                                        "§aSession restored automatically!"));
                            });
                            return java.util.concurrent.CompletableFuture.completedFuture(null);
                        }
                        return container.getAuthService().isRegistered(uuid).thenAccept(registered ->
                                platform.runTaskOnMainThread(() -> platform.sendMessage(uuid, config.getMessage(
                                        registered ? "messages.login-prompt" : "messages.register-prompt",
                                        registered
                                                ? "§ePlease log in using /login <password>"
                                                : "§ePlease register using /register <password> <confirmPassword>"))));
                    })
                    .exceptionally(throwable -> {
                        LOGGER.error("Failed to establish authentication state for {}", player.getName().getString(), throwable);
                        return null;
                    });
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID uuid = handler.getPlayer().getUUID();
            restrictionAdapter.removePlayer(uuid);
            playerAddresses.remove(uuid);
        });
    }

    private void registerRestrictionEvents() {
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            if (isRestricted(sender.getUUID(), "restrictions.block-chat", true)) {
                sender.sendSystemMessage(Component.literal(message("messages.must-auth",
                        "§cYou must authenticate before doing this!")));
                return false;
            }
            return true;
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer player
                    && isRestricted(player.getUUID(), "restrictions.block-damage-taken", true)) {
                return false;
            }
            if (source.getEntity() instanceof ServerPlayer player
                    && isRestricted(player.getUUID(), "restrictions.block-damage-dealt", true)) {
                return false;
            }
            return true;
        });
    }

    private static String extractAddress(SocketAddress socketAddress) {
        if (socketAddress instanceof InetSocketAddress inetAddress) {
            return inetAddress.getAddress() != null
                    ? inetAddress.getAddress().getHostAddress()
                    : inetAddress.getHostString();
        }
        return socketAddress != null ? socketAddress.toString() : "0.0.0.0";
    }

    public boolean isInitialized() {
        return initialized;
    }

    public DependencyContainer getContainer() {
        return container;
    }

    public FabricPlayerRestrictionAdapter getRestrictionAdapter() {
        return restrictionAdapter;
    }

    public String getPlayerAddress(UUID uuid) {
        return playerAddresses.getOrDefault(uuid, "0.0.0.0");
    }

    public boolean isRestricted(UUID uuid, String configPath, boolean defaultValue) {
        return initialized
                && !restrictionAdapter.isAuthenticated(uuid)
                && container.getConfigManager().getBoolean(configPath, defaultValue);
    }

    public boolean isAllowedCommand(String command) {
        if (!initialized) {
            return false;
        }
        String normalized = command.startsWith("/") ? command : "/" + command;
        normalized = normalized.strip().toLowerCase(Locale.ROOT).split("\\s+", 2)[0];
        List<String> defaults = List.of("/login", "/l", "/register", "/reg", "/changepassword", "/cp");
        return container.getConfigManager().getStringList("restrictions.allowed-commands", defaults)
                .stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(normalized::equals);
    }

    public String message(String key, String fallback) {
        return initialized ? container.getConfigManager().getMessage(key, fallback) : fallback;
    }

    public static AuthSecuredFabricMod getInstance() {
        return instance;
    }
}
