package com.mcmiddleearth.introduction.paper.rooms;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.mcmiddleearth.architect.serverResoucePack.RpManager;
import com.mcmiddleearth.architect.serverResoucePack.RpPlayerData;
import com.mcmiddleearth.architect.serverResoucePack.RpPlayerStatus;
import com.mcmiddleearth.introduction.paper.IntroductionChain;
import com.mcmiddleearth.introduction.paper.IntroductionPlugin;
import com.mcmiddleearth.introduction.paper.listener.ChatPacketListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.json.JSONComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.advancement.Advancement;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.EquippableComponent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.*;
import java.util.logging.Logger;

public abstract class Room {

    private Room next;
    private final Location pos1, pos2, tpTarget, playerLocation;
    private final float yaw, pitch;
    private final Component tpTransitionTitle, messageActionBar;
    private final String[] tpTransitionTimes;
    private final boolean canIgnore;
    private final long actionBarPeriod, actionBarDelay;
    private final String advancementKey, advancementDisplay;
    private final Advancement advancement;

    private final Map<UUID, ItemStack> playerItems = new HashMap<>();
    private final Map<UUID, GameMode> playerGamemodes = new HashMap<>();
    private final Set<UUID> fixedPlayers = new HashSet<>();
    private final Map<UUID, Integer> playerEnterTimes = new HashMap<>();

    private final Set<UUID> awaitingTeleport = new HashSet<>();

    private final Map<UUID, BukkitTask> actionBarTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> overlayTasks = new HashMap<>();

    // Resource pack related messages (loaded from config JSON)
    private final Component rpFailureMessage;
    private final Component rpTimeoutMessage;
    private final Component rpDeclinedMessage;
    private final Component rpFailedDownloadMessage;
    private final Component rpFailedReloadMessage;
    private final Component rpInvalidUrlMessage;

    public Room(ConfigurationSection config) {
        String worldName = config.getString("world");
        World world = (worldName != null ? Bukkit.getWorld(worldName) : null);
        this.pos1 = getLocation(world, "pos1", config);
        this.pos2 = getLocation(world, "pos2", config);
        this.playerLocation = getLocation(world, "playerLocation", config);
        if(pos1.getX() > pos2.getX()) {
            double temp = pos2.getX();
            pos2.setX(pos1.getX());
            pos1.setX(temp);
        }
            if(pos1.getY() > pos2.getY()) {
            double temp = pos2.getY();
            pos2.setY(pos1.getY());
            pos1.setY(temp);
        }
        if(pos1.getZ() > pos2.getZ()) {
            double temp = pos2.getZ();
            pos2.setZ(pos1.getZ());
            pos1.setZ(temp);
        }
        this.yaw = (float)config.getDouble("yaw", 0);
        this.pitch = (float)config.getDouble("pitch", 0);
        ConfigurationSection targetConfig = config.getConfigurationSection("tpTarget");
        this.tpTarget = getLocation(Bukkit.getWorld(targetConfig.getString("world", world.getName())),
                                "pos", targetConfig);
        this.actionBarPeriod = config.getLong("actionBarPeriod", 40);
        this.actionBarDelay = config.getLong("actionBarDelay", 40);
        this.tpTransitionTitle = getMessage(config.getString("tpTransitionComponent", "{\"text\":\"\"}"));
        this.messageActionBar = getMessage(config.getString("messageActionBar", "{\"text\":\"\"}"));
        this.advancementKey = config.getString("advancementKey", "mcme:intro");
        this.advancementDisplay = config.getString("advancementDisplay", Room.TestDisplay.getAdvancementTestDisplay);
        this.tpTransitionTimes = Objects.requireNonNull(config.getString("tpTransitionTimes")).split(" ");
        this.canIgnore = config.getBoolean("canIgnore", false);
//Logger.getGlobal().info("Load: "+this);
        if(!advancementDisplay.equals(Room.TestDisplay.getAdvancementTestDisplay)) {
//Logger.getGlobal().info("Load: "+advancementKey);
            advancement = Bukkit.getUnsafe().loadAdvancement(NamespacedKey.fromString(advancementKey),
                    "{\"display\":" + advancementDisplay + ", \"criteria\":{\"manual\":{\"trigger\":\"minecraft:impossible\"}}}");
        } else {
            advancement = null;
        }

        // Load resource-pack related messages from the room config or fall back to top-level rpMessages in config.yml
        String rpFail = getConfigStringWithFallback(config, "messageRpWarning");
        if(rpFail == null) rpFail = getConfigStringWithFallback(config, "messageRpFailed");
        this.rpFailureMessage = getMessage(rpFail);
        String rpTimeout = getConfigStringWithFallback(config, "messageRpTimeout");
        this.rpTimeoutMessage = getMessage(rpTimeout);

        // Specific failure reasons (optional, fallback to general failure message)
        Component declined = getMessage(getConfigStringWithFallback(config, "messageRpDeclined"));
        this.rpDeclinedMessage = (declined.equals(Component.text("")) ? rpFailureMessage : declined);
        Component failedDownload = getMessage(getConfigStringWithFallback(config, "messageRpFailedDownload"));
        this.rpFailedDownloadMessage = (failedDownload.equals(Component.text("")) ? rpFailureMessage : failedDownload);
        Component failedReload = getMessage(getConfigStringWithFallback(config, "messageRpFailedReload"));
        this.rpFailedReloadMessage = (failedReload.equals(Component.text("")) ? rpFailureMessage : failedReload);
        Component invalidUrl = getMessage(getConfigStringWithFallback(config, "messageRpInvalidUrl"));
        this.rpInvalidUrlMessage = (invalidUrl.equals(Component.text("")) ? rpFailureMessage : invalidUrl);
    }

