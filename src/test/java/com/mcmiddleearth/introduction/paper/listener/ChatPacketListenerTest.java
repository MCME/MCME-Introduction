package com.mcmiddleearth.introduction.paper.listener;

import com.comphenix.protocol.PacketType;
import com.mcmiddleearth.introduction.paper.IntroductionPlugin;
import com.mcmiddleearth.introduction.paper.PluginTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests which chat messages are hidden from players in an introduction room.
 * <p>
 * ProtocolLib's packet classes look up CraftBukkit when they load, so they cannot be created or
 * mocked under MockBukkit. These tests therefore cover the decision, given the message JSON that
 * the listener reads from the packet.
 */
class ChatPacketListenerTest extends PluginTestBase {

    private static final String INTRO_MESSAGE =
            "{\"text\":\"Welcome\",\"insertion\":\"" + IntroductionPlugin.CHANNEL + "\"}";
    private static final String OTHER_MESSAGE = "{\"text\":\"Hello everyone\"}";

    private PlayerMock silencedPlayer;
    private PlayerMock otherPlayer;

    @BeforeEach
    void addPlayers() {
        silencedPlayer = server.addPlayer();
        otherPlayer = server.addPlayer();
        ChatPacketListener.silence(silencedPlayer);
    }

    @Test
    void onlySystemChatIsHidden() {
        // Since 1.21.5 the client numbers the player chat messages it receives and disconnects on a
        // gap, so dropping one disconnects the player at the next. System chat carries no number.
        Set<PacketType> types = new ChatPacketListener().getSendingWhitelist().getTypes();

        // (PacketType.toString needs Minecraft's classes, so no assertion may print a type.)
        assertFalse(types.contains(PacketType.Play.Server.CHAT), "listens to player chat");
        assertTrue(types.contains(PacketType.Play.Server.SYSTEM_CHAT), "listens to system chat");
        assertEquals(1, types.size(), "packet types listened to");
    }

    @Test
    void chatWithoutAReadableMessageIsBlockedForSilencedPlayers() {
        // When the listener can't read the message, it can't be one of the plugin's own.
        assertTrue(ChatPacketListener.isBlocked(silencedPlayer.getUniqueId(), null));
    }

    @Test
    void otherMessagesAreBlockedForSilencedPlayers() {
        assertTrue(ChatPacketListener.isBlocked(silencedPlayer.getUniqueId(), OTHER_MESSAGE));
    }

    @Test
    void introMessagesStillReachSilencedPlayers() {
        assertFalse(ChatPacketListener.isBlocked(silencedPlayer.getUniqueId(), INTRO_MESSAGE));
    }

    @Test
    void nothingIsBlockedForOtherPlayers() {
        assertFalse(ChatPacketListener.isBlocked(otherPlayer.getUniqueId(), OTHER_MESSAGE));
        assertFalse(ChatPacketListener.isBlocked(otherPlayer.getUniqueId(), null));
    }
}
