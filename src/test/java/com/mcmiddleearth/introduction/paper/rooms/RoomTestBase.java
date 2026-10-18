package com.mcmiddleearth.introduction.paper.rooms;

import com.mcmiddleearth.introduction.paper.PluginTestBase;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * For tests that put players in the rooms. The rooms ask ViaVersion for a player's client
 * version and MCME-Architect whether the client has MCME's marker mod; neither runs in
 * MockBukkit. Here every player has a 26.2 client without the marker mod, unless a test sets
 * {@link SecondRoom#clientVersion} or {@link SecondRoom#mcmeMarker} itself.
 */
abstract class RoomTestBase extends PluginTestBase {

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
}
