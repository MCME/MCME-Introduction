package com.mcmiddleearth.introduction.paper.rooms;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.mcmiddleearth.architect.serverResoucePack.RpManager;
import com.mcmiddleearth.architect.serverResoucePack.RpPlayerData;
import com.mcmiddleearth.introduction.paper.IntroductionPlugin;
import com.mcmiddleearth.introduction.paper.confirmData.ConfirmDataManager;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

public class SecondRoom extends Room {

    private static final ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();

    private final NamespacedKey overlayWarningVersion, overlayWarningModded, overlayWarningMcme, overlayWarningMissingMod;
    private final Component messageUnsupportedVanilla, messageShaders, messageOptifine, messageUnsupportedModded,
                            messageManualMods, messageRunInstaller, messageConfirmIgnore;

    private final Map<UUID, BukkitTask> reminderTasks = new HashMap<>();

    public SecondRoom(ConfigurationSection config) {
        super(config);
        this.overlayWarningVersion = NamespacedKey.fromString(Objects.requireNonNull(config.getString("overlayWarningVersion")));
        this.overlayWarningModded = NamespacedKey.fromString(Objects.requireNonNull(config.getString("overlayWarningModded")));
        this.overlayWarningMcme = NamespacedKey.fromString(Objects.requireNonNull(config.getString("overlayWarningMcme")));
        this.overlayWarningMissingMod = NamespacedKey.fromString(Objects.requireNonNull(config.getString("overlayWarningMissingMod")));
        messageUnsupportedVanilla = getMessage(config.getString("messageUnsupportedVanilla", "{\"text\":\"\"}"));
        messageShaders = getMessage(config.getString("messageShaders", "{\"text\":\"\"}"));
        messageOptifine = getMessage(config.getString("messageOptifine", "{\"text\":\"\"}"));
        messageUnsupportedModded = getMessage(config.getString("messageUnsupportedModded", "{\"text\":\"\"}"));
        messageManualMods = getMessage(config.getString("messageManualMods", "{\"text\":\"\"}"));
        messageRunInstaller = getMessage(config.getString("messageRunInstaller", "{\"text\":\"\"}"));
        messageConfirmIgnore = getMessage(config.getString("messageConfirmIgnore", "{\"text\":\"\"}"));
    }

    @Override
    public boolean isSkipped(Player player) {
        ConfirmDataManager dataManager = IntroductionPlugin.getInstance().getConfirmDataManager();
        if(isSupportedVersion(player)
                    && ((!isForge(player) && !isFabric(player))
                        || (isFabric(player) && isMcmeMarker(player)))) {
            //vanilla or mcme sodium installer
//Logger.getGlobal().info("vanilla or mcme sodium installer: forge: "+isForge(player)+" fabric: "+isFabric(player)+" marker: "+isMcmeMarker(player));
//Logger.getGlobal().info("Client Brand: "+player.getClientBrandName());
            return true;
        } else if(isSupportedVersion(player) && isForge(player)) {
            //forge supported version, possibly optifine
//Logger.getGlobal().info("forge suported: confirm");
            return dataManager.hasConfirmedOptifine(player.getUniqueId());
        } else {
            //unsupported version or fabric without mcme marker
//Logger.getGlobal().info("fabrick without marker");
            return dataManager.hasConfirmedIgnore(player.getUniqueId());
        }
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
                    message = message.append(messageConfirmIgnore);
                }
            } else {
                message = message.append(messageUnsupportedVanilla);
                message = message.append(messageConfirmIgnore);
            }
            sendMessage(player, message);
        //}, 0, 20));
    }

    public boolean isSupportedVersion(Player player) {
//Logger.getGlobal().info(Bukkit.getServer().getMinecraftVersion());
        int protocolId = Via.getAPI().getPlayerVersion(player);
        ProtocolVersion version = ProtocolVersion.getProtocol(protocolId);
//Logger.getGlobal().info(version.getName());
        return Bukkit.getServer().getMinecraftVersion().equals(version.getName());
    }

    public boolean isForge(Player player) {
        return player.getClientBrandName()!=null
                && (player.getClientBrandName().toLowerCase().contains("forge")
                    || player.getClientBrandName().toLowerCase().contains("neoforge")
                    || player.getClientBrandName().toLowerCase().contains("liteloader")
                    || player.getClientBrandName().toLowerCase().contains("optifine"));
    }

    public boolean isFabric(Player player) {
        return player.getClientBrandName()!=null && player.getClientBrandName().toLowerCase().contains("fabric");
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

    public void startReminderTask(Player player) {
        cancelReminderTask(player);
        long reminderPeriod =  IntroductionPlugin.getInstance().getConfig().getLong("reminderPeriod",600);
        handleOverride(player, true);
        startStopReminderTask(player);
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (isSkipped(player) || !player.isOnline()) {
                    cancel();
                    return;
                }
                if (isRpLoaded(RpManager.getPlayerData(player))) {
                    handleOverride(player, true);
                    startStopReminderTask(player);
                }
            }
        }.runTaskTimer(IntroductionPlugin.getInstance(), reminderPeriod, reminderPeriod);
        reminderTasks.put(player.getUniqueId(), task);
    }

    public void cancelReminderTask(Player player) {
        BukkitTask task = reminderTasks.get(player.getUniqueId());
        if(task != null) {
            if(!task.isCancelled()) {
                task.cancel();
            }
            reminderTasks.remove(player.getUniqueId());
        }
    }

    public void startStopReminderTask(Player player) {
        new BukkitRunnable() {
            int rpLoadTime = -1;
            int attempts = 0;
            @Override
            public void run() {
                if(!player.isOnline()) {
                    cancel();
                    return;
                }
                attempts++;
                RpPlayerData data = RpManager.getPlayerData(player);
                if(rpLoadTime == -1) {
                    if (isRpLoaded(RpManager.getPlayerData(player))) {
                        rpLoadTime = Bukkit.getServer().getCurrentTick();
                    }
                } else if(isRpFail(data)) {
                    cancel();
                } else if(attempts > 1000) {
                    cancel();
                } else {
                    if(Bukkit.getServer().getCurrentTick() > rpLoadTime + IntroductionPlugin.getInstance().getConfig().getLong("reminderDuration", 100)) {
                        handleOverride(player, false);
                        cancel();
                    }
                }
            }
        }.runTaskTimer(IntroductionPlugin.getInstance(), 0L, 20L);
    }

}