    public void unload() {
//Logger.getGlobal().info("Unload: "+this);
        if(advancement != null) {
//Logger.getGlobal().info("Unload: "+advancementKey);
            Bukkit.getUnsafe().removeAdvancement(NamespacedKey.fromString(advancementKey));
//Logger.getGlobal().info("intro: "+Bukkit.getAdvancement(NamespacedKey.fromString(advancementKey)));
        }
    }

    public void setNextRoom(Room room) {
        next = room;
    }

    public Room getNext() {
        return next;
    }

    public boolean isSkipped(Player player) {
        //default: do not skip
        return false;
    }

    public boolean isInside(Location location) {
        return pos1!=null && pos1.getWorld()!=null && pos1.getWorld().equals(location.getWorld())
                && pos1.getX()<location.getX() && pos2.getX()>location.getX()
                && pos1.getY()<location.getY() && pos2.getY()>location.getY()
                && pos1.getZ()<location.getZ() && pos2.getZ()>location.getZ();
    }

    public boolean handleEnter(Player player) {
        if(!playerItems.containsKey(player.getUniqueId())) {
            playerItems.put(player.getUniqueId(), player.getInventory().getItem(EquipmentSlot.HEAD));
            setCameraOverlay(player);
            //sendChat(player); -> moved inside setCameraOverlay
            sendActionBar(player);
            sendAdvancement(player);
            silence(player);
            playerGamemodes.put(player.getUniqueId(), player.getGameMode());
            player.setGameMode(GameMode.SPECTATOR);
            Location loc = player.getLocation();
            /*loc.setPitch(pitch);
            loc.setYaw(yaw);
            RoomListener.allowTeleportOut(player);
            player.teleport(loc);*/
//Logger.getGlobal().info("Teleport! "+player.getName()+", "+yaw+", "+pitch);
            return true;
        } else {
            if(!playerGamemodes.containsKey(player.getUniqueId())) {
                sendChat(player);
                sendActionBar(player);
                sendAdvancement(player);
                silence(player);
                playerGamemodes.put(player.getUniqueId(), player.getGameMode());
                player.setGameMode(GameMode.SPECTATOR);
                /*Location loc = player.getLocation();
                loc.setPitch(pitch);
                loc.setYaw(yaw);
                RoomListener.allowTeleportOut(player);
                player.teleport(loc);*/
//Logger.getGlobal().info("Teleport! "+player.getName()+", "+yaw+", "+pitch);
                return true;
            }
        }
        return false;
    }

