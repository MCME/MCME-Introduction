package com.mcmiddleearth.introduction.paper.rooms;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.mcmiddleearth.architect.serverResoucePack.RpManager;
import com.mcmiddleearth.architect.serverResoucePack.RpPlayerData;
import com.mcmiddleearth.architect.serverResoucePack.RpPlayerStatus;
import com.mcmiddleearth.introduction.paper.IntroductionChain;
import com.mcmiddleearth.introduction.paper.IntroductionPlugin;
import com.mcmiddleearth.introduction.paper.flow.FlowEvents;
import com.mcmiddleearth.introduction.paper.flow.FlowEvents.Exit;
import com.mcmiddleearth.introduction.paper.listener.ChatPacketListener;
import com.mcmiddleearth.introduction.paper.listener.RoomListener;
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

import javax.annotation.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Logger;

public abstract class Room {

    private Room next;
    private final Location pos1, pos2, tpTarget, playerLocation;
    private final float yaw, pitch;
    private final Component tpTransitionTitle, messageActionBar;
    private final String[] tpTransitionTimes;
    private final boolean canIgnore;
    private final long actionBarPeriod, actionBarDelay;
    private final int enterMoveDelay;
    private final int unsilenceDelay;
    private final String advancementKey, advancementDisplay;
    private final Advancement advancement;

    private final Map<UUID, ItemStack> playerItems = new HashMap<>();
    // Players whose head item the overlay item has replaced: their item is only in playerItems then.
    private final Set<UUID> overlaid = new HashSet<>();
    private final Map<UUID, GameMode> playerGamemodes = new HashMap<>();
    private final Set<UUID> fixedPlayers = new HashSet<>();
    private final Map<UUID, Integer> playerEnterTimes = new HashMap<>();
    // When each player in the room was put in it, for the flow log.
    private final Map<UUID, Instant> enteredAt = new HashMap<>();

    private final Set<UUID> awaitingTeleport = new HashSet<>();

    private final Map<UUID, BukkitTask> actionBarTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> overlayTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> unsilenceTasks = new HashMap<>();
    // The move on at the end of the black screen, for players this room teleports.
    private final Map<UUID, BukkitTask> blackScreenTasks = new HashMap<>();

    // Resource pack related messages (loaded from config JSON)
    private final Component rpFailureMessage;
    private final Component rpTimeoutMessage;
    private final Component rpDeclinedMessage;
    private final Component rpFailedDownloadMessage;
    private final Component rpFailedReloadMessage;
    private final Component rpInvalidUrlMessage;

    protected static final Set<NamespacedKey> overlays = new HashSet<>();

    // Where the rooms read a player's resource-pack status: MCME-Architect's RpManager. Tests replace
    // it, because RpManager cannot even be loaded without a running MCME-Architect.
    static Function<Player, RpPlayerData> rpPlayerData = RpManager::getPlayerData;

