package com.mcmiddleearth.introduction.paper.rooms;

import com.mcmiddleearth.introduction.paper.PluginTestBase;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.EquippableComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A room puts a camera-overlay item on the player's head and gives the old head item back when
 * the player leaves. An overlay item can stay behind on a player's head, for example when the
 * server stops while they are in a room. Such a leftover must not be handed back as if it were
 * the player's own item.
 */
class RoomHeadItemTest extends PluginTestBase {

    private PlayerMock player;
    private Room room;

    @BeforeEach
    void addPlayer() {
        // The second room picks its overlay by client brand, which PlayerMock does not implement.
        player = new PlayerMock(server, "Newcomer") {
            @Override
            public String getClientBrandName() {
                return "vanilla";
            }
        };
        server.addPlayer(player);
        room = plugin.getSecond();
    }

    @Test
    void helmetComesBackAfterLeavingTheRoom() {
        wear(new ItemStack(Material.DIAMOND_HELMET));

        room.handleEnter(player);
        room.handleExit(player, false);

        assertEquals(Material.DIAMOND_HELMET, head().getType());
    }

    @Test
    void leftoverOverlayIsNotPutBackAfterLeavingTheRoom() {
        wear(leftoverOverlay());

        room.handleEnter(player);
        room.handleExit(player, false);

        assertTrue(head().isEmpty(), "head after leaving the room: " + head());
    }

    @Test
    void leftoverOverlayIsNotPutBackAfterAReminder() {
        wear(leftoverOverlay());

        room.handleOverride(player, true);
        room.handleOverride(player, false);

        assertTrue(head().isEmpty(), "head after the reminder: " + head());
    }

    @Test
    void anOverlayItemThatIsNotOursComesBack() {
        // A camera overlay this plugin does not use, like a carved pumpkin's.
        wear(itemWithOverlay(Material.LEATHER_HELMET, "minecraft:misc/pumpkinblur"));

        room.handleEnter(player);
        room.handleExit(player, false);

        assertEquals(Material.LEATHER_HELMET, head().getType());
    }

    /** The item a room puts on the head: stone carrying one of this plugin's camera overlays. */
    private ItemStack leftoverOverlay() {
        return itemWithOverlay(Material.STONE, plugin.getConfig().getString("secondRoom.overlayWarningVersion"));
    }

    @SuppressWarnings("UnstableApiUsage")
    private static ItemStack itemWithOverlay(Material material, String overlay) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        EquippableComponent equip = meta.getEquippable();
        equip.setSlot(EquipmentSlot.HEAD);
        equip.setCameraOverlay(NamespacedKey.fromString(overlay));
        meta.setEquippable(equip);
        item.setItemMeta(meta);
        return item;
    }

    private void wear(ItemStack item) {
        player.getInventory().setItem(EquipmentSlot.HEAD, item);
    }

    private ItemStack head() {
        return player.getInventory().getItem(EquipmentSlot.HEAD);
    }
}