    public void handleExit(Player player) {
//Logger.getGlobal().info("Room: "+this.toString()+" handleExit");
//Logger.getGlobal().info(fixedPlayers.contains(player.getUniqueId())+" && "+playerItems.containsKey(player.getUniqueId()));
        if(playerGamemodes.containsKey(player.getUniqueId())) {
            //if(!fixedPlayers.contains(player.getUniqueId())) {
                removeCameraOverlay(player);
                playerItems.remove(player.getUniqueId());
            //}
            player.setGameMode(playerGamemodes.get(player.getUniqueId()));
            playerGamemodes.remove(player.getUniqueId());
            stopActionBar(player);
            unSilence(player);
        }
    }

    public void handleOverride(Player player, boolean override) {
        if(override) {
            fixedPlayers.add(player.getUniqueId());
            if(!playerItems.containsKey(player.getUniqueId())) {
                playerItems.put(player.getUniqueId(), player.getInventory().getItem(EquipmentSlot.HEAD));
                setCameraOverlay(player);
                //sendChat(player); -> moved inside setCameraOverlay
            }
        } else {
            fixedPlayers.remove(player.getUniqueId());
            if(playerItems.containsKey(player.getUniqueId()) && !isInside(player.getLocation())) {
                removeCameraOverlay(player);
                playerItems.remove(player.getUniqueId());
            }
        }
    }

    public boolean isFixed(Player player) {
        return fixedPlayers.contains(player.getUniqueId());
    }

    public void teleport(Player player, Room previous, Room next) {
//Logger.getGlobal().info("SET awaiting Teleport");
        previous.awaitingTeleport.add(player.getUniqueId());
//Logger.getGlobal().info("test is awaiting: "+isAwaitingTeleport(player));
//Logger.getGlobal().info("Second test: "+awaitingTeleport.contains(player.getUniqueId()));
        Title.Times times = Title.Times.times(Duration.ofMillis(Integer.parseInt(tpTransitionTimes[0])*50L),
                                        Duration.ofMillis(Integer.parseInt(tpTransitionTimes[1])*50L),
                                        Duration.ofMillis(Integer.parseInt(tpTransitionTimes[2])*50L));
        Title black = Title.title(tpTransitionTitle,Component.empty(),times);
        player.showTitle(black);
        Bukkit.getScheduler().runTaskLater(IntroductionPlugin.getInstance(), () -> {
            previous.handleExit(player);
            //RoomListener.allowTeleportOut(player);
            player.teleport(tpTarget);
            previous.awaitingTeleport.remove(player.getUniqueId());
//Logger.getGlobal().info("UNSET awaiting teleport");
            if(next != null) {
                next.handleEnter(player);
            } else {
                IntroductionChain.startChain(player);
            }
                },Long.parseLong(tpTransitionTimes[0]));
    }

    @SuppressWarnings("UnstableApiUsage")
    public void setCameraOverlay(Player player) {
        ItemStack overlayItem = new ItemStack(Material.STONE);
        ItemMeta meta = overlayItem.getItemMeta();
        EquippableComponent equip = meta.getEquippable();
        equip.setSlot(EquipmentSlot.HEAD);
        NamespacedKey overlay = selectCameraOverlay(player);
//Logger.getGlobal().info("Overlay: "+overlay.asString());
        equip.setCameraOverlay(overlay);
        meta.setEquippable(equip);
        overlayItem.setItemMeta(meta);
        startOverlayTask(player,overlayItem);
    }

    public void removeCameraOverlay(Player player) {
        //player.getInventory().setItem(EquipmentSlot.HEAD, new ItemStack(Material.AIR));
        player.getInventory().setItem(EquipmentSlot.HEAD, playerItems.get(player.getUniqueId()));
        stopOverlayTask(player);
    }

