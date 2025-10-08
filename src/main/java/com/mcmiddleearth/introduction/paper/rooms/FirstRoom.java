package com.mcmiddleearth.introduction.paper.rooms;

import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.Objects;

public class FirstRoom extends Room {

    private final NamespacedKey cameraOverlay;
    private Component messageWelcome, messageRpWarning;

    public FirstRoom(ConfigurationSection config) {
        super(config);
        this.cameraOverlay = NamespacedKey.fromString(Objects.requireNonNull(config.getString("cameraOverlay")));
        messageWelcome = getMessage(config.getString("messageWelcome", "{\"text\":\"\"}"));
        messageRpWarning = getMessage(config.getString("messageRpWarning", "{\"text\":\"\"}"));
    }

    @Override
    public NamespacedKey selectCameraOverlay(Player player) {
        return cameraOverlay;
    }

    @Override
    public void sendChat(Player player) {
        player.sendMessage(messageWelcome);
    }

    public void sendRpWarning(Player player) {
        player.sendMessage(messageRpWarning);
    }
}
