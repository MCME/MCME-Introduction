package com.mcmiddleearth.introduction.paper.rooms;

import com.mcmiddleearth.architect.serverResoucePack.RpPlayerData;
import com.mcmiddleearth.introduction.paper.flow.FlowEvents.Exit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A room puts the player in spectator mode and gives their own game mode back when they leave.
 * It keeps that mode in the player's own data, so a crash in a room doesn't lose it.
 */
class RoomGameModeTest extends RoomTestBase {

    private static final NamespacedKey SAVED_MODE = NamespacedKey.fromString("mcme:intro_gamemode");

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
    void theGameModeIsKeptWithThePlayerWhileInARoom() {
        player.setGameMode(GameMode.ADVENTURE);

        plugin.getFirst().handleEnter(player);
        assertEquals("ADVENTURE", savedMode(), "saved with the player");
        plugin.getFirst().handleExit(player, false, Exit.WALKED);

        assertEquals(GameMode.ADVENTURE, player.getGameMode());
        assertNull(savedMode(), "left with the player");
    }

    @Test
    void aGameModeLeftByACrashComesBack() {
        // A crash in a room: the server saved the player in the room's spectator mode, and with
        // their own mode kept by the room.
        player.setGameMode(GameMode.SPECTATOR);
        player.getPersistentDataContainer().set(SAVED_MODE, PersistentDataType.STRING, "ADVENTURE");

        plugin.getFirst().handleEnter(player);
        plugin.getFirst().handleExit(player, false, Exit.WALKED);

        assertEquals(GameMode.ADVENTURE, player.getGameMode());
        assertNull(savedMode());
    }

    @Test
    void staffWhoComeInSpectatorModeLeaveInSpectatorMode() {
        player.setGameMode(GameMode.SPECTATOR);

        plugin.getFirst().handleEnter(player);
        plugin.getFirst().handleExit(player, false, Exit.WALKED);

        assertEquals(GameMode.SPECTATOR, player.getGameMode());
    }

    /** The game mode a room keeps with the player while they are in it. */
    private String savedMode() {
        return player.getPersistentDataContainer().get(SAVED_MODE, PersistentDataType.STRING);
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
