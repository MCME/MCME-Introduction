package com.mcmiddleearth.introduction.paper.rooms;

import com.mcmiddleearth.architect.serverResoucePack.RpManager;
import com.mcmiddleearth.architect.serverResoucePack.RpPlayerData;
import com.mcmiddleearth.introduction.paper.IntroductionPlugin;
import com.mcmiddleearth.introduction.paper.confirmData.ConfirmDataManager;
import com.mcmiddleearth.introduction.paper.flow.FlowEvents;
import com.mcmiddleearth.introduction.paper.flow.FlowEvents.Exit;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

public class SecondRoom extends Room {

    /**
     * Which compatibility warning a client gets: each has its own picture in config.yml.
     */
    public enum Warning {
        /** A vanilla client on a version that is not supported: overlayWarningVersion. */
        VERSION,
        /** Forge, NeoForge, LiteLoader or OptiFine: overlayWarningModded. */
        MODDED,
        /** Fabric with MCME's marker mod, on a version that is not supported: overlayWarningMcme. */
        MCME,
        /** Fabric without MCME's marker mod: overlayWarningMissingMod. */
        MISSING_MOD;

        /** The warning's name in the flow log. */
        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    // Where the room reads a player's client version (ViaVersion) and whether the client has MCME's
    // marker mod (MCME-Architect). Tests replace them: neither plugin runs in tests.
    static Function<Player, ProtocolVersion> clientVersion = player -> Via.getAPI().getPlayerProtocolVersion(player);
    static Predicate<Player> mcmeMarker = RpManager::isSodiumClient;

    private final NamespacedKey overlayWarningVersion, overlayWarningModded, overlayWarningMcme, overlayWarningMissingMod;
    private final Component messageUnsupportedVanilla, messageShaders, messageOptifine, messageUnsupportedModded,
                            messageManualMods, messageRunInstaller, messageConfirmIgnore;

    // Client versions that get no compatibility warning, from supportedVersions in config.yml.
    private final List<String> supportedVersions;

    private final Map<UUID, BukkitTask> reminderTasks = new HashMap<>();

    public SecondRoom(ConfigurationSection config, ConfigurationSection locationConfig) {
        super(config, locationConfig);
        this.overlayWarningVersion = NamespacedKey.fromString(Objects.requireNonNull(config.getString("overlayWarningVersion")));
        this.overlayWarningModded = NamespacedKey.fromString(Objects.requireNonNull(config.getString("overlayWarningModded")));
        this.overlayWarningMcme = NamespacedKey.fromString(Objects.requireNonNull(config.getString("overlayWarningMcme")));
        this.overlayWarningMissingMod = NamespacedKey.fromString(Objects.requireNonNull(config.getString("overlayWarningMissingMod")));
        overlays.add(overlayWarningVersion);
        overlays.add(overlayWarningMcme);
        overlays.add(overlayWarningMissingMod);
        overlays.add(overlayWarningModded);
        // Without a list, only the server's own version is supported.
        List<String> configuredVersions = IntroductionPlugin.getInstance().getConfig().getStringList("supportedVersions");
        supportedVersions = (configuredVersions.isEmpty() ? List.of(Bukkit.getMinecraftVersion()) : List.copyOf(configuredVersions));
        messageUnsupportedVanilla = getVersionMessage(config, "messageUnsupportedVanilla");
        messageShaders = getVersionMessage(config, "messageShaders");
        messageOptifine = getVersionMessage(config, "messageOptifine");
        messageUnsupportedModded = getVersionMessage(config, "messageUnsupportedModded");
        messageManualMods = getVersionMessage(config, "messageManualMods");
        messageRunInstaller = getVersionMessage(config, "messageRunInstaller");
        messageConfirmIgnore = getVersionMessage(config, "messageConfirmIgnore");
    }

    /**
     * Loads a message, with &lt;supported_versions&gt; in it replaced by the supported versions.
     */
    private Component getVersionMessage(ConfigurationSection config, String key) {
        return getMessage(config.getString(key, "{\"text\":\"\"}")
                .replace("<supported_versions>", String.join(", ", supportedVersions)));
    }

