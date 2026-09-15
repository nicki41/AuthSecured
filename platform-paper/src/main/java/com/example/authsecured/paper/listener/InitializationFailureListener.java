package com.example.authsecured.paper.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

/** Denies every login when authentication storage or configuration failed to initialize. */
public final class InitializationFailureListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(PlayerLoginEvent event) {
        event.disallow(PlayerLoginEvent.Result.KICK_OTHER,
                "AuthSecured failed to initialize. Contact the server administrator.");
    }
}
