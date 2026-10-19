package com.mcmiddleearth.introduction.paper.rooms;

import com.mcmiddleearth.architect.serverResoucePack.RpPlayerData;
import com.mcmiddleearth.architect.serverResoucePack.RpPlayerStatus;
import com.mcmiddleearth.connect.events.PlayerConnectEvent;
import com.mcmiddleearth.introduction.paper.flow.FlowEvents.Exit;
import com.mcmiddleearth.introduction.paper.listener.ChatPacketListener;
import com.mcmiddleearth.introduction.paper.listener.RoomListener;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * /intro reload replaces the rooms with new ones. What the old rooms had scheduled for a player
 * must not run on afterwards, with rooms the plugin no longer uses.
 */
class RoomReloadTest extends RoomTestBase {

    // Room's default unsilenceDelay; the default config.yml does not set it.
    private static final int UNSILENCE_DELAY = 20;
    // /intro reload loads the data again 20 ticks later.
    private static final int RELOAD_DELAY = 20;

    private Function<Player, RpPlayerData> realPackStatus;
    private final RpPlayerData packStatus = new RpPlayerData();
    private World world;
    private PlayerMock player;

    @BeforeEach
    void setUp() throws IOException {
        realPackStatus = Room.rpPlayerData;
        Room.rpPlayerData = p -> packStatus;
        // Without its advancement, the first room awards none (MockBukkit cannot show it).
        plugin.getConfig().set("firstRoom.advancementDisplay", null);
        world = addRooms();
        // The flow log records the client brand, which PlayerMock does not implement.
        player = new PlayerMock(server, "Newcomer") {
            @Override
            public String getClientBrandName() {
                return "vanilla";
            }

            // /intro reload reads config.yml again, with the welcome room's toast; MockBukkit
            // does not implement advancement progress.
            @Override
            public AdvancementProgress getAdvancementProgress(Advancement advancement) {
                return Mockito.mock(AdvancementProgress.class);
            }
        };
        server.addPlayer(player);
        player.setLocation(new Location(world, 2, 61, 2));
    }

    @AfterEach
    void restorePackStatus() {
        Room.rpPlayerData = realPackStatus;
    }

    @Test
    void aPlayerWhoJoinsInARoomDuringAReloadIsPutInTheNewRoom() {
        packStatus.setCurrentRpStatus(RpPlayerStatus.SUCCESSFULLY_LOADED);
        player.getInventory().setItem(EquipmentSlot.HEAD, new ItemStack(Material.DIAMOND_HELMET));
        server.dispatchCommand(server.getConsoleSender(), "help");

        server.getPluginManager().callEvent(new PlayerConnectEvent(player, PlayerConnectEvent.ConnectReason.JOIN_PROXY));
        server.getScheduler().performTicks(5);
        server.dispatchCommand(server.getConsoleSender(), "intro reload");
        server.getScheduler().performTicks(RELOAD_DELAY + 2 * joinDelay() + 5);

        assertTrue(plugin.getFirst().isEntered(player), "in the new welcome room");
        plugin.getFirst().handleExit(player, false, Exit.LEFT);
        assertEquals(Material.DIAMOND_HELMET, player.getInventory().getItem(EquipmentSlot.HEAD).getType());
        assertEquals(GameMode.SURVIVAL, player.getGameMode());
    }

    @Test
    void aPlayerWhoJoinsOutsideTheRoomsDuringAReloadGetsTheNewRoomsReminders() {
        SecondRoom.clientVersion = p -> ProtocolVersion.v1_21_4;
        player.setLocation(new Location(world, 40, 61, 2));
        server.dispatchCommand(server.getConsoleSender(), "help");

        server.getPluginManager().callEvent(new PlayerConnectEvent(player, PlayerConnectEvent.ConnectReason.JOIN_PROXY));
        server.getScheduler().performTicks(5);
        server.dispatchCommand(server.getConsoleSender(), "intro reload");
        server.getScheduler().performTicks(RELOAD_DELAY + 2 * joinDelay() + 5);

        assertTrue(plugin.getSecond().isFixed(player), "the new compatibility room's reminder");
    }

    private long joinDelay() {
        return plugin.getConfig().getLong("joinDelay");
    }

    @Test
    void aReloadDuringTheBlackScreenLeavesThePlayerWhereTheyAre() {
        SecondRoom.clientVersion = p -> ProtocolVersion.v1_21_4;
        plugin.getFirst().handleEnter(player);
        RoomListener.teleportToNextRoom(plugin.getFirst(), player, Exit.WALKED);

        // What /intro reload does: the new rooms come a second later.
        plugin.unloadData();
        server.getScheduler().performTicks(TELEPORT_DELAY + 1);

        assertEquals(GameMode.SURVIVAL, player.getGameMode(), "put in an old room");
        assertTrue(plugin.getFirst().isInside(player.getLocation()), "moved on: " + player.getLocation());
    }

    @Test
    void chatHiddenAgainAfterAReloadStaysHidden() {
        plugin.getFirst().handleEnter(player);
        // Leaving a room gives chat back a moment later.
        plugin.getFirst().handleExit(player, true, Exit.WALKED);

        plugin.unloadData();
        plugin.loadData();
        plugin.getSecond().handleEnter(player);
        server.getScheduler().performTicks(UNSILENCE_DELAY + 5);

        assertTrue(ChatPacketListener.isBlocked(player.getUniqueId(), null), "chat hidden in the room");
        plugin.getSecond().handleExit(player, false, Exit.LEFT);
    }
}
