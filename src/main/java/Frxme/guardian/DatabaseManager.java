package Frxme.guardian;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

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
            if (connection != null && !connection.isClosed()) {
                return true;
            }

            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }

            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            plugin.getLogger().info("SQLite Datenbankverbindung hergestellt.");
            return true;
        } catch (SQLException | ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "Konnte keine Verbindung zur SQLite Datenbank herstellen!", e);
            return false;
        }
    }

    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("SQLite Datenbankverbindung geschlossen.");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Schließen der SQLite Datenbankverbindung!", e);
        }
    }

    public boolean initializeDatabase() {
        boolean blockLogSuccess = initializeBlockLogTable();
        boolean containerLogSuccess = initializeContainerLogTable();
        return blockLogSuccess && containerLogSuccess;
    }

    private boolean initializeBlockLogTable() {
        String sql = "CREATE TABLE IF NOT EXISTS block_log ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "timestamp INTEGER NOT NULL,"
                + "player_uuid VARCHAR(36) NOT NULL,"
                + "player_name VARCHAR(16) NOT NULL,"
                + "action INTEGER NOT NULL,"
                + "world VARCHAR(255) NOT NULL,"
                + "x INTEGER NOT NULL,"
                + "y INTEGER NOT NULL,"
                + "z INTEGER NOT NULL,"
                + "block_type VARCHAR(255) NOT NULL,"
                + "old_block_type VARCHAR(255)"
                + ");";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            plugin.getLogger().info("Datenbank Tabelle 'block_log' erfolgreich initialisiert/geprüft.");
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Konnte Datenbank Tabelle 'block_log' nicht initialisieren!", e);
            return false;
        }
    }

    private boolean initializeContainerLogTable() {
        String sql = "CREATE TABLE IF NOT EXISTS container_log ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "timestamp INTEGER NOT NULL,"
                + "player_uuid VARCHAR(36),"
                + "player_name VARCHAR(16),"
                + "action INTEGER NOT NULL,"
                + "world VARCHAR(255) NOT NULL,"
                + "x INTEGER NOT NULL,"
                + "y INTEGER NOT NULL,"
                + "z INTEGER NOT NULL,"
                + "item_material VARCHAR(255) NOT NULL,"
                + "item_amount INTEGER NOT NULL"
                + ");";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            plugin.getLogger().info("Datenbank Tabelle 'container_log' erfolgreich initialisiert/geprüft.");
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Konnte Datenbank Tabelle 'container_log' nicht initialisieren!", e);
            return false;
        }
    }

    public void logBlockActionAsync(Player player, Block block, int action, Material oldMaterial) {
        final UUID playerUUID = player.getUniqueId();
        final String playerName = player.getName();
        final long timestamp = System.currentTimeMillis() / 1000;
        final String worldName = block.getWorld().getName();
        final int x = block.getX();
        final int y = block.getY();
        final int z = block.getZ();
        final String blockType = (action == ACTION_PLACE ? block.getType() : oldMaterial).name();
        final String oldBlockType = (action == ACTION_BREAK ? oldMaterial : block.getType()).name();


        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "INSERT INTO block_log(timestamp, player_uuid, player_name, action, world, x, y, z, block_type, old_block_type) VALUES(?,?,?,?,?,?,?,?,?,?)";

            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setLong(1, timestamp);
                pstmt.setString(2, playerUUID.toString());
                pstmt.setString(3, playerName);
                pstmt.setInt(4, action);
                pstmt.setString(5, worldName);
                pstmt.setInt(6, x);
                pstmt.setInt(7, y);
                pstmt.setInt(8, z);
                pstmt.setString(9, blockType);
                pstmt.setString(10, (action == ACTION_BREAK) ? oldBlockType : null);

                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Fehler beim Einfügen eines Block-Logs in die Datenbank!", e);
            }
        });
    }

    public void logContainerActionAsync(Player player, Location containerLoc, int action, ItemStack item) {
        if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
            return;
        }
        if (containerLoc == null) {
            plugin.getLogger().warning("Versuch, Container-Aktion ohne gültige Location zu loggen.");
            return;
        }

        final UUID playerUUID = (player != null) ? player.getUniqueId() : null;
        final String playerName = (player != null) ? player.getName() : "SYSTEM";
        final long timestamp = System.currentTimeMillis() / 1000;
        final String worldName = containerLoc.getWorld().getName();
        final int x = containerLoc.getBlockX();
        final int y = containerLoc.getBlockY();
        final int z = containerLoc.getBlockZ();
        final String itemMaterial = item.getType().name();
        final int itemAmount = item.getAmount();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "INSERT INTO container_log(timestamp, player_uuid, player_name, action, world, x, y, z, item_material, item_amount) VALUES(?,?,?,?,?,?,?,?,?,?)";

            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setLong(1, timestamp);
                if (playerUUID != null) {
                    pstmt.setString(2, playerUUID.toString());
                    pstmt.setString(3, playerName);
                } else {
                    pstmt.setNull(2, Types.VARCHAR);
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
                plugin.getLogger().log(Level.SEVERE, "Fehler beim Einfügen eines Container-Logs in die Datenbank!", e);
            }
        });
    }


    public CompletableFuture<List<BlockLogEntry>> lookupBlockHistoryAsync(Location location, int page, int limit) {
        CompletableFuture<List<BlockLogEntry>> future = new CompletableFuture<>();
        if (location == null) {
            future.completeExceptionally(new IllegalArgumentException("Location cannot be null"));
            return future;
        }

        final String worldName = location.getWorld().getName();
        final int x = location.getBlockX();
        final int y = location.getBlockY();
        final int z = location.getBlockZ();
        final int offset = (page - 1) * limit;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<BlockLogEntry> results = new ArrayList<>();
            String sql = "SELECT timestamp, player_name, action, block_type, old_block_type FROM block_log "
                    + "WHERE world = ? AND x = ? AND y = ? AND z = ? "
                    + "ORDER BY timestamp DESC LIMIT ? OFFSET ?";

            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, worldName);
                pstmt.setInt(2, x);
                pstmt.setInt(3, y);
                pstmt.setInt(4, z);
                pstmt.setInt(5, limit);
                pstmt.setInt(6, offset);

                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    results.add(new BlockLogEntry(
                            rs.getLong("timestamp"),
                            rs.getString("player_name"),
                            rs.getInt("action"),
                            rs.getString("block_type"),
                            rs.getString("old_block_type")
                    ));
                }
                future.complete(results);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Fehler beim Abfragen der Block-Historie!", e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public CompletableFuture<List<ContainerLogEntry>> lookupContainerHistoryAsync(Location location, int page, int limit) {
        CompletableFuture<List<ContainerLogEntry>> future = new CompletableFuture<>();
        if (location == null) {
            future.completeExceptionally(new IllegalArgumentException("Location cannot be null"));
            return future;
        }

        final String worldName = location.getWorld().getName();
        final int x = location.getBlockX();
        final int y = location.getBlockY();
        final int z = location.getBlockZ();
        final int offset = (page - 1) * limit;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<ContainerLogEntry> results = new ArrayList<>();
            String sql = "SELECT timestamp, player_name, action, item_material, item_amount FROM container_log "
                    + "WHERE world = ? AND x = ? AND y = ? AND z = ? "
                    + "ORDER BY timestamp DESC LIMIT ? OFFSET ?";

            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, worldName);
                pstmt.setInt(2, x);
                pstmt.setInt(3, y);
                pstmt.setInt(4, z);
                pstmt.setInt(5, limit);
                pstmt.setInt(6, offset);

                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    results.add(new ContainerLogEntry(
                            rs.getLong("timestamp"),
                            rs.getString("player_name"),
                            rs.getInt("action"),
                            rs.getString("item_material"),
                            rs.getInt("item_amount")
                    ));
                }
                future.complete(results);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Fehler beim Abfragen der Container-Historie!", e);
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

                if (leftLoc.getBlockX() < rightLoc.getBlockX() || (leftLoc.getBlockX() == rightLoc.getBlockX() && leftLoc.getBlockZ() < rightLoc.getBlockZ())) {
                    return leftLoc;
                } else {
                    return rightLoc;
                }
            }
            Bukkit.getLogger().warning("[Guardian] Konnte keine BlockState-Locations für DoubleChest-Hälften finden.");
            return null;

        } else if (holder instanceof BlockState) {
            return ((BlockState) holder).getLocation();
        } else {
            Location invLoc = holder.getInventory().getLocation();
            if (invLoc != null) {
                return invLoc;
            }
            //Bukkit.getLogger().warning("[Guardian] Unbekannter InventoryHolder-Typ ohne BlockState: " + holder.getClass().getName());
            return null;
        }
    }


    public static class BlockLogEntry {
        public final long timestamp;
        public final String playerName;
        public final int action;
        public final String blockType;
        public final String oldBlockType;

        public BlockLogEntry(long timestamp, String playerName, int action, String blockType, String oldBlockType) {
            this.timestamp = timestamp;
            this.playerName = playerName;
            this.action = action;
            this.blockType = blockType;
            this.oldBlockType = oldBlockType;
        }
    }

    public static class ContainerLogEntry {
        public final long timestamp;
        public final String playerName;
        public final int action;
        public final String itemMaterial;
        public final int itemAmount;

        public ContainerLogEntry(long timestamp, String playerName, int action, String itemMaterial, int itemAmount) {
            this.timestamp = timestamp;
            this.playerName = playerName;
            this.action = action;
            this.itemMaterial = itemMaterial;
            this.itemAmount = itemAmount;
        }
    }
}