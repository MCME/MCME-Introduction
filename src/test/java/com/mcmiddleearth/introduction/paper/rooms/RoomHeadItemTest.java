package com.mcmiddleearth.introduction.paper.rooms;

import com.mcmiddleearth.architect.serverResoucePack.RpPlayerData;
import com.mcmiddleearth.architect.serverResoucePack.RpPlayerStatus;
import com.mcmiddleearth.introduction.paper.flow.FlowEvents.Exit;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.EquippableComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A room puts a camera-overlay item on the player's head and gives the old head item back when
 * the player leaves. An overlay item can stay behind on a player's head, for example when the
 * server stops while they are in a room. Such a leftover must not be handed back as if it were
 * the player's own item.
 * <p>
 * A reminder does the same outside the rooms, where the player can change their head item while
 * the reminder runs: the player must end up with each item once.
 */
class RoomHeadItemTest extends RoomTestBase {

    private Function<Player, RpPlayerData> realPackStatus;
    private final RpPlayerData packStatus = new RpPlayerData();
    private PlayerMock player;
    private Room room;

    @BeforeEach
    void addPlayer() {
        realPackStatus = Room.rpPlayerData;
        Room.rpPlayerData = p -> packStatus;
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

    @AfterEach
    void restorePackStatus() {
        Room.rpPlayerData = realPackStatus;
    }

    @Test
    void helmetComesBackAfterLeavingTheRoom() {
        wear(new ItemStack(Material.DIAMOND_HELMET));

        room.handleEnter(player);
        room.handleExit(player, false, Exit.LEFT);

        assertEquals(Material.DIAMOND_HELMET, head().getType());
    }

    @Test
    void leftoverOverlayIsNotPutBackAfterLeavingTheRoom() {
        wear(leftoverOverlay());

        room.handleEnter(player);
        room.handleExit(player, false, Exit.LEFT);

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
        room.handleExit(player, false, Exit.LEFT);

        assertEquals(Material.LEATHER_HELMET, head().getType());
    }

    @Test
    void aHelmetSwappedDuringAReminderWithoutThePackStaysOn() {
        // A new RpPlayerData reads NOT_SENT: the pack never comes, so the warning screen never goes on.
        wear(new ItemStack(Material.DIAMOND_HELMET));
        reminder().startReminderTask(player);
        server.getScheduler().performTicks(40);

        swapHelmetFor(Material.GOLDEN_HELMET);
        player.disconnect();

        assertEquals(Material.GOLDEN_HELMET, head().getType());
        assertEquals(1, count(Material.DIAMOND_HELMET), "diamond helmets");
    }

    @Test
    void aHelmetSwappedBeforeThePackLoadsComesBackAfterTheReminder() {
        packStatus.setCurrentRpStatus(RpPlayerStatus.SENT);
        wear(new ItemStack(Material.DIAMOND_HELMET));
        reminder().startReminderTask(player);
        server.getScheduler().performTicks(20);

        swapHelmetFor(Material.GOLDEN_HELMET);
        packStatus.setCurrentRpStatus(RpPlayerStatus.SUCCESSFULLY_LOADED);
        server.getScheduler().performTicks(10);
        assertTrue(room.isOverlayItem(head()), "the warning screen is on: " + head());
        server.getScheduler().performTicks(reminderDuration() + 40);

        assertEquals(Material.GOLDEN_HELMET, head().getType());
        assertEquals(1, count(Material.DIAMOND_HELMET), "diamond helmets");
    }

    @Test
    void aHelmetUnderAWarningScreenTakenOffComesBackInTheInventory() {
        packStatus.setCurrentRpStatus(RpPlayerStatus.SUCCESSFULLY_LOADED);
        wear(new ItemStack(Material.DIAMOND_HELMET));
        reminder().startReminderTask(player);
        server.getScheduler().performTicks(5);

        // The player clicks the warning screen's item off their head.
        player.getInventory().addItem(head());
        wear(new ItemStack(Material.AIR));
        server.getScheduler().performTicks(reminderDuration() + 40);

        assertEquals(1, count(Material.DIAMOND_HELMET), "diamond helmets");
    }

    @Test
    void aReminderEndsWhenThePackFailsBeforeItLoaded() {
        packStatus.setCurrentRpStatus(RpPlayerStatus.DECLINED);
        reminder().startReminderTask(player);

        server.getScheduler().performTicks(40);

        assertFalse(room.isFixed(player), "the reminder still holds the player");
    }

    @Test
    void aReminderEndsAfterItsLastCheckWhenThePackNeverComes() {
        reminder().startReminderTask(player);

        // The reminder checks the pack every second, 1000 times.
        server.getScheduler().performTicks(20 * 1000 + 40);

        assertFalse(room.isFixed(player), "the reminder still holds the player");
    }

    /** The compatibility room, set up so the player gets reminders: a client on 1.21.4. */
    private SecondRoom reminder() {
        SecondRoom.clientVersion = p -> ProtocolVersion.v1_21_4;
        return (SecondRoom) room;
    }

    private long reminderDuration() {
        return plugin.getConfig().getLong("reminderDuration");
    }

    /** Puts the helmet the player wears in their inventory, and another helmet on their head. */
    private void swapHelmetFor(Material other) {
        player.getInventory().addItem(head());
        wear(new ItemStack(other));
    }

    /** How many of an item the player has, on their head and in their inventory. */
    private int count(Material material) {
        int count = (head().getType() == material ? head().getAmount() : 0);
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count;
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
