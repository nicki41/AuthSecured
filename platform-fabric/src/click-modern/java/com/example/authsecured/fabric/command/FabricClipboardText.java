package com.example.authsecured.fabric.command;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

final class FabricClipboardText {
    private FabricClipboardText() {}

    static Component create(String prefix, String password) {
        return Component.literal(prefix).append(Component.literal(password).withStyle(style -> style
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.CopyToClipboard(password))));
    }
}
