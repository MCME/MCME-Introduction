package com.mcmiddleearth.introduction.rooms;

import com.mcmiddleearth.architect.serverResoucePack.RpManager;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.kyori.adventure.text.Component;
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
        messageUnsupportedVanilla = getMessage(config.getString("messageUnsupportedVanilla"));
        messageShaders = getMessage(config.getString("messageShaders"));
        messageOptifine = getMessage(config.getString("messageOptifine"));
        messageUnsupportedModded = getMessage(config.getString("messageUnsupportedModded"));
        messageManualMods = getMessage(config.getString("messageManualMods"));
        messageRunInstaller = getMessage(config.getString("messageRunInstaller"));
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
        if (isForge(player)) {
            if (!isSupportedVersion(player)) {
                player.sendMessage(messageUnsupportedModded);
            }
            player.sendMessage(messageShaders);
            player.sendMessage(messageOptifine);
        } else if (isFabric(player)) {
            if (isMcmeMarker(player)) {
                player.sendMessage(messageRunInstaller);
            } else {
                if (!isSupportedVersion(player)) {
                    player.sendMessage(messageUnsupportedModded);
                }
                player.sendMessage(messageShaders);
                player.sendMessage(messageManualMods);
            }
        } else {
            player.sendMessage(messageUnsupportedVanilla);
        }
    }

    public boolean isSupportedVersion(Player player) {
//Logger.getGlobal().info(Bukkit.getServer().getMinecraftVersion());
        int protocolId = Via.getAPI().getPlayerVersion(player);
        ProtocolVersion version = ProtocolVersion.getProtocol(protocolId);
//Logger.getGlobal().info(version.getName());
        return Bukkit.getServer().getMinecraftVersion().equals(version.getName());
    }

    public boolean isForge(Player player) {
        return player.getClientBrandName()!=null && player.getClientBrandName().contains("Forge");
    }

    public boolean isFabric(Player player) {
        return player.getClientBrandName()!=null && player.getClientBrandName().contains("Forge");
    }

    public boolean isMcmeMarker(Player player) {
        return RpManager.isSodiumClient(player);
    }
}
