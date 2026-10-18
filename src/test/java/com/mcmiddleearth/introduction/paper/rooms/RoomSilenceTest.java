package com.mcmiddleearth.introduction.paper.rooms;

import com.mcmiddleearth.architect.serverResoucePack.RpPlayerData;
import com.mcmiddleearth.connect.events.PlayerConnectEvent;
import com.mcmiddleearth.introduction.paper.flow.FlowEvents.Exit;
import com.mcmiddleearth.introduction.paper.listener.ChatPacketListener;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.IOException;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A player in a room sees no chat. Leaving a room gives chat back after a short delay, unless the
 * player enters another room meanwhile.
 */
class RoomSilenceTest extends RoomTestBase {

    // Room's default unsilenceDelay; the default config.yml does not set it.
    private static final int UNSILENCE_DELAY = 20;

    private Function<Player, RpPlayerData> realPackStatus;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        // No resource pack offered: the rooms keep waiting for one and send nothing.
        realPackStatus = Room.rpPlayerData;
        Room.rpPlayerData = p -> new RpPlayerData();
        // Without its advancement, the first room awards none (MockBukkit cannot show it).
        plugin.getConfig().set("firstRoom.advancementDisplay", null);
        plugin.unloadData();
        plugin.loadData();
        // The second room picks its overlay by client brand, which PlayerMock does not implement.
        player = new PlayerMock(server, "Newcomer") {
            @Override
            public String getClientBrandName() {
                return "vanilla";
            }
        };
        server.addPlayer(player);
    }

    @AfterEach
    void restorePackStatus() {
        Room.rpPlayerData = realPackStatus;
    }

    @Test
    void chatStaysHiddenWhenThePlayerWalksOnIntoTheSecondRoom() {
        plugin.getFirst().handleEnter(player);

        // What Room.teleport does when the player walks on: leave the first room, enter the next.
        plugin.getFirst().handleExit(player, true, Exit.WALKED);
        plugin.getSecond().handleEnter(player);
        server.getScheduler().performTicks(UNSILENCE_DELAY + 20);

        assertTrue(ChatPacketListener.isBlocked(player.getUniqueId(), null), "chat hidden in the second room");
        plugin.getSecond().handleExit(player, false, Exit.LEFT);
    }

    @Test
    void theServerStopsCleanlyWithAPlayerInARoom() {
        plugin.getFirst().handleEnter(player);

        // Paper disables the plugin before onDisable runs; onDisable takes the player out of the room.
        assertDoesNotThrow(() -> server.getPluginManager().disablePlugin(plugin));

        assertFalse(ChatPacketListener.isBlocked(player.getUniqueId(), null), "chat back after the shutdown");
    }

    @Test
    void chatComesBackAfterLeavingTheLastRoom() {
        plugin.getFirst().handleEnter(player);

        plugin.getFirst().handleExit(player, true, Exit.WALKED);
        server.getScheduler().performTicks(UNSILENCE_DELAY + 20);

        assertFalse(ChatPacketListener.isBlocked(player.getUniqueId(), null), "chat back after the rooms");
    }

    @Test
    void chatComesBackForAPlayerWhoJoinsInARoomAndIsPassedOn() throws IOException {
        // Quit in the compatibility room last time, and came back with a client that needs nothing.
        World world = addRooms();
        player.setLocation(new Location(world, 22, 61, 2));

        server.getPluginManager().callEvent(new PlayerConnectEvent(player, PlayerConnectEvent.ConnectReason.JOIN_PROXY));
        server.getScheduler().performTicks(plugin.getConfig().getLong("joinDelay") + TELEPORT_DELAY + UNSILENCE_DELAY + 20);

        assertFalse(ChatPacketListener.isBlocked(player.getUniqueId(), null), "chat back after being passed on");
    }

    @Test
    void chatComesBackForAPlayerMovedOutOfARoomWhileJoining() throws IOException {
        SecondRoom.clientVersion = p -> ProtocolVersion.v1_21_4;
        World world = addRooms();
        player.setLocation(new Location(world, 22, 61, 2));
        server.getPluginManager().callEvent(new PlayerConnectEvent(player, PlayerConnectEvent.ConnectReason.JOIN_PROXY));

        // A teleport by staff, say, before the join delay is over.
        player.teleport(new Location(world, 40, 61, 2));
        server.getScheduler().performTicks(plugin.getConfig().getLong("joinDelay") + 1);

        assertFalse(ChatPacketListener.isBlocked(player.getUniqueId(), null), "chat back outside the rooms");
    }
}
