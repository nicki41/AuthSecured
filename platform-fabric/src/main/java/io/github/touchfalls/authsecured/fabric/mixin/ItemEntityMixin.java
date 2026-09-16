package io.github.touchfalls.authsecured.fabric.mixin;

import io.github.touchfalls.authsecured.fabric.AuthSecuredFabricMod;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {

    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void authsecured$blockItemPickup(Player player, CallbackInfo callback) {
        AuthSecuredFabricMod mod = AuthSecuredFabricMod.getInstance();
        if (mod != null
                && mod.isRestricted(player.getUUID(), "restrictions.block-item-pickup", true)) {
            callback.cancel();
        }
    }
}
