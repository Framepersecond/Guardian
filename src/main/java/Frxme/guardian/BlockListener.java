/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 * 
 * Could not load the following classes:
 *  org.bukkit.ChatColor
 *  org.bukkit.GameMode
 *  org.bukkit.Location
 *  org.bukkit.Material
 *  org.bukkit.block.Block
 *  org.bukkit.block.BlockState
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.block.Action
 *  org.bukkit.event.block.BlockBreakEvent
 *  org.bukkit.event.block.BlockPlaceEvent
 *  org.bukkit.event.inventory.InventoryClickEvent
 *  org.bukkit.event.player.PlayerInteractEvent
 *  org.bukkit.inventory.Inventory
 *  org.bukkit.inventory.InventoryHolder
 *  org.bukkit.inventory.ItemStack
 */
package Frxme.guardian;

import Frxme.guardian.DatabaseManager;
import Frxme.guardian.Guardian;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class BlockListener
implements Listener {
    private final Guardian plugin;
    private final DatabaseManager databaseManager;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final int RESULTS_PER_PAGE = 8;

    public BlockListener(Guardian plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!this.plugin.isInspecting(event.getPlayer()) || event.getPlayer().getGameMode() == GameMode.CREATIVE) {
            // empty if block
        }
        Block block = event.getBlock();
        Material brokenMaterial = block.getType();
        this.databaseManager.logBlockActionAsync(event.getPlayer(), block, 0, brokenMaterial);
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        this.databaseManager.logBlockActionAsync(event.getPlayer(), block, 1, block.getType());
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Location clickedLocation;
        Player player = event.getPlayer();
        if (!this.plugin.isInspecting(player)) {
            return;
        }
        Action action = event.getAction();
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }
        event.setCancelled(true);
        Location queryLocation = clickedLocation = clickedBlock.getLocation();
        BlockState blockState = clickedBlock.getState();
        InventoryHolder holder = blockState instanceof InventoryHolder ? (InventoryHolder)blockState : null;
        int page = 1;
        if (action == Action.LEFT_CLICK_BLOCK) {
            player.sendMessage(String.valueOf(ChatColor.GRAY) + "Suche Blockverlauf f\u00fcr " + clickedBlock.getType().name().toLowerCase().replace('_', ' ') + " bei " + clickedLocation.getBlockX() + "," + clickedLocation.getBlockY() + "," + clickedLocation.getBlockZ() + "...");
            CompletableFuture<List<DatabaseManager.BlockLogEntry>> future = this.databaseManager.lookupBlockHistoryAsync(clickedLocation, page, 8);
            this.displayBlockHistory(player, future, page, "lookup");
        } else if (action == Action.RIGHT_CLICK_BLOCK) {
            if (holder != null) {
                queryLocation = DatabaseManager.getCanonicalLocation(holder);
                if (queryLocation == null) {
                    player.sendMessage(String.valueOf(ChatColor.RED) + "Konnte die Container-Position nicht eindeutig bestimmen.");
                    return;
                }
                player.sendMessage(String.valueOf(ChatColor.GRAY) + "Suche Itemverlauf f\u00fcr Container bei " + queryLocation.getBlockX() + "," + queryLocation.getBlockY() + "," + queryLocation.getBlockZ() + "...");
                CompletableFuture<List<DatabaseManager.ContainerLogEntry>> future = this.databaseManager.lookupContainerHistoryAsync(queryLocation, page, 8);
                this.displayContainerHistory(player, future, page, "guardian");
            } else {
                player.sendMessage(String.valueOf(ChatColor.GRAY) + "Suche Blockverlauf f\u00fcr " + clickedBlock.getType().name().toLowerCase().replace('_', ' ') + " bei " + clickedLocation.getBlockX() + "," + clickedLocation.getBlockY() + "," + clickedLocation.getBlockZ() + "...");
                CompletableFuture<List<DatabaseManager.BlockLogEntry>> future = this.databaseManager.lookupBlockHistoryAsync(clickedLocation, page, 8);
                this.displayBlockHistory(player, future, page, "lookup");
            }
        }
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player)event.getWhoClicked();
        Inventory topInventory = event.getView().getTopInventory();
        Inventory clickedInventory = event.getClickedInventory();
        InventoryHolder holder = topInventory.getHolder();
        if (holder == null) {
            return;
        }
        Location logLocation = DatabaseManager.getCanonicalLocation(holder);
        if (logLocation == null) {
            return;
        }
        ItemStack cursorItem = event.getCursor();
        ItemStack currentItem = event.getCurrentItem();
        if (clickedInventory == topInventory && cursorItem != null && cursorItem.getType() != Material.AIR && (currentItem == null || currentItem.getType() == Material.AIR)) {
            this.databaseManager.logContainerActionAsync(player, logLocation, 1, cursorItem);
        } else if (event.isShiftClick() && clickedInventory != topInventory && clickedInventory != null && currentItem != null && currentItem.getType() != Material.AIR) {
            this.databaseManager.logContainerActionAsync(player, logLocation, 1, currentItem);
        } else if (clickedInventory == topInventory && cursorItem != null && cursorItem.getType() != Material.AIR && currentItem != null && currentItem.getType() != Material.AIR && cursorItem.isSimilar(currentItem)) {
            ItemStack addedItem = cursorItem.clone();
            this.databaseManager.logContainerActionAsync(player, logLocation, 1, addedItem);
        } else if (clickedInventory == topInventory && currentItem != null && currentItem.getType() != Material.AIR && (cursorItem == null || cursorItem.getType() == Material.AIR)) {
            this.databaseManager.logContainerActionAsync(player, logLocation, 0, currentItem);
        } else if (event.isShiftClick() && clickedInventory == topInventory && currentItem != null && currentItem.getType() != Material.AIR) {
            this.databaseManager.logContainerActionAsync(player, logLocation, 0, currentItem);
        } else if (clickedInventory == topInventory && cursorItem != null && cursorItem.getType() != Material.AIR && currentItem != null && currentItem.getType() != Material.AIR && !cursorItem.isSimilar(currentItem)) {
            this.databaseManager.logContainerActionAsync(player, logLocation, 0, currentItem);
            this.databaseManager.logContainerActionAsync(player, logLocation, 1, cursorItem);
        } else if (event.getAction().name().contains("HOTBAR") && clickedInventory == topInventory && currentItem != null && currentItem.getType() != Material.AIR) {
            ItemStack hotbarItem = player.getInventory().getItem(event.getHotbarButton());
            this.databaseManager.logContainerActionAsync(player, logLocation, 0, currentItem);
            if (hotbarItem != null && hotbarItem.getType() != Material.AIR) {
                this.databaseManager.logContainerActionAsync(player, logLocation, 1, hotbarItem);
            }
        }
    }

    private void displayBlockHistory(Player player, CompletableFuture<List<DatabaseManager.BlockLogEntry>> future, int page, String commandLabel) {
        future.whenComplete((results, throwable) -> {
            if (throwable != null) {
                player.sendMessage(String.valueOf(ChatColor.RED) + "Fehler beim Abrufen des Block-Verlaufs: " + throwable.getMessage());
                return;
            }
            if (results == null) {
                results = Collections.emptyList();
            }
            if (this.plugin.isHideOwnActions(player)) {
                String self = player.getName();
                ArrayList<DatabaseManager.BlockLogEntry> filtered = new ArrayList<DatabaseManager.BlockLogEntry>();
                for (DatabaseManager.BlockLogEntry e : results) {
                    if (e.playerName != null && e.playerName.equalsIgnoreCase(self)) continue;
                    filtered.add(e);
                }
                results = filtered;
            }
            if (results.isEmpty()) {
                if (page == 1) {
                    player.sendMessage(String.valueOf(ChatColor.YELLOW) + "Kein Block-Verlauf gefunden.");
                } else {
                    player.sendMessage(String.valueOf(ChatColor.YELLOW) + "Keine weiteren Eintr\u00e4ge auf Seite " + page + ".");
                }
                return;
            }
            player.sendMessage(String.valueOf(ChatColor.GOLD) + "--- Block Verlauf (Seite " + page + ") ---");
            for (DatabaseManager.BlockLogEntry entry : results) {
                String actionString;
                String string = actionString = entry.action == 0 ? String.valueOf(ChatColor.RED) + "zerst\u00f6rt" : String.valueOf(ChatColor.GREEN) + "platziert";
                String materialString = entry.action == 0 ? (entry.oldBlockType != null ? entry.oldBlockType : entry.blockType) : entry.blockType;
                String formattedDate = this.dateFormat.format(new Date(entry.timestamp * 1000L));
                player.sendMessage(String.valueOf(ChatColor.GRAY) + "[" + formattedDate + "] " + String.valueOf(ChatColor.WHITE) + entry.playerName + " " + actionString + " " + String.valueOf(ChatColor.AQUA) + materialString.toLowerCase().replace('_', ' '));
            }
            if (results.size() == 8) {
                player.sendMessage(String.valueOf(ChatColor.GRAY) + "F\u00fcr n\u00e4chste Seite: /" + commandLabel + " " + (page + 1));
            }
        });
    }

    private void displayContainerHistory(Player player, CompletableFuture<List<DatabaseManager.ContainerLogEntry>> future, int page, String commandLabel) {
        future.whenComplete((results, throwable) -> {
            if (throwable != null) {
                player.sendMessage(String.valueOf(ChatColor.RED) + "Fehler beim Abrufen des Container-Verlaufs: " + throwable.getMessage());
                return;
            }
            if (results == null) {
                results = Collections.emptyList();
            }
            if (this.plugin.isHideOwnActions(player)) {
                String self = player.getName();
                ArrayList<DatabaseManager.ContainerLogEntry> filtered = new ArrayList<DatabaseManager.ContainerLogEntry>();
                for (DatabaseManager.ContainerLogEntry e : results) {
                    String name = e.playerName != null ? e.playerName : "";
                    if (name.equalsIgnoreCase(self)) continue;
                    filtered.add(e);
                }
                results = filtered;
            }
            if (results.isEmpty()) {
                if (page == 1) {
                    player.sendMessage(String.valueOf(ChatColor.YELLOW) + "Kein Container-Verlauf gefunden.");
                } else {
                    player.sendMessage(String.valueOf(ChatColor.YELLOW) + "Keine weiteren Eintr\u00e4ge auf Seite " + page + ".");
                }
                return;
            }
            player.sendMessage(String.valueOf(ChatColor.GOLD) + "--- Container Verlauf (Seite " + page + ") ---");
            for (DatabaseManager.ContainerLogEntry entry : results) {
                String actionString = entry.action == 0 ? String.valueOf(ChatColor.RED) + "-" : String.valueOf(ChatColor.GREEN) + "+";
                String formattedDate = this.dateFormat.format(new Date(entry.timestamp * 1000L));
                String playerName = entry.playerName != null ? entry.playerName : "SYSTEM";
                player.sendMessage(String.valueOf(ChatColor.GRAY) + "[" + formattedDate + "] " + String.valueOf(ChatColor.WHITE) + playerName + " " + actionString + entry.itemAmount + " " + String.valueOf(ChatColor.AQUA) + entry.itemMaterial.toLowerCase().replace('_', ' '));
            }
            if (results.size() == 8) {
                player.sendMessage(String.valueOf(ChatColor.GRAY) + "F\u00fcr n\u00e4chste Seite: /" + commandLabel + " " + (page + 1));
            }
        });
    }
}