    @Override
    public void teleport(Player player, Room previous, Room next, Exit how) {
        super.teleport(player, previous, next, how);
        long reminederDelay = IntroductionPlugin.getInstance().getConfig().getLong("reminderPeriod",600);
        Bukkit.getScheduler().runTaskLater(IntroductionPlugin.getInstance(),
                                           ()-> startReminderTask(player), reminederDelay);
    }

    @Override
    public boolean isSkipped(Player player) {
        ConfirmDataManager dataManager = IntroductionPlugin.getInstance().getConfirmDataManager();
        if(isCompatible(player)) {
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

    /**
     * Whether the client needs nothing from the player: a supported version, and vanilla or
     * Fabric with MCME's marker mod.
     */
    public boolean isCompatible(Player player) {
        return isSupportedVersion(player)
                && ((!isForge(player) && !isFabric(player))
                    || (isFabric(player) && isMcmeMarker(player)));
    }

    @Override
    public NamespacedKey selectCameraOverlay(Player player) {
        return switch(getWarning(player)) {
            case MODDED -> overlayWarningModded;
            case MCME -> overlayWarningMcme;
            case MISSING_MOD -> overlayWarningMissingMod;
            case VERSION -> overlayWarningVersion;
        };
    }

    public Warning getWarning(Player player) {
        if(isForge(player)) {
            return Warning.MODDED;
        } else if(isFabric(player)) {
            return (isMcmeMarker(player) ? Warning.MCME : Warning.MISSING_MOD);
        } else {
            return Warning.VERSION;
        }
    }

    @Override
    public String getFlowName() {
        return "compatibility";
    }

    public List<String> getSupportedVersions() {
        return supportedVersions;
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
        return isSupportedVersion(getClientVersion(player));
    }

    public ProtocolVersion getClientVersion(Player player) {
        return clientVersion.apply(player);
    }

    /**
     * Whether a client version is supported. Releases that share a protocol cannot be told
     * apart, so a listed version covers all of them: "26.1.2" also accepts 26.1 and 26.1.1,
     * whose shared protocol ViaVersion names "26.1-26.1.2".
     */
    public boolean isSupportedVersion(ProtocolVersion clientVersion) {
        return supportedVersions.stream().anyMatch(clientVersion.getIncludedVersions()::contains);
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
        return mcmeMarker.test(player);
    }

    private void sendMessage(Player player, Component message) {
        player.sendMessage(message);
        //player.sendActionBar(Component.text("1.21.4         ").append(Component.text(".").color(NamedTextColor.BLACK)));
    }

    public void startReminderTask(Player player) {
//Logger.getGlobal().info("start reminder task for "+player.getName());
        if(isSkipped(player)) {
            return;
        }
        cancelReminderTask(player);
        long reminderPeriod =  IntroductionPlugin.getInstance().getConfig().getLong("reminderPeriod",600);
        FlowEvents.reminder(player, getWarning(player));
        handleOverride(player, true);
        startStopReminderTask(player);
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
//Logger.getGlobal().info("Reminder task running for "+player.getName());
                if (isSkipped(player) || !player.isOnline() || IntroductionPlugin.getInstance().isInsideRoom(player)) {
//Logger.getGlobal().info("cancel reminder task");
                    cancel();
                    return;
                }
                if (isRpLoaded(rpPlayerData.apply(player)) ){ //!IntroductionPlugin.getInstance().isInsideRoom(player)) {
                    FlowEvents.reminder(player, getWarning(player));
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
                if(!player.isOnline() || IntroductionPlugin.getInstance().isInsideRoom(player)) {
                    cancel();
                    return;
                }
                attempts++;
                RpPlayerData data = rpPlayerData.apply(player);
                if(rpLoadTime == -1) {
                    if (isRpLoaded(rpPlayerData.apply(player))) {
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
