package com.mcmiddleearth.introduction.paper.rooms;

import com.mcmiddleearth.introduction.paper.PluginTestBase;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * For tests that put players in the rooms. The rooms ask ViaVersion for a player's client
 * version and MCME-Architect whether the client has MCME's marker mod; neither runs in
 * MockBukkit. Here every player has a 26.2 client without the marker mod, unless a test sets
 * {@link SecondRoom#clientVersion} or {@link SecondRoom#mcmeMarker} itself.
 */
abstract class RoomTestBase extends PluginTestBase {

    // The rooms' tpTransitionTimes in the default config.yml start with 10: the teleport comes
    // 10 ticks after the black screen.
    static final int TELEPORT_DELAY = 10;

    // Two rooms side by side, as in a locations.yml. Leaving the first room puts a player in the
    // second; leaving the second puts them outside both, at 40 61 2.
    private static final String LOCATIONS = """
            firstRoom:
              world: world
              pos1: 0 60 0
              pos2: 4 64 4
              playerLocation: 2 61 2 0 0
              tpTarget:
                pos: 22 61 2 0 0
            secondRoom:
              world: world
              pos1: 20 60 0
              pos2: 24 64 4
              playerLocation: 22 61 2 0 0
              tpTarget:
                pos: 40 61 2 0 0
            """;

    private Function<Player, ProtocolVersion> realClientVersion;
    private Predicate<Player> realMcmeMarker;

    @BeforeEach
    void fakeTheClient() {
        realClientVersion = SecondRoom.clientVersion;
        realMcmeMarker = SecondRoom.mcmeMarker;
        SecondRoom.clientVersion = player -> ProtocolVersion.v26_2;
        SecondRoom.mcmeMarker = player -> false;
    }

    @AfterEach
    void restoreTheClient() {
        SecondRoom.clientVersion = realClientVersion;
        SecondRoom.mcmeMarker = realMcmeMarker;
    }

    /**
     * Builds the two rooms in the world named "world": the first room is the box from 0 60 0 to
     * 4 64 4, the second from 20 60 0 to 24 64 4. Reloads the plugin's data, as /intro reload does.
     */
    protected World addRooms() throws IOException {
        // Adding a player to a server without worlds gives it one named "world" already.
        World world = server.getWorld("world");
        if (world == null) {
            world = server.addSimpleWorld("world");
        }
        Files.writeString(new File(plugin.getDataFolder(), "locations.yml").toPath(), LOCATIONS);
        plugin.unloadData();
        plugin.loadData();
        return world;
    }
}
