package com.mcmiddleearth.introduction.rooms;

import com.mcmiddleearth.introduction.IntroductionPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.json.JSONComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.EquippableComponent;

import java.time.Duration;
import java.util.*;
import java.util.logging.Logger;

public abstract class Room {

    private Room next;
    private final Location pos1, pos2, tpTarget;
    private final Component tpTransitionTitle;
    private final String[] tpTransitionTimes;
    private final boolean canIgnore;

    private final Map<UUID, ItemStack> playerItems = new HashMap<>();
    private final Map<UUID, GameMode> playerGamemodes = new HashMap<>();
    private final Set<UUID> fixedPlayers = new HashSet<>();

    private final Set<UUID> awaitingTeleport = new HashSet<>();

    public Room(ConfigurationSection config) {
        World world = Bukkit.getWorld(Objects.requireNonNull(config.getString("world")));
        this.pos1 = getLocation(world, "pos1", config);
        this.pos2 = getLocation(world, "pos2", config);
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
        ConfigurationSection targetConfig = config.getConfigurationSection("tpTarget");
        this.tpTarget = getLocation(Bukkit.getWorld(targetConfig.getString("world", world.getName())),
                                "pos", targetConfig);
        this.tpTransitionTitle = getMessage(config.getString("tpTransitionComponent"));
        this.tpTransitionTimes = Objects.requireNonNull(config.getString("tpTransitionTimes")).split(" ");
        this.canIgnore = config.getBoolean("canIgnore", false);
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

    public boolean isInside(Player player) {
        Location location = player.getLocation();
        return pos1!=null && pos1.getWorld()!=null && pos1.getWorld().equals(location.getWorld())
                && pos1.getX()<location.getX() && pos2.getX()>location.getX()
                && pos1.getY()<location.getY() && pos2.getY()>location.getY()
                && pos1.getZ()<location.getZ() && pos2.getZ()>location.getZ();
    }

    public void handleEnter(Player player) {
        if(!playerItems.containsKey(player.getUniqueId())) {
            playerItems.put(player.getUniqueId(), player.getInventory().getItem(EquipmentSlot.HEAD));
            setCameraOverlay(player);
            sendChat(player);
            playerGamemodes.put(player.getUniqueId(), player.getGameMode());
            player.setGameMode(GameMode.SPECTATOR);
        } else {
            if(!playerGamemodes.containsKey(player.getUniqueId())) {
                sendChat(player);
                playerGamemodes.put(player.getUniqueId(), player.getGameMode());
                player.setGameMode(GameMode.SPECTATOR);
            }
        }
    }

    public void handleExit(Player player) {
Logger.getGlobal().info("Room: "+this.toString()+" handleExit");
Logger.getGlobal().info(fixedPlayers.contains(player.getUniqueId())+" && "+playerItems.containsKey(player.getUniqueId()));
        if(playerGamemodes.containsKey(player.getUniqueId())) {
            if(!fixedPlayers.contains(player.getUniqueId())) {
                removeCameraOverlay(player);
                playerItems.remove(player.getUniqueId());
            }
            player.setGameMode(playerGamemodes.get(player.getUniqueId()));
            playerGamemodes.remove(player.getUniqueId());
        }
    }

    public void handleOverride(Player player, boolean override) {
        if(override) {
            fixedPlayers.add(player.getUniqueId());
            if(!playerItems.containsKey(player.getUniqueId())) {
                playerItems.put(player.getUniqueId(), player.getInventory().getItem(EquipmentSlot.HEAD));
                setCameraOverlay(player);
                sendChat(player);
            }
        } else {
            fixedPlayers.remove(player.getUniqueId());
            if(playerItems.containsKey(player.getUniqueId()) && !isInside(player)) {
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
            player.teleport(tpTarget);
            previous.awaitingTeleport.remove(player.getUniqueId());
//Logger.getGlobal().info("UNSET awaiting teleport");
            if(next != null) {
                next.handleEnter(player);
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
        player.getInventory().setItem(EquipmentSlot.HEAD, overlayItem);
    }

    public void removeCameraOverlay(Player player) {
        //player.getInventory().setItem(EquipmentSlot.HEAD, new ItemStack(Material.AIR));
        player.getInventory().setItem(EquipmentSlot.HEAD, playerItems.get(player.getUniqueId()));
    }

    public abstract NamespacedKey selectCameraOverlay(Player player);

    public void sendChat(Player player) {
        //default: no message sent
    }

    public boolean canIgnore() {
        return canIgnore;
    }

    public boolean isAwaitingTeleport(Player player) {
//Logger.getGlobal().info("check in room "+this.toString()+" for uuid: "+player.getUniqueId() +" "+awaitingTeleport.contains(player.getUniqueId()));
//awaitingTeleport.forEach(uuid -> Logger.getGlobal().info(" "+uuid));
        return awaitingTeleport.contains(player.getUniqueId());
    }

    private Location getLocation(World world, String key, ConfigurationSection config) {
        String[] location = Objects.requireNonNull(config.getString(key)).split(" ");
        return new Location(world, Double.parseDouble(location[0]),
                                   Double.parseDouble(location[1]),
                                   Double.parseDouble(location[2]));
    }

    protected Component getMessage(String code) {
        try {
            return JSONComponentSerializer.json().deserialize(code);
        } catch (Exception ex) {
            ex.printStackTrace();
            return Component.text(code);
        }
    }

    public void showPlayers() {
        playerItems.forEach((uuid,item)->Logger.getGlobal().info(uuid+" - "+item));
    }
}
