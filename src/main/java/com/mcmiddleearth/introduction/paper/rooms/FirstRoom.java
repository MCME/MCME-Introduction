package com.mcmiddleearth.introduction.paper.rooms;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;
import java.util.Objects;

public class FirstRoom extends Room {

    private final NamespacedKey cameraOverlay;
    private final Component messageWelcome;//, messageRpWarning;

    // Display entity related
    private org.bukkit.entity.TextDisplay displayEntity;
    private org.bukkit.Location displayLocation;

    public FirstRoom(ConfigurationSection config, @Nullable ConfigurationSection locationConfig) {
        super(config, locationConfig);
        this.cameraOverlay = NamespacedKey.fromString(Objects.requireNonNull(config.getString("cameraOverlay")));
        overlays.add(cameraOverlay);
        messageWelcome = getMessage(config.getString("messageWelcome", "{\"text\":\"\"}"));
        //messageRpWarning = getMessage(config.getString("messageRpWarning", "{\"text\":\"\"}"));

        ConfigurationSection disp = (locationConfig!=null?config.getConfigurationSection("displayEntity"):null);
        if(disp != null) {
            // Always use the room's configured world (do not allow overriding in the subsection)
            String worldName = config.getString("world");
            World displayWorld = (worldName != null ? Bukkit.getWorld(worldName) : null);
            if(displayWorld != null && disp.getString("pos") != null) {
                displayLocation = getLocation(displayWorld, "pos", disp);
            }
            Component displayText = getMessage(disp.getString("text", "{\"text\":\"\"}"));

            // spawn the TextDisplay entity synchronously as part of the room construction
            if(displayLocation != null && displayWorld != null) {
                try {
                    displayEntity = (org.bukkit.entity.TextDisplay) displayWorld.spawn(displayLocation, org.bukkit.entity.TextDisplay.class);
                    displayEntity.text(displayText);
                    // Try to set billboard to face players if API supports it
                    try {
                        java.lang.reflect.Method m = displayEntity.getClass().getMethod("setBillboard", org.bukkit.entity.Display.Billboard.class);
                        m.invoke(displayEntity, org.bukkit.entity.Display.Billboard.CENTER);
                    } catch (Exception ignored) {
                        // ignore if method not available
                    }
                } catch (NoSuchMethodError | ClassCastException ex) {
                    Bukkit.getLogger().warning("TextDisplay entity not supported on this server version. Skipping displayEntity for FirstRoom.");
                    displayEntity = null;
                }
            }
        }
    }

    @Override
    public NamespacedKey selectCameraOverlay(Player player) {
        return cameraOverlay;
    }

    @Override
    public void sendChat(Player player) {
        player.sendMessage(messageWelcome);
    }

    @Override
    public void unload() {
        super.unload();
        if(displayEntity != null && !displayEntity.isDead()) {
            try {
                displayEntity.remove();
            } catch (Throwable t) {
                // ignore
            }
            displayEntity = null;
        }
    }

    /*public void sendRpWarning(Player player) {
        player.sendMessage(messageRpWarning);
    }*/
}
