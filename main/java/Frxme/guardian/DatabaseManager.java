/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.Location
 *  org.bukkit.Material
 *  org.bukkit.block.Block
 *  org.bukkit.block.BlockState
 *  org.bukkit.block.Chest
 *  org.bukkit.block.DoubleChest
 *  org.bukkit.entity.Player
 *  org.bukkit.inventory.InventoryHolder
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.plugin.Plugin
 */
package Frxme.guardian;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public class DatabaseManager {
    private final Guardian plugin;
    private Connection connection;
    private final String dbPath;
    public static final int ACTION_BREAK = 0;
    public static final int ACTION_PLACE = 1;
    public static final int CONTAINER_ACTION_REMOVE = 0;
    public static final int CONTAINER_ACTION_ADD = 1;

    public DatabaseManager(Guardian plugin) {
        this.plugin = plugin;
        this.dbPath = plugin.getDataFolder().getAbsolutePath() + File.separator + "data.db";
    }

    public boolean connect() {
        try {
            if (this.connection != null && !this.connection.isClosed()) {
                return true;
            }
            File dataFolder = this.plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }
            Class.forName("org.sqlite.JDBC");
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + this.dbPath);
            this.plugin.getLogger().info("SQLite Datenbankverbindung hergestellt.");
            return true;
        } catch (ClassNotFoundException | SQLException e) {
            this.plugin.getLogger().log(Level.SEVERE, "Konnte keine Verbindung zur SQLite Datenbank herstellen!", e);
            return false;
        }
    }

    public void disconnect() {
        try {
            if (this.connection != null && !this.connection.isClosed()) {
                this.connection.close();
                this.plugin.getLogger().info("SQLite Datenbankverbindung geschlossen.");
            }
        } catch (SQLException e) {
            this.plugin.getLogger().log(Level.SEVERE, "Fehler beim Schlie\u00dfen der SQLite Datenbankverbindung!", e);
        }
    }

    public boolean initializeDatabase() {
        boolean blockLogSuccess = this.initializeBlockLogTable();
        boolean containerLogSuccess = this.initializeContainerLogTable();
        boolean webUsersSuccess = this.initializeWebUsersTable();
        boolean registrationCodesSuccess = this.initializeRegistrationCodesTable();
        return blockLogSuccess && containerLogSuccess && webUsersSuccess && registrationCodesSuccess;
    }

    private boolean initializeBlockLogTable() {
        String sql = "CREATE TABLE IF NOT EXISTS block_log (id INTEGER PRIMARY KEY AUTOINCREMENT,timestamp INTEGER NOT NULL,player_uuid VARCHAR(36) NOT NULL,player_name VARCHAR(16) NOT NULL,action INTEGER NOT NULL,world VARCHAR(255) NOT NULL,x INTEGER NOT NULL,y INTEGER NOT NULL,z INTEGER NOT NULL,block_type VARCHAR(255) NOT NULL,old_block_type VARCHAR(255));";
        try (Statement stmt = this.connection.createStatement()) {
            stmt.execute(sql);
            this.plugin.getLogger().info("Datenbank Tabelle 'block_log' erfolgreich initialisiert/geprüft.");
            return true;
        } catch (SQLException e) {
            this.plugin.getLogger().log(Level.SEVERE, "Konnte Datenbank Tabelle 'block_log' nicht initialisieren!", e);
            return false;
        }
    }

    private boolean initializeContainerLogTable() {
        String sql = "CREATE TABLE IF NOT EXISTS container_log (id INTEGER PRIMARY KEY AUTOINCREMENT,timestamp INTEGER NOT NULL,player_uuid VARCHAR(36),player_name VARCHAR(16),action INTEGER NOT NULL,world VARCHAR(255) NOT NULL,x INTEGER NOT NULL,y INTEGER NOT NULL,z INTEGER NOT NULL,item_material VARCHAR(255) NOT NULL,item_amount INTEGER NOT NULL);";
        try (Statement stmt = this.connection.createStatement()) {
            stmt.execute(sql);
            this.plugin.getLogger().info("Datenbank Tabelle 'container_log' erfolgreich initialisiert/geprüft.");
            return true;
        } catch (SQLException e) {
            this.plugin.getLogger().log(Level.SEVERE, "Konnte Datenbank Tabelle 'container_log' nicht initialisieren!",
                    e);
            return false;
        }
    }

    private boolean initializeWebUsersTable() {
        String sql = "CREATE TABLE IF NOT EXISTS web_users (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "username VARCHAR(32) UNIQUE NOT NULL," +
                "password_hash VARCHAR(255) NOT NULL," +
                "minecraft_uuid VARCHAR(36) UNIQUE NOT NULL," +
                "minecraft_name VARCHAR(16) NOT NULL," +
                "role VARCHAR(16) NOT NULL," +
                "created_at INTEGER NOT NULL," +
                "last_login INTEGER);";
        try (Statement stmt = this.connection.createStatement()) {
            stmt.execute(sql);
            this.plugin.getLogger().info("Datenbank Tabelle 'web_users' erfolgreich initialisiert/geprüft.");
            return true;
        } catch (SQLException e) {
            this.plugin.getLogger().log(Level.SEVERE, "Konnte Datenbank Tabelle 'web_users' nicht initialisieren!", e);
            return false;
        }
    }

    private boolean initializeRegistrationCodesTable() {
        String sql = "CREATE TABLE IF NOT EXISTS registration_codes (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "code VARCHAR(8) UNIQUE NOT NULL," +
                "minecraft_uuid VARCHAR(36) NOT NULL," +
                "minecraft_name VARCHAR(16) NOT NULL," +
                "role VARCHAR(16) NOT NULL," +
                "created_at INTEGER NOT NULL," +
                "expires_at INTEGER NOT NULL," +
                "used BOOLEAN DEFAULT FALSE);";
        try (Statement stmt = this.connection.createStatement()) {
            stmt.execute(sql);
            this.plugin.getLogger().info("Datenbank Tabelle 'registration_codes' erfolgreich initialisiert/geprüft.");
            return true;
        } catch (SQLException e) {
            this.plugin.getLogger().log(Level.SEVERE,
                    "Konnte Datenbank Tabelle 'registration_codes' nicht initialisieren!", e);
            return false;
        }
    }

    // Web User Management Methods

    public String createRegistrationCode(UUID playerUUID, String playerName, String role) {
        // First, invalidate any existing unused codes for this player
        String deleteSql = "DELETE FROM registration_codes WHERE minecraft_uuid = ? AND used = FALSE";
        try (PreparedStatement pstmt = this.connection.prepareStatement(deleteSql)) {
            pstmt.setString(1, playerUUID.toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            this.plugin.getLogger().log(Level.WARNING, "Fehler beim Löschen alter Registrierungscodes!", e);
        }

        // Generate a random 8-character alphanumeric code
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder code = new StringBuilder();
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < 8; i++) {
            code.append(chars.charAt(random.nextInt(chars.length())));
        }

        long now = System.currentTimeMillis() / 1000L;
        long expiresAt = now + (10 * 60); // 10 minutes

        String sql = "INSERT INTO registration_codes (code, minecraft_uuid, minecraft_name, role, created_at, expires_at, used) VALUES (?, ?, ?, ?, ?, ?, FALSE)";
        try (PreparedStatement pstmt = this.connection.prepareStatement(sql)) {
            pstmt.setString(1, code.toString());
            pstmt.setString(2, playerUUID.toString());
            pstmt.setString(3, playerName);
            pstmt.setString(4, role);
            pstmt.setLong(5, now);
            pstmt.setLong(6, expiresAt);
            pstmt.executeUpdate();
            return code.toString();
        } catch (SQLException e) {
            this.plugin.getLogger().log(Level.SEVERE, "Fehler beim Erstellen des Registrierungscodes!", e);
            return null;
        }
    }

    public RegistrationCodeInfo validateRegistrationCode(String code) {
        String sql = "SELECT minecraft_uuid, minecraft_name, role, expires_at FROM registration_codes WHERE code = ? AND used = FALSE";
        try (PreparedStatement pstmt = this.connection.prepareStatement(sql)) {
            pstmt.setString(1, code.toUpperCase());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                long expiresAt = rs.getLong("expires_at");
                long now = System.currentTimeMillis() / 1000L;
                if (now > expiresAt) {
                    return null; // Code expired
                }
                return new RegistrationCodeInfo(
                        rs.getString("minecraft_uuid"),
                        rs.getString("minecraft_name"),
                        rs.getString("role"));
            }
        } catch (SQLException e) {
            this.plugin.getLogger().log(Level.SEVERE, "Fehler beim Validieren des Registrierungscodes!", e);
        }
        return null;
    }

    public void markRegistrationCodeUsed(String code) {
        String sql = "UPDATE registration_codes SET used = TRUE WHERE code = ?";
        try (PreparedStatement pstmt = this.connection.prepareStatement(sql)) {
            pstmt.setString(1, code.toUpperCase());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            this.plugin.getLogger().log(Level.SEVERE, "Fehler beim Markieren des Registrierungscodes als verwendet!",
                    e);
        }
    }

    public boolean createWebUser(String username, String passwordHash, String minecraftUUID, String minecraftName,
            String role) {
        long now = System.currentTimeMillis() / 1000L;
        String sql = "INSERT INTO web_users (username, password_hash, minecraft_uuid, minecraft_name, role, created_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = this.connection.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, passwordHash);
            pstmt.setString(3, minecraftUUID);
            pstmt.setString(4, minecraftName);
            pstmt.setString(5, role);
            pstmt.setLong(6, now);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            this.plugin.getLogger().log(Level.SEVERE, "Fehler beim Erstellen des Web-Benutzers!", e);
            return false;
        }
    }

    public WebUser getWebUserByUsername(String username) {
        String sql = "SELECT id, username, password_hash, minecraft_uuid, minecraft_name, role, created_at, last_login FROM web_users WHERE username = ?";
        try (PreparedStatement pstmt = this.connection.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new WebUser(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getString("minecraft_uuid"),
                        rs.getString("minecraft_name"),
                        rs.getString("role"),
                        rs.getLong("created_at"),
                        rs.getLong("last_login"));
            }
        } catch (SQLException e) {
            this.plugin.getLogger().log(Level.SEVERE, "Fehler beim Abrufen des Web-Benutzers!", e);
        }
        return null;
    }

    public boolean isMinecraftAccountLinked(UUID uuid) {
        String sql = "SELECT COUNT(*) FROM web_users WHERE minecraft_uuid = ?";
        try (PreparedStatement pstmt = this.connection.prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            this.plugin.getLogger().log(Level.SEVERE, "Fehler beim Prüfen der Minecraft-Kontoverknüpfung!", e);
        }
        return false;
    }

    public void updateLastLogin(int userId) {
        long now = System.currentTimeMillis() / 1000L;
        String sql = "UPDATE web_users SET last_login = ? WHERE id = ?";
        try (PreparedStatement pstmt = this.connection.prepareStatement(sql)) {
            pstmt.setLong(1, now);
            pstmt.setInt(2, userId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            this.plugin.getLogger().log(Level.SEVERE, "Fehler beim Aktualisieren des letzten Logins!", e);
        }
    }

    public List<WebUser> getAllWebUsers() {
        List<WebUser> users = new ArrayList<>();
        String sql = "SELECT id, username, password_hash, minecraft_uuid, minecraft_name, role, created_at, last_login FROM web_users ORDER BY created_at DESC";
        try (Statement stmt = this.connection.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                users.add(new WebUser(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getString("minecraft_uuid"),
                        rs.getString("minecraft_name"),
                        rs.getString("role"),
                        rs.getLong("created_at"),
                        rs.getLong("last_login")));
            }
        } catch (SQLException e) {
            this.plugin.getLogger().log(Level.SEVERE, "Fehler beim Abrufen aller Web-Benutzer!", e);
        }
        return users;
    }

    public boolean deleteWebUser(int userId) {
        String sql = "DELETE FROM web_users WHERE id = ?";
        try (PreparedStatement pstmt = this.connection.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            this.plugin.getLogger().log(Level.SEVERE, "Fehler beim Löschen des Web-Benutzers!", e);
            return false;
        }
    }

    public List<String> getDistinctWorlds() {
        List<String> worlds = new ArrayList<>();
        String sql = "SELECT DISTINCT world FROM block_log UNION SELECT DISTINCT world FROM container_log ORDER BY world";
        try (Statement stmt = this.connection.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                worlds.add(rs.getString("world"));
            }
        } catch (SQLException e) {
            this.plugin.getLogger().log(Level.SEVERE, "Fehler beim Abrufen der Welten!", e);
        }
        return worlds;
    }

    // Statistics methods for admin dashboard
    public ServerStats getServerStats() {
        ServerStats stats = new ServerStats();
        try {
            // Total blocks placed/broken
            String blockCountSql = "SELECT action, COUNT(*) as count FROM block_log GROUP BY action";
            try (Statement stmt = this.connection.createStatement();
                    ResultSet rs = stmt.executeQuery(blockCountSql)) {
                while (rs.next()) {
                    if (rs.getInt("action") == ACTION_BREAK) {
                        stats.totalBlocksBroken = rs.getInt("count");
                    } else if (rs.getInt("action") == ACTION_PLACE) {
                        stats.totalBlocksPlaced = rs.getInt("count");
                    }
                }
            }

            // Total container actions
            String containerCountSql = "SELECT action, COUNT(*) as count FROM container_log GROUP BY action";
            try (Statement stmt = this.connection.createStatement();
                    ResultSet rs = stmt.executeQuery(containerCountSql)) {
                while (rs.next()) {
                    if (rs.getInt("action") == CONTAINER_ACTION_REMOVE) {
                        stats.totalItemsRemoved = rs.getInt("count");
                    } else if (rs.getInt("action") == CONTAINER_ACTION_ADD) {
                        stats.totalItemsAdded = rs.getInt("count");
                    }
                }
            }

            // Unique players
            String uniquePlayersSql = "SELECT COUNT(DISTINCT player_uuid) as count FROM block_log";
            try (Statement stmt = this.connection.createStatement();
                    ResultSet rs = stmt.executeQuery(uniquePlayersSql)) {
                if (rs.next()) {
                    stats.uniquePlayers = rs.getInt("count");
                }
            }

            // Top 5 active players (by block actions)
            String topPlayersSql = "SELECT player_name, COUNT(*) as count FROM block_log GROUP BY player_uuid ORDER BY count DESC LIMIT 5";
            try (Statement stmt = this.connection.createStatement();
                    ResultSet rs = stmt.executeQuery(topPlayersSql)) {
                while (rs.next()) {
                    stats.topPlayers.add(new PlayerActivity(rs.getString("player_name"), rs.getInt("count")));
                }
            }
        } catch (SQLException e) {
            this.plugin.getLogger().log(Level.SEVERE, "Fehler beim Abrufen der Server-Statistiken!", e);
        }
        return stats;
    }

    public CompletableFuture<List<TimelineEntry>> getTimelineStats(long from, long to) {
        CompletableFuture<List<TimelineEntry>> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously((Plugin) this.plugin, () -> {
            List<TimelineEntry> results = new ArrayList<>();
            // We join block_log and container_log counts by time slot
            // SQLite specific syntax for grouping by Hour
            String sql = "SELECT time_slots.slot, " +
                    "COALESCE(b.count, 0) as blocks, " +
                    "COALESCE(c.count, 0) as containers " +
                    "FROM (" +
                    "  SELECT DISTINCT strftime('%Y-%m-%d %H:00', datetime(timestamp, 'unixepoch')) as slot " +
                    "  FROM block_log WHERE timestamp BETWEEN ? AND ? " +
                    "  UNION " +
                    "  SELECT DISTINCT strftime('%Y-%m-%d %H:00', datetime(timestamp, 'unixepoch')) as slot " +
                    "  FROM container_log WHERE timestamp BETWEEN ? AND ? " +
                    ") time_slots " +
                    "LEFT JOIN (" +
                    "  SELECT strftime('%Y-%m-%d %H:00', datetime(timestamp, 'unixepoch')) as slot, COUNT(*) as count "
                    +
                    "  FROM block_log WHERE timestamp BETWEEN ? AND ? GROUP BY slot" +
                    ") b ON time_slots.slot = b.slot " +
                    "LEFT JOIN (" +
                    "  SELECT strftime('%Y-%m-%d %H:00', datetime(timestamp, 'unixepoch')) as slot, COUNT(*) as count "
                    +
                    "  FROM container_log WHERE timestamp BETWEEN ? AND ? GROUP BY slot" +
                    ") c ON time_slots.slot = c.slot " +
                    "ORDER BY time_slots.slot ASC";

            try (PreparedStatement pstmt = this.connection.prepareStatement(sql)) {
                pstmt.setLong(1, from);
                pstmt.setLong(2, to);
                pstmt.setLong(3, from);
                pstmt.setLong(4, to);
                pstmt.setLong(5, from);
                pstmt.setLong(6, to);
                pstmt.setLong(7, from);
                pstmt.setLong(8, to);

                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    results.add(new TimelineEntry(
                            rs.getString("slot"),
                            rs.getInt("blocks"),
                            rs.getInt("containers")));
                }
                future.complete(results);
            } catch (SQLException e) {
                this.plugin.getLogger().log(Level.SEVERE, "Fehler beim Abrufen der Timeline-Statistiken!", e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    // Synchronous version for BlueMap integration
    public List<ChunkActivity> getChunkActivityData() {
        List<ChunkActivity> results = new ArrayList<>();
        // Last 24 hours
        long since = (System.currentTimeMillis() / 1000) - 86400;

        String sql = "SELECT world, (x / 16) as chunk_x, (z / 16) as chunk_z, COUNT(*) as count " +
                "FROM block_log WHERE timestamp >= ? " +
                "GROUP BY world, chunk_x, chunk_z " +
                "ORDER BY count DESC LIMIT 100";

        try (PreparedStatement pstmt = this.connection.prepareStatement(sql)) {
            pstmt.setLong(1, since);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                results.add(new ChunkActivity(
                        rs.getString("world"),
                        rs.getInt("chunk_x"),
                        rs.getInt("chunk_z"),
                        rs.getInt("count")));
            }
        } catch (SQLException e) {
            this.plugin.getLogger().log(Level.SEVERE, "Fehler beim Abrufen der Chunk-Aktivität!", e);
        }
        return results;
    }

    public CompletableFuture<List<HeatmapEntry>> getHeatmapData(long since) {
        CompletableFuture<List<HeatmapEntry>> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously((Plugin) this.plugin, () -> {
            List<HeatmapEntry> results = new ArrayList<>();
            // Group by Chunk (x/16, z/16)
            String sql = "SELECT world, (x / 16) as chunk_x, (z / 16) as chunk_z, COUNT(*) as count " +
                    "FROM block_log WHERE timestamp >= ? " +
                    "GROUP BY world, chunk_x, chunk_z " +
                    "ORDER BY count DESC LIMIT 50";

            try (PreparedStatement pstmt = this.connection.prepareStatement(sql)) {
                pstmt.setLong(1, since);
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    results.add(new HeatmapEntry(
                            rs.getString("world"),
                            rs.getInt("chunk_x"),
                            rs.getInt("chunk_z"),
                            rs.getInt("count")));
                }
                future.complete(results);
            } catch (SQLException e) {
                this.plugin.getLogger().log(Level.SEVERE, "Fehler beim Abrufen der Heatmap-Daten!", e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public CompletableFuture<List<SuspiciousEntry>> getSuspiciousPlayers(long since) {
        CompletableFuture<List<SuspiciousEntry>> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously((Plugin) this.plugin, () -> {
            List<SuspiciousEntry> results = new ArrayList<>();
            // > 100 blocks broken and > 5% diamond/debris
            String sql = "SELECT player_name, " +
                    "COUNT(*) as total_broken, " +
                    "SUM(CASE WHEN block_type LIKE '%DIAMOND_ORE%' THEN 1 ELSE 0 END) as diamonds, " +
                    "SUM(CASE WHEN block_type LIKE '%ANCIENT_DEBRIS%' THEN 1 ELSE 0 END) as debris " +
                    "FROM block_log WHERE action = 0 AND timestamp >= ? " +
                    "GROUP BY player_name " +
                    "HAVING total_broken > 100 AND " +
                    "((CAST(diamonds AS FLOAT) / total_broken) > 0.05 OR (CAST(debris AS FLOAT) / total_broken) > 0.05) "
                    +
                    "ORDER BY diamonds DESC";

            try (PreparedStatement pstmt = this.connection.prepareStatement(sql)) {
                pstmt.setLong(1, since);
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    results.add(new SuspiciousEntry(
                            rs.getString("player_name"),
                            rs.getInt("total_broken"),
                            rs.getInt("diamonds"),
                            rs.getInt("debris")));
                }
                future.complete(results);
            } catch (SQLException e) {
                this.plugin.getLogger().log(Level.SEVERE, "Fehler beim Abrufen der verdächtigen Spieler!", e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    // Extended search for web interface
    public CompletableFuture<List<BlockLogEntry>> searchBlockLogs(String playerName, String world, Long fromTime,
            Long toTime, int page, int limit) {
        CompletableFuture<List<BlockLogEntry>> future = new CompletableFuture<>();
        int offset = (page - 1) * limit;

        Bukkit.getScheduler().runTaskAsynchronously((Plugin) this.plugin, () -> {
            List<BlockLogEntry> results = new ArrayList<>();
            StringBuilder sql = new StringBuilder(
                    "SELECT timestamp, player_name, action, block_type, old_block_type, world, x, y, z FROM block_log WHERE 1=1");
            List<Object> params = new ArrayList<>();

            if (playerName != null && !playerName.isEmpty()) {
                sql.append(" AND player_name LIKE ?");
                params.add("%" + playerName + "%");
            }
            if (world != null && !world.isEmpty()) {
                sql.append(" AND world = ?");
                params.add(world);
            }
            if (fromTime != null) {
                sql.append(" AND timestamp >= ?");
                params.add(fromTime);
            }
            if (toTime != null) {
                sql.append(" AND timestamp <= ?");
                params.add(toTime);
            }

            sql.append(" ORDER BY timestamp DESC LIMIT ? OFFSET ?");
            params.add(limit);
            params.add(offset);

            try (PreparedStatement pstmt = this.connection.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    Object param = params.get(i);
                    if (param instanceof String) {
                        pstmt.setString(i + 1, (String) param);
                    } else if (param instanceof Long) {
                        pstmt.setLong(i + 1, (Long) param);
                    } else if (param instanceof Integer) {
                        pstmt.setInt(i + 1, (Integer) param);
                    }
                }

                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    results.add(new BlockLogEntry(
                            rs.getLong("timestamp"),
                            rs.getString("player_name"),
                            rs.getInt("action"),
                            rs.getString("block_type"),
                            rs.getString("old_block_type"),
                            rs.getString("world"),
                            rs.getInt("x"),
                            rs.getInt("y"),
                            rs.getInt("z")));
                }
                future.complete(results);
            } catch (SQLException e) {
                this.plugin.getLogger().log(Level.SEVERE, "Fehler bei der Block-Log-Suche!", e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public CompletableFuture<List<ContainerLogEntry>> searchContainerLogs(String playerName, String world,
            Long fromTime, Long toTime, int page, int limit) {
        CompletableFuture<List<ContainerLogEntry>> future = new CompletableFuture<>();
        int offset = (page - 1) * limit;

        Bukkit.getScheduler().runTaskAsynchronously((Plugin) this.plugin, () -> {
            List<ContainerLogEntry> results = new ArrayList<>();
            StringBuilder sql = new StringBuilder(
                    "SELECT timestamp, player_name, action, item_material, item_amount, world, x, y, z FROM container_log WHERE 1=1");
            List<Object> params = new ArrayList<>();

            if (playerName != null && !playerName.isEmpty()) {
                sql.append(" AND player_name LIKE ?");
                params.add("%" + playerName + "%");
            }
            if (world != null && !world.isEmpty()) {
                sql.append(" AND world = ?");
                params.add(world);
            }
            if (fromTime != null) {
                sql.append(" AND timestamp >= ?");
                params.add(fromTime);
            }
            if (toTime != null) {
                sql.append(" AND timestamp <= ?");
                params.add(toTime);
            }

            sql.append(" ORDER BY timestamp DESC LIMIT ? OFFSET ?");
            params.add(limit);
            params.add(offset);

            try (PreparedStatement pstmt = this.connection.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    Object param = params.get(i);
                    if (param instanceof String) {
                        pstmt.setString(i + 1, (String) param);
                    } else if (param instanceof Long) {
                        pstmt.setLong(i + 1, (Long) param);
                    } else if (param instanceof Integer) {
                        pstmt.setInt(i + 1, (Integer) param);
                    }
                }

                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    results.add(new ContainerLogEntry(
                            rs.getLong("timestamp"),
                            rs.getString("player_name"),
                            rs.getInt("action"),
                            rs.getString("item_material"),
                            rs.getInt("item_amount"),
                            rs.getString("world"),
                            rs.getInt("x"),
                            rs.getInt("y"),
                            rs.getInt("z")));
                }
                future.complete(results);
            } catch (SQLException e) {
                this.plugin.getLogger().log(Level.SEVERE, "Fehler bei der Container-Log-Suche!", e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    // Data classes for web interface
    public static class RegistrationCodeInfo {
        public final String minecraftUUID;
        public final String minecraftName;
        public final String role;

        public RegistrationCodeInfo(String minecraftUUID, String minecraftName, String role) {
            this.minecraftUUID = minecraftUUID;
            this.minecraftName = minecraftName;
            this.role = role;
        }
    }

    public static class ChunkActivity {
        public final String world;
        public final int chunkX;
        public final int chunkZ;
        public final int count;

        public ChunkActivity(String world, int chunkX, int chunkZ, int count) {
            this.world = world;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.count = count;
        }
    }

    public static class WebUser {
        public final int id;
        public final String username;
        public final String passwordHash;
        public final String minecraftUUID;
        public final String minecraftName;
        public final String role;
        public final long createdAt;
        public final long lastLogin;

        public WebUser(int id, String username, String passwordHash, String minecraftUUID, String minecraftName,
                String role, long createdAt, long lastLogin) {
            this.id = id;
            this.username = username;
            this.passwordHash = passwordHash;
            this.minecraftUUID = minecraftUUID;
            this.minecraftName = minecraftName;
            this.role = role;
            this.createdAt = createdAt;
            this.lastLogin = lastLogin;
        }
    }

    public static class ServerStats {
        public int totalBlocksBroken = 0;
        public int totalBlocksPlaced = 0;
        public int totalItemsRemoved = 0;
        public int totalItemsAdded = 0;
        public int uniquePlayers = 0;
        public List<PlayerActivity> topPlayers = new ArrayList<>();
    }

    public static class PlayerActivity {
        public final String playerName;
        public final int actionCount;
        public final int totalActions;
        public final int blocksBroken;
        public final int blocksPlaced;

        public PlayerActivity(String playerName, int actionCount) {
            this.playerName = playerName;
            this.actionCount = actionCount;
            this.totalActions = actionCount;
            this.blocksBroken = 0;
            this.blocksPlaced = 0;
        }

        public PlayerActivity(String playerName, int totalActions, int blocksBroken, int blocksPlaced) {
            this.playerName = playerName;
            this.actionCount = totalActions;
            this.totalActions = totalActions;
            this.blocksBroken = blocksBroken;
            this.blocksPlaced = blocksPlaced;
        }
    }

    public static class TimelineEntry {
        public final String timeSlot;
        public final int blockCount;
        public final int containerCount;

        public TimelineEntry(String timeSlot, int blockCount, int containerCount) {
            this.timeSlot = timeSlot;
            this.blockCount = blockCount;
            this.containerCount = containerCount;
        }
    }

    public static class HeatmapEntry {
        public final String world;
        public final int chunkX;
        public final int chunkZ;
        public final int count;

        public HeatmapEntry(String world, int chunkX, int chunkZ, int count) {
            this.world = world;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.count = count;
        }
    }

    public static class SuspiciousEntry {
        public final String playerName;
        public final int totalBroken;
        public final int diamonds;
        public final int debris;

        public SuspiciousEntry(String playerName, int totalBroken, int diamonds, int debris) {
            this.playerName = playerName;
            this.totalBroken = totalBroken;
            this.diamonds = diamonds;
            this.debris = debris;
        }
    }

    public void logBlockActionAsync(Player player, Block block, int action, Material oldMaterial) {
        UUID playerUUID = player.getUniqueId();
        String playerName = player.getName();
        long timestamp = System.currentTimeMillis() / 1000L;
        String worldName = block.getWorld().getName();
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
        String blockType = (action == 1 ? block.getType() : oldMaterial).name();
        String oldBlockType = (action == 0 ? oldMaterial : block.getType()).name();
        Bukkit.getScheduler().runTaskAsynchronously((Plugin) this.plugin, () -> {
            String sql = "INSERT INTO block_log(timestamp, player_uuid, player_name, action, world, x, y, z, block_type, old_block_type) VALUES(?,?,?,?,?,?,?,?,?,?)";
            try (PreparedStatement pstmt = this.connection.prepareStatement(sql);) {
                pstmt.setLong(1, timestamp);
                pstmt.setString(2, playerUUID.toString());
                pstmt.setString(3, playerName);
                pstmt.setInt(4, action);
                pstmt.setString(5, worldName);
                pstmt.setInt(6, x);
                pstmt.setInt(7, y);
                pstmt.setInt(8, z);
                pstmt.setString(9, blockType);
                pstmt.setString(10, action == 0 ? oldBlockType : null);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                this.plugin.getLogger().log(Level.SEVERE,
                        "Fehler beim Einf\u00fcgen eines Block-Logs in die Datenbank!", e);
            }
        });
    }

    public void logContainerActionAsync(Player player, Location containerLoc, int action, ItemStack item) {
        if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
            return;
        }
        if (containerLoc == null) {
            this.plugin.getLogger().warning("Versuch, Container-Aktion ohne g\u00fcltige Location zu loggen.");
            return;
        }
        UUID playerUUID = player != null ? player.getUniqueId() : null;
        String playerName = player != null ? player.getName() : "SYSTEM";
        long timestamp = System.currentTimeMillis() / 1000L;
        String worldName = containerLoc.getWorld().getName();
        int x = containerLoc.getBlockX();
        int y = containerLoc.getBlockY();
        int z = containerLoc.getBlockZ();
        String itemMaterial = item.getType().name();
        int itemAmount = item.getAmount();
        Bukkit.getScheduler().runTaskAsynchronously((Plugin) this.plugin, () -> {
            String sql = "INSERT INTO container_log(timestamp, player_uuid, player_name, action, world, x, y, z, item_material, item_amount) VALUES(?,?,?,?,?,?,?,?,?,?)";
            try (PreparedStatement pstmt = this.connection.prepareStatement(sql);) {
                pstmt.setLong(1, timestamp);
                if (playerUUID != null) {
                    pstmt.setString(2, playerUUID.toString());
                    pstmt.setString(3, playerName);
                } else {
                    pstmt.setNull(2, 12);
                    pstmt.setString(3, playerName);
                }
                pstmt.setInt(4, action);
                pstmt.setString(5, worldName);
                pstmt.setInt(6, x);
                pstmt.setInt(7, y);
                pstmt.setInt(8, z);
                pstmt.setString(9, itemMaterial);
                pstmt.setInt(10, itemAmount);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                this.plugin.getLogger().log(Level.SEVERE,
                        "Fehler beim Einf\u00fcgen eines Container-Logs in die Datenbank!", e);
            }
        });
    }

    public CompletableFuture<List<BlockLogEntry>> lookupBlockHistoryAsync(Location location, int page, int limit) {
        CompletableFuture<List<BlockLogEntry>> future = new CompletableFuture<List<BlockLogEntry>>();
        if (location == null) {
            future.completeExceptionally(new IllegalArgumentException("Location cannot be null"));
            return future;
        }
        String worldName = location.getWorld().getName();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        int offset = (page - 1) * limit;
        Bukkit.getScheduler().runTaskAsynchronously((Plugin) this.plugin, () -> {
            ArrayList<BlockLogEntry> results = new ArrayList<BlockLogEntry>();
            String sql = "SELECT timestamp, player_name, action, block_type, old_block_type FROM block_log WHERE world = ? AND x = ? AND y = ? AND z = ? ORDER BY timestamp DESC LIMIT ? OFFSET ?";
            try (PreparedStatement pstmt = this.connection.prepareStatement(sql);) {
                pstmt.setString(1, worldName);
                pstmt.setInt(2, x);
                pstmt.setInt(3, y);
                pstmt.setInt(4, z);
                pstmt.setInt(5, limit);
                pstmt.setInt(6, offset);
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    results.add(new BlockLogEntry(rs.getLong("timestamp"), rs.getString("player_name"),
                            rs.getInt("action"), rs.getString("block_type"), rs.getString("old_block_type"),
                            worldName, x, y, z));
                }
                future.complete(results);
            } catch (SQLException e) {
                this.plugin.getLogger().log(Level.SEVERE, "Fehler beim Abfragen der Block-Historie!", e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public CompletableFuture<List<ContainerLogEntry>> lookupContainerHistoryAsync(Location location, int page,
            int limit) {
        CompletableFuture<List<ContainerLogEntry>> future = new CompletableFuture<List<ContainerLogEntry>>();
        if (location == null) {
            future.completeExceptionally(new IllegalArgumentException("Location cannot be null"));
            return future;
        }
        String worldName = location.getWorld().getName();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        int offset = (page - 1) * limit;
        Bukkit.getScheduler().runTaskAsynchronously((Plugin) this.plugin, () -> {
            ArrayList<ContainerLogEntry> results = new ArrayList<ContainerLogEntry>();
            String sql = "SELECT timestamp, player_name, action, item_material, item_amount FROM container_log WHERE world = ? AND x = ? AND y = ? AND z = ? ORDER BY timestamp DESC LIMIT ? OFFSET ?";
            try (PreparedStatement pstmt = this.connection.prepareStatement(sql);) {
                pstmt.setString(1, worldName);
                pstmt.setInt(2, x);
                pstmt.setInt(3, y);
                pstmt.setInt(4, z);
                pstmt.setInt(5, limit);
                pstmt.setInt(6, offset);
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    results.add(new ContainerLogEntry(rs.getLong("timestamp"), rs.getString("player_name"),
                            rs.getInt("action"), rs.getString("item_material"), rs.getInt("item_amount"),
                            worldName, x, y, z));
                }
                future.complete(results);
            } catch (SQLException e) {
                this.plugin.getLogger().log(Level.SEVERE, "Fehler beim Abfragen der Container-Historie!", e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public static Location getCanonicalLocation(InventoryHolder holder) {
        if (holder == null) {
            return null;
        }
        if (holder instanceof DoubleChest) {
            DoubleChest doubleChest = (DoubleChest) holder;
            InventoryHolder leftHolder = doubleChest.getLeftSide();
            InventoryHolder rightHolder = doubleChest.getRightSide();
            if (leftHolder instanceof BlockState && rightHolder instanceof BlockState) {
                Location leftLoc = ((BlockState) leftHolder).getLocation();
                Location rightLoc = ((BlockState) rightHolder).getLocation();
                if (leftLoc.getBlockX() < rightLoc.getBlockX()
                        || leftLoc.getBlockX() == rightLoc.getBlockX() && leftLoc.getBlockZ() < rightLoc.getBlockZ()) {
                    return leftLoc;
                }
                return rightLoc;
            }
            Bukkit.getLogger()
                    .warning("[Guardian] Konnte keine BlockState-Locations f\u00fcr DoubleChest-H\u00e4lften finden.");
            return null;
        }
        if (holder instanceof BlockState) {
            InventoryHolder invHolder;
            if (holder instanceof Chest
                    && (invHolder = ((Chest) holder).getInventory().getHolder()) instanceof DoubleChest) {
                return DatabaseManager.getCanonicalLocation(invHolder);
            }
            return ((BlockState) holder).getLocation();
        }
        Location invLoc = holder.getInventory().getLocation();
        if (invLoc != null) {
            return invLoc;
        }
        return null;
    }

    public static class ContainerLogEntry {
        public final long timestamp;
        public final String playerName;
        public final int action;
        public final String itemMaterial;
        public final int itemAmount;
        public final String world;
        public final int x;
        public final int y;
        public final int z;

        public ContainerLogEntry(long timestamp, String playerName, int action, String itemMaterial, int itemAmount,
                String world, int x, int y, int z) {
            this.timestamp = timestamp;
            this.playerName = playerName;
            this.action = action;
            this.itemMaterial = itemMaterial;
            this.itemAmount = itemAmount;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public static class BlockLogEntry {
        public final long timestamp;
        public final String playerName;
        public final int action;
        public final String blockType;
        public final String oldBlockType;
        public final String world;
        public final int x;
        public final int y;
        public final int z;

        public BlockLogEntry(long timestamp, String playerName, int action, String blockType, String oldBlockType,
                String world, int x, int y, int z) {
            this.timestamp = timestamp;
            this.playerName = playerName;
            this.action = action;
            this.blockType = blockType;
            this.oldBlockType = oldBlockType;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    // Analytics query methods
    public Map<Integer, Integer> getPeakHoursData() {
        Map<Integer, Integer> peakHours = new HashMap<>();
        // Initialize all 24 hours
        for (int i = 0; i < 24; i++) {
            peakHours.put(i, 0);
        }

        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT strftime('%H', datetime(timestamp, 'unixepoch')) as hour, COUNT(*) as count " +
                        "FROM block_log WHERE timestamp > ? GROUP BY hour ORDER BY hour")) {
            stmt.setLong(1, System.currentTimeMillis() / 1000L - 7 * 24 * 60 * 60); // Last 7 days
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                int hour = Integer.parseInt(rs.getString("hour"));
                peakHours.put(hour, rs.getInt("count"));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error getting peak hours data: " + e.getMessage());
        }
        return peakHours;
    }

    public List<PlayerActivity> getTopPlayersData(int limit) {
        List<PlayerActivity> players = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT player_name, COUNT(*) as total_actions, " +
                        "SUM(CASE WHEN action = 0 THEN 1 ELSE 0 END) as blocks_broken, " +
                        "SUM(CASE WHEN action = 1 THEN 1 ELSE 0 END) as blocks_placed " +
                        "FROM block_log WHERE timestamp > ? GROUP BY player_name " +
                        "ORDER BY total_actions DESC LIMIT ?")) {
            stmt.setLong(1, System.currentTimeMillis() / 1000L - 7 * 24 * 60 * 60); // Last 7 days
            stmt.setInt(2, limit);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                players.add(new PlayerActivity(
                        rs.getString("player_name"),
                        rs.getInt("total_actions"),
                        rs.getInt("blocks_broken"),
                        rs.getInt("blocks_placed")));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error getting top players: " + e.getMessage());
        }
        return players;
    }

    public Map<String, Integer> getBlockTypesData(String action) {
        Map<String, Integer> blockTypes = new HashMap<>();
        String sql = "SELECT block_type, COUNT(*) as count FROM block_log WHERE timestamp > ?";
        if (action != null) {
            if ("break".equalsIgnoreCase(action)) {
                sql += " AND action = 0";
            } else if ("place".equalsIgnoreCase(action)) {
                sql += " AND action = 1";
            }
        }
        sql += " GROUP BY block_type ORDER BY count DESC LIMIT 20";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, System.currentTimeMillis() / 1000L - 7 * 24 * 60 * 60); // Last 7 days
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                blockTypes.put(rs.getString("block_type"), rs.getInt("count"));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error getting block types: " + e.getMessage());
        }
        return blockTypes;
    }

    public List<Map<String, Object>> getCustomChartData(String xAxis, String yAxis, String period, String filter) {
        List<Map<String, Object>> data = new ArrayList<>();

        // Calculate time range
        long to = System.currentTimeMillis() / 1000L;
        long from;
        switch (period) {
            case "7d":
                from = to - 7 * 24 * 60 * 60;
                break;
            case "30d":
                from = to - 30 * 24 * 60 * 60;
                break;
            default:
                from = to - 24 * 60 * 60; // 24h
        }

        // Build query based on x-axis
        String groupBy, selectX;
        switch (xAxis) {
            case "player":
                selectX = "player_name as label";
                groupBy = "player_name";
                break;
            case "world":
                selectX = "world as label";
                groupBy = "world";
                break;
            case "block":
                selectX = "block_type as label";
                groupBy = "block_type";
                break;
            default: // time
                selectX = "strftime('%Y-%m-%d %H:00', datetime(timestamp, 'unixepoch')) as label";
                groupBy = "strftime('%Y-%m-%d %H', datetime(timestamp, 'unixepoch'))";
        }

        // Build y-axis calculation
        String selectY;
        switch (yAxis) {
            case "blocks_broken":
                selectY = "SUM(CASE WHEN action = 0 THEN 1 ELSE 0 END) as value";
                break;
            case "blocks_placed":
                selectY = "SUM(CASE WHEN action = 1 THEN 1 ELSE 0 END) as value";
                break;
            default: // count
                selectY = "COUNT(*) as value";
        }

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(selectX).append(", ").append(selectY);
        sql.append(" FROM block_log WHERE timestamp BETWEEN ? AND ?");

        // Apply filters
        if (filter != null && !filter.isEmpty()) {
            String[] parts = filter.split(":");
            if (parts.length == 2) {
                switch (parts[0]) {
                    case "player":
                        sql.append(" AND player_name = '").append(parts[1].replace("'", "''")).append("'");
                        break;
                    case "world":
                        sql.append(" AND world = '").append(parts[1].replace("'", "''")).append("'");
                        break;
                    case "action":
                        if ("break".equalsIgnoreCase(parts[1])) {
                            sql.append(" AND action = 0");
                        } else if ("place".equalsIgnoreCase(parts[1])) {
                            sql.append(" AND action = 1");
                        }
                        break;
                }
            }
        }

        sql.append(" GROUP BY ").append(groupBy).append(" ORDER BY value DESC LIMIT 50");

        try (PreparedStatement stmt = connection.prepareStatement(sql.toString())) {
            stmt.setLong(1, from);
            stmt.setLong(2, to);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Map<String, Object> entry = new HashMap<>();
                entry.put("label", rs.getString("label"));
                entry.put("value", rs.getInt("value"));
                data.add(entry);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error getting custom chart data: " + e.getMessage());
        }

        return data;
    }
}
