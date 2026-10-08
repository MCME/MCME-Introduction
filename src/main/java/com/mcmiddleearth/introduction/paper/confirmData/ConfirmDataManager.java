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
        this.databaseConnector = new DatabaseConnector();
        if (this.databaseConnector.isEnabled()) {
            this.databaseConnector.initialize();
            IntroductionPlugin.getInstance().getLogger().info("ConfirmDataManager: DatabaseConnector initialized synchronously.");
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
     * Intended to be called from an async context after reading a row from DB.
     */
    public void applyRow(DatabaseConnector.ConfirmRow row) {
        if (row == null) return;
        if (row.optifine) optifineConfirmed.add(row.uuid);
        if (row.ignore) ignoreConfirmed.add(row.uuid);
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
        // persist async
        if (databaseConnector.isInitialized()) {
            boolean ignore = ignoreConfirmed.contains(playerUUID.toString());
            Bukkit.getScheduler().runTaskAsynchronously(IntroductionPlugin.getInstance(),
                    () -> databaseConnector.upsertRow(playerUUID.toString(), true, ignore));
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
        // persist async
        if (databaseConnector.isInitialized()) {
            boolean optifine = optifineConfirmed.contains(playerUUID.toString());
            Bukkit.getScheduler().runTaskAsynchronously(IntroductionPlugin.getInstance(),
                    () -> databaseConnector.upsertRow(playerUUID.toString(), optifine, true));
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

    public void loadPlayerData(String uuid) {
//Logger.getGlobal().info("Confirm: load player data");
        if (databaseConnector == null || !databaseConnector.isInitialized()) return;
        Bukkit.getScheduler().runTaskAsynchronously(IntroductionPlugin.getInstance(), () ->
        {
            DatabaseConnector.ConfirmRow row = databaseConnector.readRow(uuid);
            if (row != null) {
                applyRow(row);
//Logger.getGlobal().info("Confirm: done");
            }
        });
    }
}
