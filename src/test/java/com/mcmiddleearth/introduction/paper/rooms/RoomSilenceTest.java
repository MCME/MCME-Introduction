package com.mcmiddleearth.introduction.paper.rooms;

import com.mcmiddleearth.architect.serverResoucePack.RpPlayerData;
import com.mcmiddleearth.introduction.paper.PluginTestBase;
import com.mcmiddleearth.introduction.paper.listener.ChatPacketListener;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A player in a room sees no chat. Leaving a room gives chat back after a short delay, unless the
 * player enters another room meanwhile.
 */
class RoomSilenceTest extends PluginTestBase {

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
        plugin.getFirst().handleExit(player, true);
        plugin.getSecond().handleEnter(player);
        server.getScheduler().performTicks(UNSILENCE_DELAY + 20);

        assertTrue(ChatPacketListener.isBlocked(player.getUniqueId(), null), "chat hidden in the second room");
        plugin.getSecond().handleExit(player, false);
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

        plugin.getFirst().handleExit(player, true);
        server.getScheduler().performTicks(UNSILENCE_DELAY + 20);

        assertFalse(ChatPacketListener.isBlocked(player.getUniqueId(), null), "chat back after the rooms");
    }
}
