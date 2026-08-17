package com.example.authsecured.fabric.mixin;

import com.example.authsecured.fabric.AuthSecuredFabricMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FoodData.class)
public abstract class FoodDataMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void authsecured$freezeHunger(ServerPlayer player, CallbackInfo callback) {
        AuthSecuredFabricMod mod = AuthSecuredFabricMod.getInstance();
        if (mod != null && mod.isRestricted(player.getUUID(), "restrictions.block-hunger", true)) {
            callback.cancel();
        }
    }
}
