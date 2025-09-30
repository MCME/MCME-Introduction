package com.mcmiddleearth.introduction.rooms;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.mcmiddleearth.architect.serverResoucePack.RpManager;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class SecondRoom extends Room {

    private static final ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();

    private final NamespacedKey overlayWarningVersion, overlayWarningModded, overlayWarningMcme, overlayWarningMissingMod;
    private final Component messageUnsupportedVanilla, messageShaders, messageOptifine, messageUnsupportedModded, messageManualMods, messageRunInstaller;

    private final Map<UUID, BukkitTask> tasks = new HashMap<>();

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
        //tasks.put(player.getUniqueId(),Bukkit.getScheduler().runTaskTimer(IntroductionPlugin.getInstance(), () -> {
        //    sendMessage(player, Component.text(".\n.\n.\n.\n.\n.\n.\n.\n.\n.\n.\n.\n.\n.\n.\n.\n.\n").color(NamedTextColor.BLACK));
            Component message = Component.empty();
            if (isForge(player)) {
                if (!isSupportedVersion(player)) {
                    message = message.append(messageUnsupportedModded).append(Component.text("\n"));
                }
                message = message.append(messageShaders).append(Component.text("\n"));
                message = message.append(messageOptifine);
            } else if (isFabric(player)) {
                if (isMcmeMarker(player)) {
                    message = message.append(messageRunInstaller);
                } else {
                    if (!isSupportedVersion(player)) {
                        message = message.append(messageUnsupportedModded).append(Component.text("\n"));
                    }
                    message = message.append(messageShaders).append(Component.text("\n"));
                    message = message.append(messageManualMods);
                }
            } else {
                message = message.append(messageUnsupportedVanilla);
            }
            sendMessage(player, message);
        //}, 0, 20));
    }

    @Override
    public void handleExit(Player player) {
        super.handleExit(player);
        BukkitTask task = tasks.get(player.getUniqueId());
//Logger.getGlobal().info("cancel: "+task);
        if(task!= null) {
            task.cancel();
            tasks.remove(player.getUniqueId());
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

    private void sendMessage(Player player, Component message) {
        player.sendMessage(message);
        //player.sendActionBar(Component.text("1.21.4         ").append(Component.text(".").color(NamedTextColor.BLACK)));
    }

    public static void clearChat(Player player) {
        try {
            PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.CLEAR_TITLES);
            packet.getBooleans().write(0, true);
            protocolManager.sendServerPacket(player, packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
