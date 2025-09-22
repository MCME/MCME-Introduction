package com.mcmiddleearth.introduction.rooms;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.json.JSONComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.Objects;

public class SecondRoom extends Room {

    private final NamespacedKey overlayWarningVersion, overlayWarningModded, overlayWarningMcme, overlayWarningMissingMod;
    private final Component messageUnsupportedVanilla, messageShaders, messageOptifine, messageUnsupportedModded, messageManualMods;

    public SecondRoom(ConfigurationSection config) {
        super(config);
        this.overlayWarningVersion = NamespacedKey.fromString(Objects.requireNonNull(config.getString("overlayWarningVersion")));
        this.overlayWarningModded = NamespacedKey.fromString(Objects.requireNonNull(config.getString("overlayWarningModded")));
        this.overlayWarningMcme = NamespacedKey.fromString(Objects.requireNonNull(config.getString("overlayWarningMcme")));
        this.overlayWarningMissingMod = NamespacedKey.fromString(Objects.requireNonNull(config.getString("overlayWarningMissingMod")));
        messageUnsupportedVanilla = JSONComponentSerializer.json()
                .deserialize(Objects.requireNonNull(config.getString("messageUnsupportedVanilla")));
        messageShaders = JSONComponentSerializer.json()
                .deserialize(Objects.requireNonNull(config.getString("messageShaders")));
        messageOptifine = JSONComponentSerializer.json()
                .deserialize(Objects.requireNonNull(config.getString("messageOptifine")));
        messageUnsupportedModded = JSONComponentSerializer.json()
                .deserialize(Objects.requireNonNull(config.getString("messageUnsupportedModded")));
        messageManualMods = JSONComponentSerializer.json()
                .deserialize(Objects.requireNonNull(config.getString("messageManualMods")));
    }

    @Override
    public boolean isSkipped(Player player) {
        return super.isSkipped(player);
    }

    @Override
    public NamespacedKey selectCameraOverlay(Player player) {
        return null;
    }
}