    public Room(ConfigurationSection config, @Nullable ConfigurationSection locationConfig) {
        String worldName = (locationConfig!=null?locationConfig.getString("world"):null);
        World world = (worldName != null ? Bukkit.getWorld(worldName) : null);
        this.pos1 = getLocation(world, "pos1", locationConfig);
        this.pos2 = getLocation(world, "pos2", locationConfig);
        this.playerLocation = getLocation(world, "playerLocation", locationConfig);
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
        this.yaw = (float)(locationConfig!=null?locationConfig.getDouble("yaw", 0):0);
        this.pitch = (float)(locationConfig!=null?locationConfig.getDouble("pitch", 0):0);
        ConfigurationSection targetConfig = (locationConfig!=null?locationConfig.getConfigurationSection("tpTarget"):null);
        if(targetConfig != null) {
            this.tpTarget = getLocation(Bukkit.getWorld(targetConfig.getString("world", (world!=null?world.getName():"world"))),
                    "pos", targetConfig);
        } else {
            this.tpTarget = getLocation(world, "tpTarget", locationConfig);
        }
        // A room's own value wins; config.yml sets the shared value at the top level.
        this.enterMoveDelay = config.getInt("roomEnterMoveDelay",
                IntroductionPlugin.getInstance().getConfig().getInt("roomEnterMoveDelay", 10));
        this.actionBarPeriod = config.getLong("actionBarPeriod", 40);
        this.actionBarDelay = config.getLong("actionBarDelay", 40);
        this.tpTransitionTitle = getMessage(config.getString("tpTransitionComponent", "{\"text\":\"\"}"));
        this.messageActionBar = getMessage(config.getString("messageActionBar", "{\"text\":\"\"}"));
        this.unsilenceDelay = config.getInt("unsilenceDelay", 20);
        this.advancementKey = config.getString("advancementKey", "mcme:intro");
        this.advancementDisplay = config.getString("advancementDisplay", Room.TestDisplay.getAdvancementTestDisplay);
        this.tpTransitionTimes = Objects.requireNonNull(config.getString("tpTransitionTimes")).split(" ");
        this.canIgnore = config.getBoolean("canIgnore", false);
//Logger.getGlobal().info("Load: "+this);
        if(!advancementDisplay.equals(Room.TestDisplay.getAdvancementTestDisplay)) {
//Logger.getGlobal().info("Load: "+advancementKey);
            advancement = IntroductionPlugin.loadAdvancement(NamespacedKey.fromString(advancementKey),
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
        // The advancement stays registered: the next Room with this key picks it up again.
        // See removeAdvancement().
        releaseHeadItems();
        // After a reload new rooms take over: this room's tasks must not run on with its players.
        // One in the black screen stays where they are, and enters the new room at their next move.
        for(Map<UUID, BukkitTask> tasks : List.of(actionBarTasks, overlayTasks, unsilenceTasks, blackScreenTasks)) {
            tasks.values().forEach(BukkitTask::cancel);
            tasks.clear();
        }
        awaitingTeleport.clear();
    }

    /**
     * Gives every player whose head item the room holds their own item back, wherever they are.
     * Called at a shutdown or a reload, after the players in the room have left it: what is left
     * are reminders, whose overlay item would stay on the head while the item it replaced is
     * forgotten.
     */
    private void releaseHeadItems() {
        for(UUID uuid : List.copyOf(playerItems.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if(player != null) {
                removeCameraOverlay(player);
            }
        }
        playerItems.clear();
        overlaid.clear();
        fixedPlayers.clear();
    }

    /**
     * Takes an overlay item off the head of a player who joins outside the rooms. Only a crash
     * leaves one there, and the item it replaced was only kept in memory, so it can't come back.
     */
    public static void removeLeftoverOverlay(Player player) {
        if(isOverlayItem(player.getInventory().getItem(EquipmentSlot.HEAD))) {
            player.getInventory().setItem(EquipmentSlot.HEAD, new ItemStack(Material.AIR));
        }
    }

    /**
     * Deletes the advancement's file, so the next start registers it anew from config.yml.
     * Paper keeps it registered until the server stops.
     */
    public void removeAdvancement() {
        if(advancement != null) {
            Bukkit.getUnsafe().removeAdvancement(NamespacedKey.fromString(advancementKey));
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
        // Moved here from the other room without walking on, by a teleport from staff say: out of
        // that room first, or its spectator mode would be the mode this room gives back.
        for(Room other : List.of(IntroductionPlugin.getInstance().getFirst(), IntroductionPlugin.getInstance().getSecond())) {
            if(other != this && other.isEntered(player)) {
                other.handleExit(player, true, Exit.LEFT);
            }
        }
        IntroductionPlugin.getInstance().getFirst().handleOverride(player, false);
        IntroductionPlugin.getInstance().getSecond().handleOverride(player, false);
        if(!playerItems.containsKey(player.getUniqueId())) {
            recordEnter(player);
            playerItems.put(player.getUniqueId(), headItemToRestore(player.getInventory().getItem(EquipmentSlot.HEAD)));
            setCameraOverlay(player, true);
            //sendChat(player); -> moved inside setCameraOverlay
            sendActionBar(player);
            sendAdvancement(player);
            // Leaving the previous room scheduled giving chat back; the player is in a room again.
            RoomListener.cancelAllUnsilenceTasks(player);
            // Tips belong after the rooms; leaving the last room starts them anew.
            IntroductionChain.interruptChain(player);
            silence(player);
            playerGamemodes.put(player.getUniqueId(), gameModeToRestore(player));
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
                recordEnter(player);
                sendChat(player);
                sendActionBar(player);
                sendAdvancement(player);
                RoomListener.cancelAllUnsilenceTasks(player);
                IntroductionChain.interruptChain(player);
                silence(player);
                playerGamemodes.put(player.getUniqueId(), gameModeToRestore(player));
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

    /**
     * The game mode the player gets back on leaving the room. A player who comes in spectator mode
     * normally has it from a room, saved there by a crash for example, so they get the server's
     * default mode instead. That includes staff who fly into a room in spectator mode.
     */
    private static GameMode gameModeToRestore(Player player) {
        return (player.getGameMode() == GameMode.SPECTATOR ? Bukkit.getDefaultGameMode() : player.getGameMode());
    }

    private void recordEnter(Player player) {
        enteredAt.put(player.getUniqueId(), Instant.now());
        FlowEvents.enter(this, player);
    }

    /**
     * Whether the player is in this room: put in it and not taken out again.
     */
    public boolean isEntered(Player player) {
        return playerGamemodes.containsKey(player.getUniqueId());
    }

    /**
     * Takes the player out of the room, if they are in it.
     *
     * @param how why the player leaves, for the flow log
     */
    public void handleExit(Player player, boolean delayUnSilence, Exit how) {
//Logger.getGlobal().info("Room: "+this.toString()+" handleExit");
//Logger.getGlobal().info(fixedPlayers.contains(player.getUniqueId())+" && "+playerItems.containsKey(player.getUniqueId()));
        if(playerGamemodes.containsKey(player.getUniqueId())) {
//Logger.getGlobal().info("EXIT");
            FlowEvents.exit(this, player, how, enteredAt.remove(player.getUniqueId()));
            //if(!fixedPlayers.contains(player.getUniqueId())) {
                removeCameraOverlay(player);
                playerItems.remove(player.getUniqueId());
            //}
            player.setGameMode(playerGamemodes.get(player.getUniqueId()));
            playerGamemodes.remove(player.getUniqueId());
            stopActionBar(player);
//Logger.getGlobal().info("unsilence delay: "+ unsilenceDelay);
            if(delayUnSilence) {
                unsilenceTasks.put(player.getUniqueId(), new BukkitRunnable() {
                    @Override
                    public void run() {
                        unSilence(player);
                    }
                }.runTaskLater(IntroductionPlugin.getInstance(), unsilenceDelay));
            } else {
                cancelUnSilence(player);
                unSilence(player);
            }
        }
    }

    public void handleOverride(Player player, boolean override) {
//Logger.getGlobal().info("Override: "+override);
        if(override) {
            fixedPlayers.add(player.getUniqueId());
            if(!playerItems.containsKey(player.getUniqueId())) {
//Logger.getGlobal().info("Not in Room, setting overlay");
                playerItems.put(player.getUniqueId(), headItemToRestore(player.getInventory().getItem(EquipmentSlot.HEAD)));
                setCameraOverlay(player, false);
                //sendChat(player); -> moved inside setCameraOverlay
            }
        } else {
            fixedPlayers.remove(player.getUniqueId());
            if(playerItems.containsKey(player.getUniqueId()) && !isInside(player.getLocation())) {
//Logger.getGlobal().info("stored player head item, removing overlay");
                removeCameraOverlay(player);
                playerItems.remove(player.getUniqueId());
            }
        }
    }

    /**
     * Decides which item to give back when the camera overlay comes off the player's head again.
     * <p>
     * Called by {@link #handleEnter} and {@link #handleOverride}, and again just before the
     * overlay item replaces what the player wears. Usually that is the player's own helmet,
     * or nothing. But an overlay item can be left on a player's head, for example when the server
     * stops while they are in a room. Handing that back would leave them wearing it for good.
     * <p>
     * A leftover is recognised by its camera overlay, so it is only caught while that overlay is
     * still one the rooms use in config.yml.
     *
     * @param currentHead what the player wears now: never null, an AIR stack when the slot is empty
     * @return the item to put back on the player's head later; AIR for a leftover overlay item
     */
    ItemStack headItemToRestore(ItemStack currentHead) {
        if(isOverlayItem(currentHead)) {
            return new ItemStack(Material.AIR);
        }
        return currentHead;
    }

    public boolean isFixed(Player player) {
        return fixedPlayers.contains(player.getUniqueId());
    }

    /**
     * Takes the player out of the previous room, after a black screen, and into the next room, or
     * on to the tips after the last room.
     *
     * @param how why the player leaves the previous room, for the flow log
     * @return false when the player is on the way out of the previous room already, such as after
     *         a double click on /confirm: the black screen and the tips must not come twice
     */
    public boolean teleport(Player player, Room previous, Room next, Exit how) {
//Logger.getGlobal().info("SET awaiting Teleport");
        if(!previous.awaitingTeleport.add(player.getUniqueId())) {
            return false;
        }
//Logger.getGlobal().info("test is awaiting: "+isAwaitingTeleport(player));
//Logger.getGlobal().info("Second test: "+awaitingTeleport.contains(player.getUniqueId()));
        Title.Times times = Title.Times.times(Duration.ofMillis(Integer.parseInt(tpTransitionTimes[0])*50L),
                                        Duration.ofMillis(Integer.parseInt(tpTransitionTimes[1])*50L),
                                        Duration.ofMillis(Integer.parseInt(tpTransitionTimes[2])*50L));
        Title black = Title.title(tpTransitionTitle,Component.empty(),times);
        player.showTitle(black);
        blackScreenTasks.put(player.getUniqueId(), Bukkit.getScheduler().runTaskLater(IntroductionPlugin.getInstance(), () -> {
            blackScreenTasks.remove(player.getUniqueId());
            if (!player.isOnline()) {
                // Quit during the black screen: quitting took the player out of the room already, and
                // they stay where they were until they come back.
                previous.awaitingTeleport.remove(player.getUniqueId());
                return;
            }
            previous.handleExit(player, true, how);
            //RoomListener.allowTeleportOut(player);
            player.teleport(tpTarget);
            previous.awaitingTeleport.remove(player.getUniqueId());
//Logger.getGlobal().info("UNSET awaiting teleport");
            if (next != null) {
                next.handleEnter(player);
            } else {
//                Bukkit.getScheduler().runTaskLater(IntroductionPlugin.getInstance(), () -> {
//Logger.getGlobal().info("Strart introduction chain");
              IntroductionChain.startChain(player);
//                }, unsilenceDelay);
            }
        },Long.parseLong(tpTransitionTimes[0])));
        return true;
    }

    /**
     * Puts the room's picture on the player's head once the resource pack has loaded.
     *
     * @param inRoom whether the player is entering the room, rather than getting a reminder;
     *               only then does the pack's result go in the flow log
     */
    @SuppressWarnings("UnstableApiUsage")
    private void setCameraOverlay(Player player, boolean inRoom) {
        ItemStack overlayItem = new ItemStack(Material.STONE);
        ItemMeta meta = overlayItem.getItemMeta();
        EquippableComponent equip = meta.getEquippable();
        equip.setSlot(EquipmentSlot.HEAD);
        NamespacedKey overlay = selectCameraOverlay(player);
//Logger.getGlobal().info("Overlay: "+overlay.asString());
        equip.setCameraOverlay(overlay);
        meta.setEquippable(equip);
        overlayItem.setItemMeta(meta);
        startOverlayTask(player,overlayItem, inRoom);
    }

    /**
     * Takes the overlay item off the player's head and gives back the item it replaced.
     * <p>
     * Outside the rooms, during a reminder, the player can change their head item meanwhile. So the
     * saved item goes back only over an overlay item. When the overlay never went on, the player
     * still has their own item, and the saved one is forgotten. When the player took the overlay
     * item off, the saved item exists only here, and goes into their inventory.
     */
    public void removeCameraOverlay(Player player) {
        UUID uuid = player.getUniqueId();
        ItemStack saved = playerItems.get(uuid);
        if(isOverlayItem(player.getInventory().getItem(EquipmentSlot.HEAD))) {
            player.getInventory().setItem(EquipmentSlot.HEAD, saved);
        } else if(overlaid.contains(uuid) && saved != null && !saved.isEmpty()) {
            player.getInventory().addItem(saved).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
        overlaid.remove(uuid);
        stopOverlayTask(player);
    }

    public abstract NamespacedKey selectCameraOverlay(Player player);

    /**
     * The room's name in the flow log: welcome or compatibility, as hub-flow names them.
     */
    public abstract String getFlowName();

    // The overlay task checks the resource pack every 10 ticks: 20 checks are 10 seconds.
    private static final int NO_PACK_CHAT_ATTEMPTS = 20;

    private void startOverlayTask(Player player, ItemStack overlayItem, boolean recordPack) {
        stopOverlayTask(player);
        Instant waitingSince = Instant.now();
        BukkitTask task = new BukkitRunnable() {
            int attempts = 0;
            boolean chatSent = false;
            @Override
            public void run() {
//Logger.getGlobal().info("attempt: "+attempts+"  "+RpManager.getPlayerData(player).getCurrentRpStatus());
                RpPlayerData data = rpPlayerData.apply(player);
                if(isRpLoaded(data)) {
                    // What the overlay item replaces comes back: during a reminder the player can
                    // have changed their head item since it was saved.
                    playerItems.put(player.getUniqueId(), headItemToRestore(player.getInventory().getItem(EquipmentSlot.HEAD)));
                    player.getInventory().setItem(EquipmentSlot.HEAD, overlayItem);
                    overlaid.add(player.getUniqueId());
                    if(!chatSent) {
                        sendChat(player);
                    }
                    recordPack("loaded");
                    cancel();
                    // Done: not also timed out, when this was the last check.
                    return;
                }
                else if(!chatSent && attempts >= NO_PACK_CHAT_ATTEMPTS
                        && data.getCurrentRpStatus().equals(RpPlayerStatus.NOT_SENT)) {
                    // Still no resource pack offered: no overlay without it, but the room's chat text
                    // tells the player what to do. Keep waiting for a pack for the overlay.
                    sendChat(player);
                    chatSent = true;
                    recordPack("not_sent");
                }
                else if(isRpFail(data)) {
                    stopOverlayTask(player);
                    // pick message based on exact failure reason
                    RpPlayerStatus status = data.getCurrentRpStatus();
                    recordPack(status.name().toLowerCase(Locale.ROOT));
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
                    return;
                }
                attempts++;
                if(attempts > 500) {
                    if(rpTimeoutMessage != null && !rpTimeoutMessage.equals(Component.text(""))) {
                        IntroductionPlugin.sendErrorMessage(player, rpTimeoutMessage);
                    } else {
                        IntroductionPlugin.sendErrorMessage(player, "Could not send overlay as your resource pack doesn't seem to load: Timed out.");
                    }
                    recordPack("timeout");
                    stopOverlayTask(player);
                }
            }

            private void recordPack(String result) {
                if(recordPack) {
                    FlowEvents.pack(Room.this, player, result, waitingSince);
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
//Logger.getGlobal().info("run task "+this);
        stopActionBar(player);
        actionBarTasks.put(player.getUniqueId(), Bukkit.getScheduler().runTaskTimer(IntroductionPlugin.getInstance(), () -> {
            player.sendActionBar(messageActionBar);
        },actionBarDelay, actionBarPeriod));
    }

    private void stopActionBar(Player player) {
        BukkitTask task = actionBarTasks.get(player.getUniqueId());
//Logger.getGlobal().info("stop task: "+task+ " "+this);
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
        String locData = (config!=null?config.getString(key, "0 0 0 0 0"):"0 0 0 0 0");
        String[] location = locData.split(" ");
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
        // At shutdown onDisable takes players out of the rooms, but Paper has disabled the plugin
        // already and refuses plugin messages from it: the proxy can't be told then.
        if(!IntroductionPlugin.getInstance().isEnabled()) {
            return;
        }
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("exit");
        out.writeUTF(player.getUniqueId().toString());
        player.sendPluginMessage(IntroductionPlugin.getInstance(),
                IntroductionPlugin.CHANNEL,
                out.toByteArray());
    }

    public void cancelUnSilence(Player player) {
        BukkitTask task = unsilenceTasks.get(player.getUniqueId());
        if(task!= null) {
            task.cancel();
            unsilenceTasks.remove(player.getUniqueId());
        }
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

    @SuppressWarnings("UnstableApiUsage")
    static boolean isOverlayItem(@Nullable ItemStack item) {
        if(item == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if(meta != null) {
            EquippableComponent equip = meta.getEquippable();
            NamespacedKey key = equip.getCameraOverlay();
            return overlays.contains(key);
        }
        return false;
    }

    public int getEnterMoveDelay() {
        return enterMoveDelay;
    }
}
