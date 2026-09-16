package io.github.touchfalls.authsecured.fabric.mixin;

import io.github.touchfalls.authsecured.util.SecretRedactor;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Prevents authentication secrets from reaching Minecraft command diagnostics,
 * profiler labels, and debug exception logs. Parsing has already completed at
 * this point, so command execution continues with the original parsed values.
 */
@Mixin(Commands.class)
public abstract class CommandsMixin {

    @ModifyVariable(method = "performCommand", at = @At("HEAD"), argsOnly = true)
    private String authsecured$redactSensitiveArguments(String command) {
        return SecretRedactor.redactCommand(command);
    }
}
