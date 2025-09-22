package com.mcmiddleearth.introduction.rooms;

import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.Objects;

public class FirstRoom extends Room {

    private final NamespacedKey cameraOverlay;

    public FirstRoom(ConfigurationSection config) {
        super(config);
        this.cameraOverlay = NamespacedKey.fromString(Objects.requireNonNull(config.getString("cameraOverlay")));
    }

    @Override
    public NamespacedKey selectCameraOverlay(Player player) {
        return cameraOverlay;
    }
}
