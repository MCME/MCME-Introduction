package com.mcmiddleearth.introduction.paper.rooms;

import com.mcmiddleearth.introduction.paper.PluginTestBase;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which client versions the second room treats as supported, and how its messages name them.
 * The client versions are ViaVersion's own ProtocolVersion constants.
 */
class SecondRoomVersionTest extends PluginTestBase {

    @Test
    void listedVersionsAreSupported() {
        SecondRoom room = secondRoomSupporting("26.2", "26.3");

        assertTrue(room.isSupportedVersion(ProtocolVersion.v26_2));
        assertTrue(room.isSupportedVersion(ProtocolVersion.v26_3));
    }

    @Test
    void otherVersionsAreNotSupported() {
        SecondRoom room = secondRoomSupporting("26.2", "26.3");

        assertFalse(room.isSupportedVersion(ProtocolVersion.v26_1));
        assertFalse(room.isSupportedVersion(ProtocolVersion.v1_21_4));
    }

    @Test
    void aListedVersionCoversTheReleasesThatShareItsProtocol() {
        // 26.1, 26.1.1 and 26.1.2 share one protocol, which ViaVersion names "26.1-26.1.2".
        SecondRoom room = secondRoomSupporting("26.1.2");

        assertTrue(room.isSupportedVersion(ProtocolVersion.v26_1));
    }

    @Test
    void withoutAListOnlyTheServersOwnVersionIsSupported() {
        plugin.getConfig().set("supportedVersions", null);
        SecondRoom room = reloadedSecondRoom();
        ProtocolVersion serverVersion = ProtocolVersion.getClosest(server.getMinecraftVersion());
        assertNotNull(serverVersion, "ViaVersion knows the server version " + server.getMinecraftVersion());

        assertTrue(room.isSupportedVersion(serverVersion));
        assertFalse(room.isSupportedVersion(ProtocolVersion.v1_21_4));
    }

    @Test
    void theUnsupportedVersionMessageNamesTheSupportedVersions() {
        SecondRoom room = secondRoomSupporting("26.2", "26.3");
        PlayerMock player = vanillaPlayer();

        room.sendChat(player);

        Component message = player.nextComponentMessage();
        assertNotNull(message, "a message was sent");
        String text = PlainTextComponentSerializer.plainText().serialize(message);
        assertTrue(text.contains("26.2, 26.3"), text);
    }

    private SecondRoom secondRoomSupporting(String... versions) {
        plugin.getConfig().set("supportedVersions", List.of(versions));
        return reloadedSecondRoom();
    }

    private SecondRoom reloadedSecondRoom() {
        plugin.unloadData();
        plugin.loadData();
        return (SecondRoom) plugin.getSecond();
    }

    private PlayerMock vanillaPlayer() {
        // The second room picks its messages by client brand, which PlayerMock does not implement.
        PlayerMock player = new PlayerMock(server, "Newcomer") {
            @Override
            public String getClientBrandName() {
                return "vanilla";
            }
        };
        server.addPlayer(player);
        return player;
    }
}
