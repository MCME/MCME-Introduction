package com.mcmiddleearth.introduction.rooms;

import com.mcmiddleearth.introduction.IntroductionPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.json.JSONComponentSerializer;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.TitlePart;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.EquippableComponent;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public abstract class Room {

    private Room next;
    private final Location pos1, pos2, tpTarget;
    private final Component tpTransitionTitle;
    private final String[] tpTransitionTimes;


    public Room(ConfigurationSection config) {
        this.pos1 = getLocation(null, config.getConfigurationSection("pos1"));
        this.pos2 = getLocation(null, config.getConfigurationSection("pos2"));
        this.tpTarget = getLocation(null, config.getConfigurationSection("tpTarget"));
        this.tpTransitionTitle = JSONComponentSerializer.json()
                .deserialize(Objects.requireNonNull(config.getString("tpTransitionComponent")));
        this.tpTransitionTimes = Objects.requireNonNull(config.getString("tpTransitionTimes")).split(" ");
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
        return pos1!=null && location!=null && pos1.getWorld()!=null && pos1.getWorld().equals(location.getWorld())
                && pos1.getX()<location.getX() && pos2.getX()>location.getX()
                && pos1.getY()<location.getY() && pos2.getY()>location.getY()
                && pos1.getZ()<location.getZ() && pos2.getZ()>location.getZ();
    }

    public void teleport(Player player) {
        Title.Times times = Title.Times.times(Duration.ofMillis(Integer.parseInt(tpTransitionTimes[0])),
                                        Duration.ofMillis(Integer.parseInt(tpTransitionTimes[0])),
                                        Duration.ofMillis(Integer.parseInt(tpTransitionTimes[0])));
        Title black = Title.title(tpTransitionTitle,Component.empty(),times);
        player.showTitle(black);
        Bukkit.getScheduler().runTaskLater(IntroductionPlugin.getInstance(), () -> player.teleport(tpTarget),
                                           Long.parseLong(tpTransitionTimes[0]));
    }

    @SuppressWarnings("UnstableApiUsage")
    public void setCameraOverlay(Player player) {
        ItemStack overlayItem = new ItemStack(Material.STONE);
        ItemMeta meta = overlayItem.getItemMeta();
        EquippableComponent equip = meta.getEquippable(); // vgl. Javadoc
        equip.setSlot(EquipmentSlot.HEAD);
        equip.setCameraOverlay(selectCameraOverlay(player));
        overlayItem.setItemMeta(meta);
        player.getInventory().setItem(EquipmentSlot.HEAD, overlayItem);
    }

    public abstract NamespacedKey selectCameraOverlay(Player player);

    public void sendChat(Player player) {
        //default: do nothing
    }

    private Location getLocation(World world, ConfigurationSection config) {
        if(world==null) {
            world = Bukkit.getWorld(Objects.requireNonNull(config.getString("world")));
        }
        return new Location(world, config.getDouble("x"), config.getDouble("y"), config.getDouble("z"));
    }
}
