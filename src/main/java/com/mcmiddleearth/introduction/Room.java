package com.mcmiddleearth.introduction;

import io.papermc.paper.datacomponent.item.Equippable;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class Room {

    private Room next;
    private Location pos1, pos2, tpTarget;
    private NamespacedKey[] cameraOverlay;


    public Room(Location pos1, Location pos2, Location tpTarget, NamespacedKey... cameraOverlay) {
        this.pos1 = pos1;
        this.pos2 = pos2;
        this.tpTarget = tpTarget;
        this.cameraOverlay = cameraOverlay;
    }

    public void setNextRoom(Room room) {
        next = room;
    }

    public Room getNext() {
        return next;
    }

    public Location getPos1() {
        return pos1;
    }

    public Location getPos2() {
        return pos2;
    }

    public Location getTpTarget() {
        return tpTarget;
    }

    public void setCameraOverlay(Player player) {
        ItemStack overlayItem = new ItemStack(Material.STONE);
        ItemMeta meta = overlayItem.getItemMeta();
        meta.setEquippable(Equippable.equippable(EquipmentSlot.HEAD).cameraOverlay(cameraOverlay).build());
        overlayItem.setItemMeta(meta);
        player.getInventory().setItem(EquipmentSlot.HEAD, overlayItem);
    }
}
