package com.mcmiddleearth.introduction.paper.rooms;

import com.destroystokyo.paper.event.player.PlayerStartSpectatingEntityEvent;
import com.mcmiddleearth.architect.serverResoucePack.RpPlayerData;
import com.mcmiddleearth.introduction.paper.flow.FlowEvents.Exit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.IOException;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A player in a room is in spectator mode, whose own ways of moving around would take them out of
 * the room past its screen: the spectator menu's "teleport to player", and spectating an entity.
 */
class RoomEscapeTest extends RoomTestBase {

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
        };
        server.addPlayer(player);
        player.setLocation(new Location(world, 2, 61, 2));
    }

    @AfterEach
    void restorePackStatus() {
        Room.rpPlayerData = realPackStatus;
    }

    @Test
    void theSpectatorMenuDoesNotTakeAPlayerOutOfARoom() {
        plugin.getFirst().handleEnter(player);

        // The number keys in spectator mode open "teleport to player".
        boolean teleported = player.teleport(new Location(world, 40, 61, 2), TeleportCause.SPECTATE);

        assertFalse(teleported, "teleported");
        assertTrue(plugin.getFirst().isInside(player.getLocation()), "still in the room");
        plugin.getFirst().handleExit(player, false, Exit.LEFT);
    }

    @Test
    void theSpectatorMenuStillWorksOutsideTheRooms() {
        player.setLocation(new Location(world, 40, 61, 2));

        assertTrue(player.teleport(new Location(world, 50, 61, 2), TeleportCause.SPECTATE));
    }

    @Test
    void aPlayerInARoomCannotSpectateAnEntity() {
        plugin.getFirst().handleEnter(player);
        PlayerMock other = server.addPlayer();

        PlayerStartSpectatingEntityEvent event = new PlayerStartSpectatingEntityEvent(player, player, other);
        server.getPluginManager().callEvent(event);

        assertTrue(event.isCancelled(), "spectating another player from the room");
        plugin.getFirst().handleExit(player, false, Exit.LEFT);
    }
}
