package com.mcmiddleearth.introduction.paper.rooms;

import com.mcmiddleearth.architect.serverResoucePack.RpPlayerData;
import com.mcmiddleearth.introduction.paper.flow.FlowEvents.Exit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * A room puts the player in spectator mode and gives their own game mode back when they leave.
 * That must never be the room's spectator mode itself.
 */
class RoomGameModeTest extends RoomTestBase {

    private Function<Player, RpPlayerData> realPackStatus;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        realPackStatus = Room.rpPlayerData;
        Room.rpPlayerData = p -> new RpPlayerData();
        // Without its advancement, the first room awards none (MockBukkit cannot show it).
        plugin.getConfig().set("firstRoom.advancementDisplay", null);
        plugin.unloadData();
        plugin.loadData();
        // The flow log records the client brand, which PlayerMock does not implement.
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
    void aPlayerWhoEntersInSpectatorModeLeavesInTheServersDefaultMode() {
        // After a crash, or a kill, in a room: the player's saved game mode is the room's.
        server.setDefaultGameMode(GameMode.ADVENTURE);
        player.setGameMode(GameMode.SPECTATOR);

        plugin.getFirst().handleEnter(player);
        plugin.getFirst().handleExit(player, false, Exit.WALKED);

        assertEquals(GameMode.ADVENTURE, player.getGameMode());
    }

    @Test
    void aPlayerMovedFromOneRoomIntoTheOtherGetsTheirOwnModeBack() {
        player.setGameMode(GameMode.ADVENTURE);
        plugin.getFirst().handleEnter(player);

        // A teleport by staff from one room's box into the other's, without walking on.
        plugin.getSecond().handleEnter(player);

        assertFalse(plugin.getFirst().isEntered(player), "still in the welcome room");
        plugin.getSecond().handleExit(player, false, Exit.LEFT);
        assertEquals(GameMode.ADVENTURE, player.getGameMode());
    }
}
