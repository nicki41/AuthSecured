package io.github.touchfalls.authsecured.fabric.mixin;

import com.mojang.brigadier.ParseResults;
import io.github.touchfalls.authsecured.fabric.AuthSecuredFabricMod;
import io.github.touchfalls.authsecured.util.SecretRedactor;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents authentication secrets from reaching Minecraft command diagnostics,
 * profiler labels, and debug exception logs. Parsing has already completed at
 * this point, so command execution continues with the original parsed values.
 * Every command path (unsigned, signed, and any future packet) also ends here,
 * so the pre-login command allow-list is enforced a second time.
 */
@Mixin(Commands.class)
public abstract class CommandsMixin {

    @Inject(method = "performCommand", at = @At("HEAD"), cancellable = true)
    private void authsecured$blockUnauthenticatedCommand(ParseResults<CommandSourceStack> parseResults,
                                                         String command, CallbackInfo callback) {
        AuthSecuredFabricMod mod = AuthSecuredFabricMod.getInstance();
        ServerPlayer player = parseResults.getContext().getSource().getPlayer();
        if (mod != null && player != null
                && mod.isCommandBlocked(player.getUUID(), parseResults.getReader().getString())) {
            callback.cancel();
            player.sendSystemMessage(Component.literal(mod.message("messages.command-blocked",
                    "§cYou must authenticate before using this command!")));
        }
    }

    @ModifyVariable(method = "performCommand", at = @At("HEAD"), argsOnly = true)
    private String authsecured$redactSensitiveArguments(String command) {
        return SecretRedactor.redactCommand(command);
    }
}
