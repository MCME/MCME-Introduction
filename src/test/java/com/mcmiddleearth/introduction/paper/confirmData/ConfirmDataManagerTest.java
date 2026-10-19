package com.mcmiddleearth.introduction.paper.confirmData;

import com.mcmiddleearth.introduction.paper.PluginTestBase;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With the database, a player's /confirm choices are read when they log in, on every server. No
 * database runs in the tests: a fake connector hands out the rows.
 */
class ConfirmDataManagerTest extends PluginTestBase {

    private final UUID player = UUID.randomUUID();

    @Test
    void aRowSetsBothChoices() {
        ConfirmDataManager manager = new ConfirmDataManager(new FakeDatabaseConnector(null));
        manager.confirmIgnore(player);
        manager.confirmOptifine(player);

        // /confirm reset on another server since.
        manager.applyRow(new DatabaseConnector.ConfirmRow(player.toString(), false, false));

        assertFalse(manager.hasConfirmedIgnore(player), "ignore");
        assertFalse(manager.hasConfirmedOptifine(player), "optifine");
    }

    @Test
    void noRowKeepsTheChoicesKnownHere() {
        ConfirmDataManager manager = new ConfirmDataManager(new FakeDatabaseConnector(null));
        manager.confirmIgnore(player);

        manager.loadPlayerData(player.toString());

        assertTrue(manager.hasConfirmedIgnore(player));
    }

    @Test
    void theRowIsReadBeforeLoginGoesOn() {
        // Read in the pre-login event, which runs off the main thread: the player must not join
        // before the row is there, or a confirmed player gets the room again.
        FakeDatabaseConnector database = new FakeDatabaseConnector(new DatabaseConnector.ConfirmRow(player.toString(), false, true));
        ConfirmDataManager manager = new ConfirmDataManager(database);

        manager.loadPlayerData(player.toString());

        assertEquals(Thread.currentThread(), database.readOn(), "the thread that read the row");
        assertTrue(manager.hasConfirmedIgnore(player));
    }

    @Test
    void aConfirmationWritesOnlyItsOwnChoice() throws InterruptedException {
        // The other choice, as this server knows it, may be out of date: another server may have
        // set it since the player logged in.
        FakeDatabaseConnector database = new FakeDatabaseConnector(null);
        ConfirmDataManager manager = new ConfirmDataManager(database);

        manager.confirmIgnore(player);
        assertEquals("ignore=true", database.nextWrite());
        manager.confirmOptifine(player);
        assertEquals("optifine=true", database.nextWrite());

        assertFalse(database.moreWrites(), "more writes");
    }

    @Test
    void aResetWritesBothChoices() throws InterruptedException {
        FakeDatabaseConnector database = new FakeDatabaseConnector(null);
        ConfirmDataManager manager = new ConfirmDataManager(database);

        manager.resetConfirmations(player.toString());

        assertEquals("optifine=false,ignore=false", database.nextWrite());
    }
}
