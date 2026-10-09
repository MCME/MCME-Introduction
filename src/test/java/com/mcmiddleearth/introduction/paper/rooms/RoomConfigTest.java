package com.mcmiddleearth.introduction.paper.rooms;

import com.mcmiddleearth.introduction.paper.PluginTestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoomConfigTest extends PluginTestBase {

    @Test
    void roomsUseTheTopLevelEnterMoveDelay() {
        // The default config.yml sets roomEnterMoveDelay at the top level only.
        int configured = plugin.getConfig().getInt("roomEnterMoveDelay");

        assertEquals(configured, plugin.getFirst().getEnterMoveDelay(), "first room");
        assertEquals(configured, plugin.getSecond().getEnterMoveDelay(), "second room");
    }

    @Test
    void aRoomsOwnEnterMoveDelayWins() {
        plugin.getConfig().set("firstRoom.roomEnterMoveDelay", 7);
        plugin.unloadData();
        plugin.loadData();

        assertEquals(7, plugin.getFirst().getEnterMoveDelay(), "first room");
        assertEquals(plugin.getConfig().getInt("roomEnterMoveDelay"), plugin.getSecond().getEnterMoveDelay(), "second room");
    }
}
