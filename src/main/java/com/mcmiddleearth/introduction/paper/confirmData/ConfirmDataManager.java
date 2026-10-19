package com.mcmiddleearth.introduction.paper.confirmData;

import com.mcmiddleearth.introduction.paper.IntroductionPlugin;
import org.bukkit.Bukkit;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Thread-safe in-memory manager for storing player confirmation flags.
 * Optionally persists data via MySQL using DatabaseConnector when available.
 */
public class ConfirmDataManager {

    // Thread-safe sets backed by ConcurrentHashMap
    private final Set<String> optifineConfirmed = ConcurrentHashMap.newKeySet();
    private final Set<String> ignoreConfirmed = ConcurrentHashMap.newKeySet();

    private final DatabaseConnector databaseConnector;

    public ConfirmDataManager() {
        this(new DatabaseConnector());
    }

    // Tests hand in a connector of their own: no database runs in them.
    ConfirmDataManager(DatabaseConnector databaseConnector) {
        this.databaseConnector = databaseConnector;
        if (this.databaseConnector.isEnabled()) {
            this.databaseConnector.initialize();
            if (!this.databaseConnector.isInitialized()) {
                IntroductionPlugin.getInstance().getLogger().warning("ConfirmDataManager: without the database, "
                        + "players' /confirm choices are kept in memory on this server until it restarts.");
            }
        } else {
            IntroductionPlugin.getInstance().getLogger().fine("DatabaseConnector is not enabled; using in-memory storage only.");
        }
    }

    /**
     * Expose the DatabaseConnector so callers (listeners) can perform async reads.
     */
    public DatabaseConnector getDatabaseConnector() {
        return databaseConnector;
    }

    /**
     * Apply a database row into the in-memory cache without persisting.
     * The row wins for both choices, so a /confirm reset on another server is seen here too.
     */
    public void applyRow(DatabaseConnector.ConfirmRow row) {
        if (row == null) return;
        setChoice(optifineConfirmed, row.uuid, row.optifine);
        setChoice(ignoreConfirmed, row.uuid, row.ignore);
    }

    private static void setChoice(Set<String> confirmed, String uuid, boolean choice) {
        if (choice) {
            confirmed.add(uuid);
        } else {
            confirmed.remove(uuid);
        }
    }

    /**
     * Checks whether the player has confirmed Optifine.
     * @param playerUUID Player's UUID as a String. If null, the method returns false.
     * @return true if confirmed, otherwise false
     */
    public boolean hasConfirmedOptifine(UUID playerUUID) {
        if (playerUUID == null) return false;
        return optifineConfirmed.contains(playerUUID.toString());
    }

    /**
     * Checks whether the player has confirmed to ignore compatibility issues.
     * @param playerUUID Player's UUID as a String. If null, the method returns false.
     * @return true if confirmed, otherwise false
     */
    public boolean hasConfirmedIgnore(UUID playerUUID) {
        if (playerUUID == null) return false;
        return ignoreConfirmed.contains(playerUUID.toString());
    }

    /**
     * Marks the player as having confirmed Optifine.
     * Persists to DB if available (async).
     * @param playerUUID Player's UUID as a String. If null, nothing happens.
     */
    public void confirmOptifine(UUID playerUUID) {
        if (playerUUID == null) return;
        optifineConfirmed.add(playerUUID.toString());
        // persist async; only this choice, as the other one may have changed on another server
        if (databaseConnector.isInitialized()) {
            Bukkit.getScheduler().runTaskAsynchronously(IntroductionPlugin.getInstance(),
                    () -> databaseConnector.saveOptifine(playerUUID.toString(), true));
        }
    }

    /**
     * Marks the player as having confirmed to ignore compatibility issues.
     * Persists to DB if available (async).
     * @param playerUUID Player's UUID as a String. If null, nothing happens.
     */
    public void confirmIgnore(UUID playerUUID) {
        if (playerUUID == null) return;
        ignoreConfirmed.add(playerUUID.toString());
        // persist async; only this choice, as the other one may have changed on another server
        if (databaseConnector.isInitialized()) {
            Bukkit.getScheduler().runTaskAsynchronously(IntroductionPlugin.getInstance(),
                    () -> databaseConnector.saveIgnore(playerUUID.toString(), true));
        }
    }

    /**
     * Clears both optifine and ignore confirmations for a player (in-memory + persistent).
     * DB write is performed asynchronously (upsert with false,false) if DB is initialized.
     */
    public void resetConfirmations(String playerUUID) {
        if (playerUUID == null) return;
        optifineConfirmed.remove(playerUUID);
        ignoreConfirmed.remove(playerUUID);
        if (databaseConnector.isInitialized()) {
            Bukkit.getScheduler().runTaskAsynchronously(IntroductionPlugin.getInstance(), () -> databaseConnector.upsertRow(playerUUID, false, false));
        }
    }

    /**
     * Reads the player's row and applies it, before returning: called in the pre-login event, so
     * the choices are known when the player joins. Blocks, so never call it on the main thread.
     */
    public void loadPlayerData(String uuid) {
//Logger.getGlobal().info("Confirm: load player data");
        if (databaseConnector == null || !databaseConnector.isInitialized()) return;
        applyRow(databaseConnector.readRow(uuid));
    }
}
