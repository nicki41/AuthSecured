package com.example.authsecured.fabric.mixin;

import com.example.authsecured.fabric.AuthSecuredFabricMod;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {

    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
    private void authsecured$blockMovement(ServerboundMovePlayerPacket packet, CallbackInfo callback) {
        if (restricted("restrictions.freeze-player", true)) {
            callback.cancel();
        }
    }

    @Inject(method = "handleContainerClick", at = @At("HEAD"), cancellable = true)
    private void authsecured$blockInventoryClick(ServerboundContainerClickPacket packet, CallbackInfo callback) {
        if (restricted("restrictions.block-inventory", true)) {
            callback.cancel();
        }
    }

    @Inject(method = "handleContainerButtonClick", at = @At("HEAD"), cancellable = true)
    private void authsecured$blockInventoryButton(ServerboundContainerButtonClickPacket packet, CallbackInfo callback) {
        if (restricted("restrictions.block-inventory", true)) {
            callback.cancel();
        }
    }

    @Inject(method = "handleUseItemOn", at = @At("HEAD"), cancellable = true)
    private void authsecured$blockUseItemOn(ServerboundUseItemOnPacket packet, CallbackInfo callback) {
        if (restricted("restrictions.block-interactions", true)
                || restricted("restrictions.block-block-place", true)) {
            callback.cancel();
        }
    }

    @Inject(method = "handleUseItem", at = @At("HEAD"), cancellable = true)
    private void authsecured$blockUseItem(ServerboundUseItemPacket packet, CallbackInfo callback) {
        if (restricted("restrictions.block-interactions", true)) {
            callback.cancel();
        }
    }

    @Inject(method = "handleInteract", at = @At("HEAD"), cancellable = true)
    private void authsecured$blockEntityInteraction(ServerboundInteractPacket packet, CallbackInfo callback) {
        if (restricted("restrictions.block-interactions", true)
                || restricted("restrictions.block-damage-dealt", true)) {
            callback.cancel();
        }
    }

    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void authsecured$blockPlayerAction(ServerboundPlayerActionPacket packet, CallbackInfo callback) {
        String action = packet.getAction().name();
        boolean dropAction = action.startsWith("DROP_");
        boolean destroyAction = action.contains("DESTROY_BLOCK");
        if ((dropAction && restricted("restrictions.block-item-drop", true))
                || (destroyAction && restricted("restrictions.block-block-break", true))) {
            callback.cancel();
        }
    }

    @Inject(method = "handleChatCommand", at = @At("HEAD"), cancellable = true, require = 0)
    private void authsecured$blockCommand(ServerboundChatCommandPacket packet, CallbackInfo callback) {
        AuthSecuredFabricMod mod = AuthSecuredFabricMod.getInstance();
        if (mod != null
                && mod.isRestricted(player.getUUID(), "restrictions.block-commands", true)
                && !mod.isAllowedCommand(packet.command())) {
            callback.cancel();
            player.sendSystemMessage(Component.literal(mod.message("messages.command-blocked",
                    "§cYou must authenticate before using this command!")));
        }
    }

    private boolean restricted(String path, boolean defaultValue) {
        AuthSecuredFabricMod mod = AuthSecuredFabricMod.getInstance();
        return mod != null && mod.isRestricted(player.getUUID(), path, defaultValue);
    }
}
