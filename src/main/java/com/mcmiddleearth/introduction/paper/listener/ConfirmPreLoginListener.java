package com.mcmiddleearth.introduction.paper.listener;

import com.mcmiddleearth.introduction.paper.IntroductionPlugin;
import com.mcmiddleearth.introduction.paper.confirmData.ConfirmDataManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

/**
 * Loads confirmation data for players on AsyncPlayerPreLoginEvent.
 */
public class ConfirmPreLoginListener implements Listener {

    private final ConfirmDataManager manager = IntroductionPlugin.getInstance().getConfirmDataManager();

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        String uuid = event.getUniqueId().toString();
        manager.loadPlayerData(uuid);
    }
}
