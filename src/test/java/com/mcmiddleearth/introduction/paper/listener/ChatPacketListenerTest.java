package com.mcmiddleearth.introduction.paper.listener;

import com.mcmiddleearth.introduction.paper.IntroductionPlugin;
import com.mcmiddleearth.introduction.paper.PluginTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

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
    void chatWithoutAReadableMessageIsBlockedForSilencedPlayers() {
        // Player chat packets usually carry no unsigned component, so the listener reads null.
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
