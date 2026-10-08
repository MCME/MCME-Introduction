package com.mcmiddleearth.introduction.paper.confirmData;

import com.mcmiddleearth.introduction.paper.IntroductionPlugin;
import org.bukkit.Bukkit;

import java.sql.*;
import java.util.logging.Level;

/**
 * Simple MySQL connector that reads credentials from the plugin config and
 * offers basic methods to read/write confirmation flags.
 *
 * Expected config keys (all optional, sensible defaults used):
 * database.enabled, database.host, database.port, database.database, database.user, database.password
 */
public class DatabaseConnector {

    private final String url;
    private final String user;
    private final String password;

    private volatile boolean initialized = false;

    public DatabaseConnector() {
        // Read config from plugin
        var cfg = IntroductionPlugin.getInstance().getConfig();
        boolean enabled = cfg.getBoolean("database.enabled", false);
        if (!enabled) {
            IntroductionPlugin.getInstance().getLogger().fine("MySQL database disabled in config (database.enabled=false); DB disabled.");
            this.url = null;
            this.user = null;
            this.password = null;
            this.initialized = false;
            return;
        }

        String host = cfg.getString("database.host", "localhost");
        int port = cfg.getInt("database.port", 3306);
        String database = cfg.getString("database.database", "minecraft");
        this.user = cfg.getString("database.user", "root");
        this.password = cfg.getString("database.password", "");
        this.url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&autoReconnect=true", host, port, database);
        // Note: actual initialization (table creation) is performed via initialize() synchronously during startup
    }

    /**
     * Returns true when the connector was enabled in config (not necessarily initialized yet).
     */
    public boolean isEnabled() {
        return this.url != null;
    }

    /**
     * Initialize the connector synchronously (creates table if needed).
     * This method is intended to be called on the main server thread during plugin startup.
     */
    public void initialize() {
        if (!isEnabled()) return;
        try {
            ensureTable();
            initialized = true;
            IntroductionPlugin.getInstance().getLogger().info("DatabaseConnector initialized (MySQL enabled).");
        } catch (Exception e) {
            initialized = false;
            IntroductionPlugin.getInstance().getLogger().log(Level.WARNING, "Failed to initialize DatabaseConnector: " + e.getMessage(), e);
        }
    }

    public boolean isInitialized() {
        return initialized;
    }

    private void ensureNotMainThread() {
        if (Bukkit.isPrimaryThread()) {
            String msg = "Database access must not be performed on the main Bukkit thread; run in an async task.";
            IntroductionPlugin.getInstance().getLogger().severe(msg);
            throw new IllegalStateException(msg);
        }
    }

    /**
     * Ensure the confirmation table exists. Synchronous method; caller may run on main thread during initialize().
     */
    public void ensureTable() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS confirm_data ("
                + "player_uuid VARCHAR(36) NOT NULL PRIMARY KEY,"
                + "optifine TINYINT(1) DEFAULT 0,"
                + "ignore_confirmed TINYINT(1) DEFAULT 0"
                + ")";
        try (var conn = DriverManager.getConnection(url, user, password); Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    /**
     * Read a player's confirmation row. If no row exists, returns null.
     * Synchronous method; caller must run async. Throws IllegalStateException on main thread.
     */
    public ConfirmRow readRow(String playerUUID) {
        ensureNotMainThread();
        if (playerUUID == null || !initialized) return null;
        String sql = "SELECT optifine, ignore_confirmed FROM confirm_data WHERE player_uuid = ?";
        try (var conn = DriverManager.getConnection(url, user, password); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUUID);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    boolean optifine = rs.getInt("optifine") != 0;
                    boolean ignore = rs.getInt("ignore_confirmed") != 0;
//Logger.getGlobal().info("Confirm: read row "+optifine+", "+ignore);
                    return new ConfirmRow(playerUUID, optifine, ignore);
                }
            }
        } catch (SQLException e) {
            IntroductionPlugin.getInstance().getLogger().log(Level.WARNING, "Failed to read confirm_data row: " + e.getMessage(), e);
        }
        return null;
    }

    /**
     * Upsert a player's confirmation flags. Synchronous; caller must run async. Throws IllegalStateException on main thread.
     */
    public void upsertRow(String playerUUID, boolean optifine, boolean ignore) {
        ensureNotMainThread();
        if (playerUUID == null || !initialized) return;
        String sql = "INSERT INTO confirm_data (player_uuid, optifine, ignore_confirmed) VALUES (?,?,?) "
                + "ON DUPLICATE KEY UPDATE optifine = VALUES(optifine), ignore_confirmed = VALUES(ignore_confirmed)";
        try (var conn = DriverManager.getConnection(url, user, password); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUUID);
            ps.setInt(2, optifine ? 1 : 0);
            ps.setInt(3, ignore ? 1 : 0);
            ps.executeUpdate();
//Logger.getGlobal().info("Confirm: upsert row: "+optifine+", "+ignore);
        } catch (SQLException e) {
            IntroductionPlugin.getInstance().getLogger().log(Level.WARNING, "Failed to upsert confirm_data row: " + e.getMessage(), e);
        }
    }

    /**
     * Load all rows from the table. Synchronous; caller must run async. Throws IllegalStateException on main thread.
     */
    /*public List<ConfirmRow> readAll() {
        ensureNotMainThread();
        List<ConfirmRow> list = new ArrayList<>();
        if (!initialized) return list;
        String sql = "SELECT player_uuid, optifine, ignore_confirmed FROM confirm_data";
        try (var conn = DriverManager.getConnection(url, user, password); PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String uuid = rs.getString("player_uuid");
                boolean optifine = rs.getInt("optifine") != 0;
                boolean ignore = rs.getInt("ignore_confirmed") != 0;
                list.add(new ConfirmRow(uuid, optifine, ignore));
            }
        } catch (SQLException e) {
            IntroductionPlugin.getInstance().getLogger().log(Level.WARNING, "Failed to read all confirm_data rows: " + e.getMessage(), e);
        }
        return list;
    }*/

    /**
     * Simple value object for a DB row.
     */
    public static class ConfirmRow {
        public final String uuid;
        public final boolean optifine;
        public final boolean ignore;

        public ConfirmRow(String uuid, boolean optifine, boolean ignore) {
            this.uuid = uuid;
            this.optifine = optifine;
            this.ignore = ignore;
        }
    }
}
