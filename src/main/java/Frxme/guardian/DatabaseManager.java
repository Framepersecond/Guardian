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

import Frxme.guardian.Guardian;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
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
        return blockLogSuccess && containerLogSuccess;
    }

    private boolean initializeBlockLogTable() {
        boolean bl;
        block8: {
            String sql = "CREATE TABLE IF NOT EXISTS block_log (id INTEGER PRIMARY KEY AUTOINCREMENT,timestamp INTEGER NOT NULL,player_uuid VARCHAR(36) NOT NULL,player_name VARCHAR(16) NOT NULL,action INTEGER NOT NULL,world VARCHAR(255) NOT NULL,x INTEGER NOT NULL,y INTEGER NOT NULL,z INTEGER NOT NULL,block_type VARCHAR(255) NOT NULL,old_block_type VARCHAR(255));";
            Statement stmt = this.connection.createStatement();
            try {
                stmt.execute(sql);
                this.plugin.getLogger().info("Datenbank Tabelle 'block_log' erfolgreich initialisiert/gepr\u00fcft.");
                bl = true;
                if (stmt == null) break block8;
            } catch (Throwable throwable) {
                try {
                    if (stmt != null) {
                        try {
                            stmt.close();
                        } catch (Throwable throwable2) {
                            throwable.addSuppressed(throwable2);
                        }
                    }
                    throw throwable;
                } catch (SQLException e) {
                    this.plugin.getLogger().log(Level.SEVERE, "Konnte Datenbank Tabelle 'block_log' nicht initialisieren!", e);
                    return false;
                }
            }
            stmt.close();
        }
        return bl;
    }

    private boolean initializeContainerLogTable() {
        boolean bl;
        block8: {
            String sql = "CREATE TABLE IF NOT EXISTS container_log (id INTEGER PRIMARY KEY AUTOINCREMENT,timestamp INTEGER NOT NULL,player_uuid VARCHAR(36),player_name VARCHAR(16),action INTEGER NOT NULL,world VARCHAR(255) NOT NULL,x INTEGER NOT NULL,y INTEGER NOT NULL,z INTEGER NOT NULL,item_material VARCHAR(255) NOT NULL,item_amount INTEGER NOT NULL);";
            Statement stmt = this.connection.createStatement();
            try {
                stmt.execute(sql);
                this.plugin.getLogger().info("Datenbank Tabelle 'container_log' erfolgreich initialisiert/gepr\u00fcft.");
                bl = true;
                if (stmt == null) break block8;
            } catch (Throwable throwable) {
                try {
                    if (stmt != null) {
                        try {
                            stmt.close();
                        } catch (Throwable throwable2) {
                            throwable.addSuppressed(throwable2);
                        }
                    }
                    throw throwable;
                } catch (SQLException e) {
                    this.plugin.getLogger().log(Level.SEVERE, "Konnte Datenbank Tabelle 'container_log' nicht initialisieren!", e);
                    return false;
                }
            }
            stmt.close();
        }
        return bl;
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
        Bukkit.getScheduler().runTaskAsynchronously((Plugin)this.plugin, () -> {
            String sql = "INSERT INTO block_log(timestamp, player_uuid, player_name, action, world, x, y, z, block_type, old_block_type) VALUES(?,?,?,?,?,?,?,?,?,?)";
            try (PreparedStatement pstmt = this.connection.prepareStatement(sql);){
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
                this.plugin.getLogger().log(Level.SEVERE, "Fehler beim Einf\u00fcgen eines Block-Logs in die Datenbank!", e);
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
        Bukkit.getScheduler().runTaskAsynchronously((Plugin)this.plugin, () -> {
            String sql = "INSERT INTO container_log(timestamp, player_uuid, player_name, action, world, x, y, z, item_material, item_amount) VALUES(?,?,?,?,?,?,?,?,?,?)";
            try (PreparedStatement pstmt = this.connection.prepareStatement(sql);){
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
                this.plugin.getLogger().log(Level.SEVERE, "Fehler beim Einf\u00fcgen eines Container-Logs in die Datenbank!", e);
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
        Bukkit.getScheduler().runTaskAsynchronously((Plugin)this.plugin, () -> {
            ArrayList<BlockLogEntry> results = new ArrayList<BlockLogEntry>();
            String sql = "SELECT timestamp, player_name, action, block_type, old_block_type FROM block_log WHERE world = ? AND x = ? AND y = ? AND z = ? ORDER BY timestamp DESC LIMIT ? OFFSET ?";
            try (PreparedStatement pstmt = this.connection.prepareStatement(sql);){
                pstmt.setString(1, worldName);
                pstmt.setInt(2, x);
                pstmt.setInt(3, y);
                pstmt.setInt(4, z);
                pstmt.setInt(5, limit);
                pstmt.setInt(6, offset);
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    results.add(new BlockLogEntry(rs.getLong("timestamp"), rs.getString("player_name"), rs.getInt("action"), rs.getString("block_type"), rs.getString("old_block_type")));
                }
                future.complete(results);
            } catch (SQLException e) {
                this.plugin.getLogger().log(Level.SEVERE, "Fehler beim Abfragen der Block-Historie!", e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public CompletableFuture<List<ContainerLogEntry>> lookupContainerHistoryAsync(Location location, int page, int limit) {
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
        Bukkit.getScheduler().runTaskAsynchronously((Plugin)this.plugin, () -> {
            ArrayList<ContainerLogEntry> results = new ArrayList<ContainerLogEntry>();
            String sql = "SELECT timestamp, player_name, action, item_material, item_amount FROM container_log WHERE world = ? AND x = ? AND y = ? AND z = ? ORDER BY timestamp DESC LIMIT ? OFFSET ?";
            try (PreparedStatement pstmt = this.connection.prepareStatement(sql);){
                pstmt.setString(1, worldName);
                pstmt.setInt(2, x);
                pstmt.setInt(3, y);
                pstmt.setInt(4, z);
                pstmt.setInt(5, limit);
                pstmt.setInt(6, offset);
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    results.add(new ContainerLogEntry(rs.getLong("timestamp"), rs.getString("player_name"), rs.getInt("action"), rs.getString("item_material"), rs.getInt("item_amount")));
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
            DoubleChest doubleChest = (DoubleChest)holder;
            InventoryHolder leftHolder = doubleChest.getLeftSide();
            InventoryHolder rightHolder = doubleChest.getRightSide();
            if (leftHolder instanceof BlockState && rightHolder instanceof BlockState) {
                Location leftLoc = ((BlockState)leftHolder).getLocation();
                Location rightLoc = ((BlockState)rightHolder).getLocation();
                if (leftLoc.getBlockX() < rightLoc.getBlockX() || leftLoc.getBlockX() == rightLoc.getBlockX() && leftLoc.getBlockZ() < rightLoc.getBlockZ()) {
                    return leftLoc;
                }
                return rightLoc;
            }
            Bukkit.getLogger().warning("[Guardian] Konnte keine BlockState-Locations f\u00fcr DoubleChest-H\u00e4lften finden.");
            return null;
        }
        if (holder instanceof BlockState) {
            InventoryHolder invHolder;
            if (holder instanceof Chest && (invHolder = ((Chest)holder).getInventory().getHolder()) instanceof DoubleChest) {
                return DatabaseManager.getCanonicalLocation(invHolder);
            }
            return ((BlockState)holder).getLocation();
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

        public ContainerLogEntry(long timestamp, String playerName, int action, String itemMaterial, int itemAmount) {
            this.timestamp = timestamp;
            this.playerName = playerName;
            this.action = action;
            this.itemMaterial = itemMaterial;
            this.itemAmount = itemAmount;
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
}

