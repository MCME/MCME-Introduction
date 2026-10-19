package com.mcmiddleearth.introduction.paper.rooms;

import com.mcmiddleearth.architect.serverResoucePack.RpPlayerData;
import com.mcmiddleearth.introduction.paper.flow.FlowEvents.Exit;
import com.mcmiddleearth.introduction.paper.listener.RoomListener;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stepping left sends a player on after a black screen; while it lasts, the room ignores their
 * moves. A player must never be left waiting for a move on that doesn't come.
 */
class RoomTeleportTest extends RoomTestBase {

    // The welcome room sends players on into a world that isn't loaded.
    private static final String LOCATIONS = """
            firstRoom:
              world: world
              pos1: 0 60 0
              pos2: 4 64 4
              playerLocation: 2 61 2 0 0
              tpTarget:
                world: nowhere
                pos: 22 61 2 0 0
            secondRoom:
              world: world
              pos1: 20 60 0
              pos2: 24 64 4
              playerLocation: 22 61 2 0 0
              tpTarget:
                pos: 40 61 2 0 0
            """;

    private Function<Player, RpPlayerData> realPackStatus;
    private World world;
    private PlayerMock player;

    @BeforeEach
    void setUp() throws IOException {
        realPackStatus = Room.rpPlayerData;
        Room.rpPlayerData = p -> new RpPlayerData();
        // Without its advancement, the first room awards none (MockBukkit cannot show it).
        plugin.getConfig().set("firstRoom.advancementDisplay", null);
        world = addRooms();
        // The flow log records the client brand, which PlayerMock does not implement.
        player = new PlayerMock(server, "Newcomer") {
            @Override
            public String getClientBrandName() {
                return "vanilla";
            }

            // Paper refuses a teleport to a location without a world; MockBukkit does it.
            @Override
            public boolean teleport(Location location) {
                if (location.getWorld() == null) {
                    throw new IllegalArgumentException("location.world");
                }
                return super.teleport(location);
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
    void aMoveOnThatFailsLeavesThePlayerFreeToMove() throws IOException {
        // A client that gets the compatibility room, so the welcome room's tpTarget is used.
        SecondRoom.clientVersion = p -> ProtocolVersion.v1_21_4;
        Files.writeString(new File(plugin.getDataFolder(), "locations.yml").toPath(), LOCATIONS);
        plugin.unloadData();
        plugin.loadData();
        plugin.getFirst().handleEnter(player);

        RoomListener.teleportToNextRoom(plugin.getFirst(), player, Exit.WALKED);
        try {
            server.getScheduler().performTicks(TELEPORT_DELAY + 1);
        } catch (RuntimeException failedTeleport) {
            // Goes to the server's log.
        }

        assertFalse(plugin.getFirst().isAwaitingTeleport(player), "still waiting for the move on");
    }

    @Test
    void aPlayerWhoQuitsDuringTheBlackScreenIsNotLeftWaiting() {
        plugin.getFirst().handleEnter(player);
        RoomListener.teleportToNextRoom(plugin.getFirst(), player, Exit.WALKED);
        assertTrue(plugin.getFirst().isAwaitingTeleport(player), "the black screen started");

        player.disconnect();

        assertFalse(plugin.getFirst().isAwaitingTeleport(player), "still waiting for the move on");
    }
}
