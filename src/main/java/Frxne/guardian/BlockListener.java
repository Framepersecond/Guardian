package Frxne.guardian;

import Frxne.guardian.DatabaseManager;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class BlockListener implements Listener {

    private final Guardian plugin;
    private final DatabaseManager databaseManager;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final int RESULTS_PER_PAGE = 8;

    public BlockListener(Guardian plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (plugin.isInspecting(event.getPlayer()) && event.getPlayer().getGameMode() == GameMode.CREATIVE) {
            // Optional: event.setCancelled(true);
        }
        Block block = event.getBlock();
        Material brokenMaterial = block.getType();
        databaseManager.logBlockActionAsync(event.getPlayer(), block, DatabaseManager.ACTION_BREAK, brokenMaterial);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        databaseManager.logBlockActionAsync(event.getPlayer(), block, DatabaseManager.ACTION_PLACE, block.getType());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!plugin.isInspecting(player)) {
            return;
        }

        Action action = event.getAction();
        Block clickedBlock = event.getClickedBlock();

        if (clickedBlock == null) {
            return;
        }

        event.setCancelled(true);

        Location clickedLocation = clickedBlock.getLocation();
        Location queryLocation = clickedLocation;

        BlockState blockState = clickedBlock.getState();
        InventoryHolder holder = (blockState instanceof InventoryHolder) ? (InventoryHolder) blockState : null;

        int page = 1;

        if (action == Action.LEFT_CLICK_BLOCK) {
            player.sendMessage(ChatColor.GRAY + "Suche Blockverlauf für " + clickedBlock.getType().name().toLowerCase().replace('_',' ') + " bei "
                    + clickedLocation.getBlockX() + "," + clickedLocation.getBlockY() + "," + clickedLocation.getBlockZ() + "...");
            CompletableFuture<List<DatabaseManager.BlockLogEntry>> future = databaseManager.lookupBlockHistoryAsync(clickedLocation, page, RESULTS_PER_PAGE);
            displayBlockHistory(player, future, page, "lookup");

        } else if (action == Action.RIGHT_CLICK_BLOCK) {
            if (holder != null) {
                queryLocation = DatabaseManager.getCanonicalLocation(holder);
                if (queryLocation == null) {
                    player.sendMessage(ChatColor.RED + "Konnte die Container-Position nicht eindeutig bestimmen.");
                    return;
                }

                player.sendMessage(ChatColor.GRAY + "Suche Itemverlauf für Container bei "
                        + queryLocation.getBlockX() + "," + queryLocation.getBlockY() + "," + queryLocation.getBlockZ() + "...");
                CompletableFuture<List<DatabaseManager.ContainerLogEntry>> future = databaseManager.lookupContainerHistoryAsync(queryLocation, page, RESULTS_PER_PAGE);
                displayContainerHistory(player, future, page, "guardian");

            } else {
                player.sendMessage(ChatColor.GRAY + "Suche Blockverlauf für " + clickedBlock.getType().name().toLowerCase().replace('_',' ') + " bei "
                        + clickedLocation.getBlockX() + "," + clickedLocation.getBlockY() + "," + clickedLocation.getBlockZ() + "...");
                CompletableFuture<List<DatabaseManager.BlockLogEntry>> future = databaseManager.lookupBlockHistoryAsync(clickedLocation, page, RESULTS_PER_PAGE);
                displayBlockHistory(player, future, page, "lookup");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        Inventory topInventory = event.getView().getTopInventory();
        Inventory clickedInventory = event.getClickedInventory();

        InventoryHolder holder = topInventory.getHolder();
        if (holder == null) return;

        Location logLocation = DatabaseManager.getCanonicalLocation(holder);
        if (logLocation == null) {
            return;
        }

        ItemStack cursorItem = event.getCursor();
        ItemStack currentItem = event.getCurrentItem();

        if (clickedInventory == topInventory && cursorItem != null && cursorItem.getType() != Material.AIR && (currentItem == null || currentItem.getType() == Material.AIR)) {
            databaseManager.logContainerActionAsync(player, logLocation, DatabaseManager.CONTAINER_ACTION_ADD, cursorItem);
        }
        else if (event.isShiftClick() && clickedInventory != topInventory && clickedInventory != null && currentItem != null && currentItem.getType() != Material.AIR) {
            databaseManager.logContainerActionAsync(player, logLocation, DatabaseManager.CONTAINER_ACTION_ADD, currentItem);
        }
        else if (clickedInventory == topInventory && cursorItem != null && cursorItem.getType() != Material.AIR && currentItem != null && currentItem.getType() != Material.AIR && cursorItem.isSimilar(currentItem)) {
            ItemStack addedItem = cursorItem.clone();
            databaseManager.logContainerActionAsync(player, logLocation, DatabaseManager.CONTAINER_ACTION_ADD, addedItem);
        }
        else if (clickedInventory == topInventory && currentItem != null && currentItem.getType() != Material.AIR && (cursorItem == null || cursorItem.getType() == Material.AIR)) {
            databaseManager.logContainerActionAsync(player, logLocation, DatabaseManager.CONTAINER_ACTION_REMOVE, currentItem);
        }
        else if (event.isShiftClick() && clickedInventory == topInventory && currentItem != null && currentItem.getType() != Material.AIR) {
            databaseManager.logContainerActionAsync(player, logLocation, DatabaseManager.CONTAINER_ACTION_REMOVE, currentItem);
        }
        else if (clickedInventory == topInventory && cursorItem != null && cursorItem.getType() != Material.AIR && currentItem != null && currentItem.getType() != Material.AIR && !cursorItem.isSimilar(currentItem)) {
            databaseManager.logContainerActionAsync(player, logLocation, DatabaseManager.CONTAINER_ACTION_REMOVE, currentItem);
            databaseManager.logContainerActionAsync(player, logLocation, DatabaseManager.CONTAINER_ACTION_ADD, cursorItem);
        }
        else if (event.getAction().name().contains("HOTBAR") && clickedInventory == topInventory && currentItem != null && currentItem.getType() != Material.AIR) {
            ItemStack hotbarItem = player.getInventory().getItem(event.getHotbarButton());
            databaseManager.logContainerActionAsync(player, logLocation, DatabaseManager.CONTAINER_ACTION_REMOVE, currentItem);
            if (hotbarItem != null && hotbarItem.getType() != Material.AIR) {
                databaseManager.logContainerActionAsync(player, logLocation, DatabaseManager.CONTAINER_ACTION_ADD, hotbarItem);
            }
        }
    }


    private void displayBlockHistory(Player player, CompletableFuture<List<DatabaseManager.BlockLogEntry>> future, int page, String commandLabel) {
        future.whenComplete((results, throwable) -> {
            if (throwable != null) {
                player.sendMessage(ChatColor.RED + "Fehler beim Abrufen des Block-Verlaufs: " + throwable.getMessage());
                return;
            }
            if (results == null || results.isEmpty()) {
                if(page == 1) player.sendMessage(ChatColor.YELLOW + "Kein Block-Verlauf gefunden.");
                else player.sendMessage(ChatColor.YELLOW + "Keine weiteren Einträge auf Seite " + page + ".");
                return;
            }
            player.sendMessage(ChatColor.GOLD + "--- Block Verlauf (Seite " + page + ") ---");
            for (DatabaseManager.BlockLogEntry entry : results) {
                String actionString = (entry.action == DatabaseManager.ACTION_BREAK) ? ChatColor.RED + "zerstört" : ChatColor.GREEN + "platziert";
                String materialString = (entry.action == DatabaseManager.ACTION_BREAK) ? (entry.oldBlockType != null ? entry.oldBlockType : entry.blockType) : entry.blockType;
                String formattedDate = dateFormat.format(new Date(entry.timestamp * 1000L));
                player.sendMessage(ChatColor.GRAY + "[" + formattedDate + "] " + ChatColor.WHITE + entry.playerName + " " + actionString + " " + ChatColor.AQUA + materialString.toLowerCase().replace('_', ' '));
            }
            if (results.size() == RESULTS_PER_PAGE) {
                player.sendMessage(ChatColor.GRAY + "Für nächste Seite: /" + commandLabel + " " + (page + 1));
            }
        });
    }

    private void displayContainerHistory(Player player, CompletableFuture<List<DatabaseManager.ContainerLogEntry>> future, int page, String commandLabel) {
        future.whenComplete((results, throwable) -> {
            if (throwable != null) {
                player.sendMessage(ChatColor.RED + "Fehler beim Abrufen des Container-Verlaufs: " + throwable.getMessage());
                return;
            }
            if (results == null || results.isEmpty()) {
                if(page == 1) player.sendMessage(ChatColor.YELLOW + "Kein Container-Verlauf gefunden.");
                else player.sendMessage(ChatColor.YELLOW + "Keine weiteren Einträge auf Seite " + page + ".");
                return;
            }
            player.sendMessage(ChatColor.GOLD + "--- Container Verlauf (Seite " + page + ") ---");
            for (DatabaseManager.ContainerLogEntry entry : results) {
                String actionString = (entry.action == DatabaseManager.CONTAINER_ACTION_REMOVE) ? ChatColor.RED + "-" : ChatColor.GREEN + "+";
                String formattedDate = dateFormat.format(new Date(entry.timestamp * 1000L));
                String playerName = entry.playerName != null ? entry.playerName : "SYSTEM";
                player.sendMessage(ChatColor.GRAY + "[" + formattedDate + "] " + ChatColor.WHITE + playerName + " " + actionString + entry.itemAmount + " " + ChatColor.AQUA + entry.itemMaterial.toLowerCase().replace('_', ' '));
            }
            if (results.size() == RESULTS_PER_PAGE) {
                player.sendMessage(ChatColor.GRAY + "Für nächste Seite: /" + commandLabel + " " + (page + 1));
            }
        });
    }
}