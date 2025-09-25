package com.mcmiddleearth.introduction.rooms;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.json.JSONComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.Objects;

public class SecondRoom extends Room {

    private final NamespacedKey overlayWarningVersion, overlayWarningModded, overlayWarningMcme, overlayWarningMissingMod;
    private final Component messageUnsupportedVanilla, messageShaders, messageOptifine, messageUnsupportedModded, messageManualMods, messageRunInstaller;

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
        messageRunInstaller = JSONComponentSerializer.json()
                .deserialize(Objects.requireNonNull(config.getString("messageRunInstaller")));
    }

    @Override
    public boolean isSkipped(Player player) {
        return isSupportedVersion(player)
                    && ((!isForge(player) && !isFabric(player))
                        || (isFabric(player) && isMcmeMarker(player)));
    }

    @Override
    public NamespacedKey selectCameraOverlay(Player player) {
        if(isForge(player)) {
            return overlayWarningModded;
        } else if(isFabric(player)) {
            if(isMcmeMarker(player)) {
                return overlayWarningMcme;
            } else {
                return overlayWarningMissingMod;
            }
        } else {
            return overlayWarningVersion;
        }
    }

    @Override
    public void sendChat(Player player) {
        if(isForge(player)) {
            if(!isSupportedVersion(player)) {
                player.sendMessage(messageUnsupportedModded);
            };
            player.sendMessage(messageShaders);
            player.sendMessage(messageOptifine);
        } else if(isFabric(player)) {
            if(isMcmeMarker(player)) {
                player.sendMessage(messageRunInstaller);
            } else {
                if(!isSupportedVersion(player)) {
                    player.sendMessage(messageUnsupportedModded);
                }
                player.sendMessage(messageShaders);
                player.sendMessage(messageManualMods);
            }
        } else {
            player.sendMessage(messageUnsupportedVanilla);
        }
    }

    private boolean isSupportedVersion(Player player) {
        return Bukkit.getServer().getVersion().equals(player.getClientOption());
    }

    private boolean isForge(Player player) {
        return player.getClientBrandName()!=null && player.getClientBrandName().contains("Forge");
    }

    private boolean isFabric(Player player) {
        return player.getClientBrandName()!=null && player.getClientBrandName().contains("Forge");
    }

    private boolean isMcmeMarker(Player player) {

    }
}