    public abstract NamespacedKey selectCameraOverlay(Player player);

    private void startOverlayTask(Player player, ItemStack overlayItem) {
        stopOverlayTask(player);
        BukkitTask task = new BukkitRunnable() {
            int attempts = 0;
            @Override
            public void run() {
//Logger.getGlobal().info("attempt: "+attempts+"  "+RpManager.getPlayerData(player).getCurrentRpStatus());
                RpPlayerData data = RpManager.getPlayerData(player);
                if(isRpLoaded(data)) {
                    player.getInventory().setItem(EquipmentSlot.HEAD, overlayItem);
                    sendChat(player);
                    cancel();
                }
                else if(isRpFail(data)) {
                    stopOverlayTask(player);
                    // pick message based on exact failure reason
                    RpPlayerStatus status = data.getCurrentRpStatus();
                    Component msg;
                    if(status.equals(RpPlayerStatus.DECLINED)) {
                        msg = rpDeclinedMessage;
                    } else if(status.equals(RpPlayerStatus.FAILED_DOWNLOAD)) {
                        msg = rpFailedDownloadMessage;
                    } else if(status.equals(RpPlayerStatus.FAILED_RELOAD)) {
                        msg = rpFailedReloadMessage;
                    } else if(status.equals(RpPlayerStatus.INVALID_URL)) {
                        msg = rpInvalidUrlMessage;
                    } else {
                        msg = rpFailureMessage;
                    }
                    if(msg != null && !msg.equals(Component.text(""))) {
                        IntroductionPlugin.sendErrorMessage(player, msg);
                    } else {
                        IntroductionPlugin.sendErrorMessage(player, "Could not send overlay as your resource pack failed to load.");
                    }
                }
                attempts++;
                if(attempts > 500) {
                    if(rpTimeoutMessage != null && !rpTimeoutMessage.equals(Component.text(""))) {
                        IntroductionPlugin.sendErrorMessage(player, rpTimeoutMessage);
                    } else {
                        IntroductionPlugin.sendErrorMessage(player, "Could not send overlay as your resource pack doesn't seem to load: Timed out.");
                    }
                    stopOverlayTask(player);
                }
            }
        }.runTaskTimer(IntroductionPlugin.getInstance(), 0L, 10L);
        overlayTasks.put(player.getUniqueId(), task);
    }
    public boolean isRpLoaded(RpPlayerData data) {
        return data.getCurrentRpStatus().equals(RpPlayerStatus.SUCCESSFULLY_LOADED)
                || data.getLastRpStatus().equals(RpPlayerStatus.SUCCESSFULLY_LOADED)
                && (   data.getCurrentRpStatus().equals(RpPlayerStatus.ACCEPTED)
                    || data.getCurrentRpStatus().equals(RpPlayerStatus.DOWNLOADED)
                    || data.getCurrentRpStatus().equals(RpPlayerStatus.SENT));
    }

    public boolean isRpFail(RpPlayerData data) {
        return data.getCurrentRpStatus().equals(RpPlayerStatus.DECLINED)
                || data.getCurrentRpStatus().equals(RpPlayerStatus.FAILED_DOWNLOAD)
                || data.getCurrentRpStatus().equals(RpPlayerStatus.FAILED_RELOAD)
                || data.getCurrentRpStatus().equals(RpPlayerStatus.INVALID_URL);
    }

    private void stopOverlayTask(Player player) {
        BukkitTask task = overlayTasks.get(player.getUniqueId());
        if(task!= null) {
            task.cancel();
            overlayTasks.remove(player.getUniqueId());
        }
    }

    public void sendChat(Player player) {
    }

    private void sendActionBar(Player player) {
        stopActionBar(player);
//Logger.getGlobal().info("run task");
        actionBarTasks.put(player.getUniqueId(), Bukkit.getScheduler().runTaskTimer(IntroductionPlugin.getInstance(), () -> {
            player.sendActionBar(messageActionBar);
        },actionBarDelay, actionBarPeriod));
    }

