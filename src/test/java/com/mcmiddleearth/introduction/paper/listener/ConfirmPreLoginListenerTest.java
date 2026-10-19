package com.mcmiddleearth.introduction.paper.listener;

import com.mcmiddleearth.introduction.paper.PluginTestBase;
import com.mcmiddleearth.introduction.paper.confirmData.ConfirmDataManager;
import com.mcmiddleearth.introduction.paper.confirmData.DatabaseConnector;
import com.mcmiddleearth.introduction.paper.confirmData.FakeDatabaseConnector;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The server holds a login until the pre-login event is done, and the event already runs off the
 * main thread. The listener reads the player's /confirm choices right there, so they are known
 * when the player joins; read later, a confirmed player would get the compatibility room again.
 */
class ConfirmPreLoginListenerTest extends PluginTestBase {

    @Test
    void theChoicesAreKnownWhenThePreLoginEventIsDone() {
        UUID uuid = UUID.randomUUID();
        FakeDatabaseConnector database = new FakeDatabaseConnector(new DatabaseConnector.ConfirmRow(uuid.toString(), false, true));
        ConfirmDataManager manager = new ConfirmDataManager(database);
        ConfirmPreLoginListener listener = new ConfirmPreLoginListener(manager);

        listener.onPreLogin(new AsyncPlayerPreLoginEvent("Newcomer", InetAddress.getLoopbackAddress(), uuid));

        assertTrue(manager.hasConfirmedIgnore(uuid), "the choice is known");
        assertEquals(Thread.currentThread(), database.readOn(), "the thread that read the row");
    }
}
