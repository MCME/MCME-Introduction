package com.mcmiddleearth.introduction.paper.rooms;

import com.mcmiddleearth.architect.serverResoucePack.RpPlayerData;
import com.mcmiddleearth.architect.serverResoucePack.RpPlayerStatus;
import com.mcmiddleearth.connect.events.PlayerConnectEvent;
import com.mcmiddleearth.introduction.paper.flow.FlowEvents.Exit;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.EquippableComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
        assertTrue(Room.isOverlayItem(head()), "the warning screen is on: " + head());
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
        // The item carries the helmet: kept, it would give a second one later.
        assertEquals(0, overlayItemsHeld(), "warning screen items the player kept");
    }

    @Test
    void aHelmetUnderAWarningScreenTakenOffIntoAFullInventoryTakesItsPlace() {
        packStatus.setCurrentRpStatus(RpPlayerStatus.SUCCESSFULLY_LOADED);
        wear(new ItemStack(Material.DIAMOND_HELMET));
        reminder().startReminderTask(player);
        server.getScheduler().performTicks(5);

        // The warning screen's item goes into the inventory, which is full otherwise.
        player.getInventory().addItem(head());
        wear(new ItemStack(Material.AIR));
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int slot = 0; slot < storage.length; slot++) {
            if (storage[slot] == null || storage[slot].isEmpty()) {
                player.getInventory().setItem(slot, new ItemStack(Material.DIRT, 64));
            }
        }
        server.getScheduler().performTicks(reminderDuration() + 40);

        assertEquals(1, count(Material.DIAMOND_HELMET), "diamond helmets");
        assertEquals(0, overlayItemsHeld(), "warning screen items the player kept");
        assertTrue(player.getWorld().getEntitiesByClass(Item.class).isEmpty(), "items dropped");
    }

    @Test
    void aHelmetUnderAWarningScreenIsWhatDropsWhenThePlayerDies() {
        packStatus.setCurrentRpStatus(RpPlayerStatus.SUCCESSFULLY_LOADED);
        wear(new ItemStack(Material.DIAMOND_HELMET));
        reminder().startReminderTask(player);
        server.getScheduler().performTicks(5);
        List<ItemStack> drops = new ArrayList<>();
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler(priority = EventPriority.MONITOR)
            public void died(PlayerDeathEvent event) {
                event.getDrops().stream().filter(Objects::nonNull).forEach(drops::add);
            }
        }, plugin);

        player.setHealth(0);
        server.getScheduler().performTicks(reminderDuration() + 40);

        long dropped = drops.stream().filter(item -> item.getType() == Material.DIAMOND_HELMET).count();
        assertEquals(1, count(Material.DIAMOND_HELMET) + dropped, "diamond helmets, kept and dropped");
        assertTrue(drops.stream().noneMatch(Room::isOverlayItem), "a warning screen item dropped");
    }

    @Test
    void aWarningScreenLeftByACrashGivesTheHelmetBackOnJoining() {
        wear(stoneLeftByACrash(Material.DIAMOND_HELMET));
        // The client needs no reminders now.
        SecondRoom.clientVersion = p -> ProtocolVersion.v26_2;

        server.getPluginManager().callEvent(new PlayerConnectEvent(player, PlayerConnectEvent.ConnectReason.JOIN_PROXY));
        server.getScheduler().performTicks(plugin.getConfig().getLong("joinDelay") + 1);

        assertEquals(Material.DIAMOND_HELMET, head().getType());
        assertEquals(1, count(Material.DIAMOND_HELMET), "diamond helmets");
    }

    @Test
    void aWarningScreenLeftByACrashGivesTheHelmetBackAfterTheRoom() {
        wear(stoneLeftByACrash(Material.DIAMOND_HELMET));
        // The room of the plugin's data as loaded after the "crash".
        Room current = plugin.getSecond();

        current.handleEnter(player);
        current.handleExit(player, false, Exit.LEFT);

        assertEquals(Material.DIAMOND_HELMET, head().getType());
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

    @Test
    void aReloadDuringAReminderGivesTheHelmetBack() {
        packStatus.setCurrentRpStatus(RpPlayerStatus.SUCCESSFULLY_LOADED);
        wear(new ItemStack(Material.DIAMOND_HELMET));
        reminder().startReminderTask(player);
        server.getScheduler().performTicks(5);

        plugin.unloadData();

        assertEquals(Material.DIAMOND_HELMET, head().getType());
        assertEquals(1, count(Material.DIAMOND_HELMET), "diamond helmets");
    }

    @Test
    void aShutdownDuringAReminderGivesTheHelmetBack() {
        packStatus.setCurrentRpStatus(RpPlayerStatus.SUCCESSFULLY_LOADED);
        wear(new ItemStack(Material.DIAMOND_HELMET));
        reminder().startReminderTask(player);
        server.getScheduler().performTicks(5);

        server.getPluginManager().disablePlugin(plugin);

        assertEquals(Material.DIAMOND_HELMET, head().getType());
    }

    @Test
    void theRemindersCarryOnInTheNewRoomAfterAReload() {
        packStatus.setCurrentRpStatus(RpPlayerStatus.SUCCESSFULLY_LOADED);
        wear(new ItemStack(Material.DIAMOND_HELMET));
        reminder().startReminderTask(player);
        server.getScheduler().performTicks(reminderDuration() + 40);

        // A reload with a new warning screen: the old room's would show that its reminders run on.
        String newScreen = "minecraft:gui/overlay/warning_after_reload";
        plugin.getConfig().set("secondRoom.overlayWarningVersion", newScreen);
        plugin.unloadData();
        plugin.loadData();

        Set<String> screens = new HashSet<>();
        for (long tick = 1; tick <= plugin.getConfig().getLong("reminderPeriod") + 40; tick++) {
            server.getScheduler().performOneTick();
            if (Room.isOverlayItem(head())) {
                screens.add(head().getItemMeta().getEquippable().getCameraOverlay().asString());
            }
        }
        assertEquals(Set.of(newScreen), screens, "warning screens after the reload");
        server.getScheduler().performTicks(reminderDuration() + 40);
        assertEquals(Material.DIAMOND_HELMET, head().getType());
    }

    @Test
    void aLeftoverOverlayIsTakenOffOnJoiningOutsideTheRooms() {
        // A crash left the warning screen's item on the head. The client needs no reminders now.
        wear(leftoverOverlay());

        server.getPluginManager().callEvent(new PlayerConnectEvent(player, PlayerConnectEvent.ConnectReason.JOIN_PROXY));
        server.getScheduler().performTicks(plugin.getConfig().getLong("joinDelay") + 1);

        assertTrue(head().isEmpty(), "head after joining: " + head());
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

    /**
     * The warning screen's item as a crash leaves it on the head: a reminder put it on over the
     * helmet, and only the item itself still knows the helmet.
     */
    private ItemStack stoneLeftByACrash(Material helmet) {
        packStatus.setCurrentRpStatus(RpPlayerStatus.SUCCESSFULLY_LOADED);
        wear(new ItemStack(helmet));
        reminder().startReminderTask(player);
        server.getScheduler().performTicks(5);
        ItemStack stone = head().clone();
        // What the plugin kept in memory is gone, as after a crash.
        plugin.unloadData();
        plugin.loadData();
        packStatus.setCurrentRpStatus(RpPlayerStatus.NOT_SENT);
        player.getInventory().clear();
        return stone;
    }

    /** How many of the plugin's overlay items the player has, anywhere in their inventory. */
    private long overlayItemsHeld() {
        return Arrays.stream(player.getInventory().getContents()).filter(Room::isOverlayItem).count()
                + (Room.isOverlayItem(player.getItemOnCursor()) ? 1 : 0);
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