    private void stopActionBar(Player player) {
        BukkitTask task = actionBarTasks.get(player.getUniqueId());
//Logger.getGlobal().info("cancel: "+task);
        if(task!= null) {
//Logger.getGlobal().info("cancel task");
            task.cancel();
            actionBarTasks.remove(player.getUniqueId());
        }
    }

    public boolean canIgnore() {
        return canIgnore;
    }

    public Location getPlayerLocation() {
        return playerLocation;
    }

    public boolean isAwaitingTeleport(Player player) {
//Logger.getGlobal().info("check in room "+this.toString()+" for uuid: "+player.getUniqueId() +" "+awaitingTeleport.contains(player.getUniqueId()));
//awaitingTeleport.forEach(uuid -> Logger.getGlobal().info(" "+uuid));
        return awaitingTeleport.contains(player.getUniqueId());
    }

    // changed visibility so subclasses (FirstRoom) can parse locations from their own subsections
    protected Location getLocation(World world, String key, ConfigurationSection config) {
        String[] location = Objects.requireNonNull(config.getString(key)).split(" ");
        if(location.length == 3) {
            return new Location(world, Double.parseDouble(location[0]),
                    Double.parseDouble(location[1]),
                    Double.parseDouble(location[2]));
        } else {
            return new Location(world, Double.parseDouble(location[0]),
                    Double.parseDouble(location[1]),
                    Double.parseDouble(location[2]),
                    Float.parseFloat(location[3]),
                    Float.parseFloat(location[4]));
        }
    }

    public static Component getMessage(String code) {
        if(code == null) {
            code = "";
        }
        try {
            Component component = JSONComponentSerializer.json().deserialize(code);
            return component.insertion(IntroductionPlugin.CHANNEL);
        } catch (Exception ex) {
            ex.printStackTrace();
            return Component.text(code);
        }
    }

    public void showPlayers() {
        playerItems.forEach((uuid,item)->Logger.getGlobal().info(uuid+" - "+item));
    }

    public void sendAdvancement(Player player) {
//Logger.getGlobal().info("Send Advancement");
        if(advancement != null) {
//Logger.getGlobal().info("Send Advancement"+advancementDisplay);
            player.getAdvancementProgress(advancement).awardCriteria("manual");
            Bukkit.getScheduler().runTaskLater(IntroductionPlugin.getInstance(), () -> {
                player.getAdvancementProgress(advancement).revokeCriteria("manual");
            }, 200);
        }
    }

    public void silence(Player player) {
        ChatPacketListener.silence(player);
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("enter");
        out.writeUTF(player.getUniqueId().toString());
        player.sendPluginMessage(IntroductionPlugin.getInstance(),
                IntroductionPlugin.CHANNEL,
                out.toByteArray());
    }

    public void unSilence(Player player) {
        ChatPacketListener.unSilence(player);
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("exit");
        out.writeUTF(player.getUniqueId().toString());
        player.sendPluginMessage(IntroductionPlugin.getInstance(),
                IntroductionPlugin.CHANNEL,
                out.toByteArray());
    }

    private static String getConfigStringWithFallback(ConfigurationSection roomConfig, String key) {
        if(roomConfig == null) return null;
        String val = roomConfig.getString(key, null);
        if(val != null) return val;
        IntroductionPlugin plugin = IntroductionPlugin.getInstance();
        if(plugin == null) return null;
        ConfigurationSection rp = plugin.getConfig().getConfigurationSection("rpMessages");
        if(rp == null) return null;
        return rp.getString(key, null);
    }

    public static class TestDisplay {
        public static final String getAdvancementTestDisplay = "{\"icon\":{\"id\":\"minecraft:stone\"},\"title\":{\"text\":\"test\"},\"description\":{\"text\":\"testest\"}}";
    }

    public void setPlayerEnterTime(UUID player) {
        playerEnterTimes.put(player, Bukkit.getCurrentTick());
    }

    public int getPlayerEnterTime(UUID player) {
        return playerEnterTimes.getOrDefault(player, Integer.MAX_VALUE);
    }
}
