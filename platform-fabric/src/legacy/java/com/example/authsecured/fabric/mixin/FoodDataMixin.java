package com.example.authsecured.fabric.mixin;

import com.example.authsecured.fabric.AuthSecuredFabricMod;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FoodData.class)
public abstract class FoodDataMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void authsecured$freezeHunger(Player player, CallbackInfo callback) {
        AuthSecuredFabricMod mod = AuthSecuredFabricMod.getInstance();
        if (mod != null && mod.isRestricted(player.getUUID(), "restrictions.block-hunger", true)) {
            callback.cancel();
        }
    }
}
