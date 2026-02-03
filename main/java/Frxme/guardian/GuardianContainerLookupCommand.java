/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 * 
 * Could not load the following classes:
 *  org.bukkit.ChatColor
 *  org.bukkit.Location
 *  org.bukkit.block.Block
 *  org.bukkit.block.BlockState
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 *  org.bukkit.inventory.InventoryHolder
 *  org.jetbrains.annotations.NotNull
 */
package Frxme.guardian;

import Frxme.guardian.DatabaseManager;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public class GuardianContainerLookupCommand
implements CommandExecutor {
    private final DatabaseManager databaseManager;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final int RESULTS_PER_PAGE = 8;

    public GuardianContainerLookupCommand(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        Location queryLocation;
        if (!(sender instanceof Player)) {
            sender.sendMessage(String.valueOf(ChatColor.RED) + "Dieser Befehl kann nur von Spielern ausgef\u00fchrt werden.");
            return true;
        }
        Player player = (Player)sender;
        if (!player.hasPermission("guardian.inspect")) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "Du hast keine Berechtigung, diesen Befehl zu verwenden.");
            return true;
        }
        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null) {
            player.sendMessage(String.valueOf(ChatColor.YELLOW) + "Du musst einen Block in deiner N\u00e4he anvisieren.");
            return true;
        }
        BlockState blockState = targetBlock.getState();
        if (!(blockState instanceof InventoryHolder)) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "Der anvisierte Block ist kein Container.");
            return true;
        }
        InventoryHolder holder = (InventoryHolder)blockState;
        int page = 1;
        if (args.length > 0) {
            try {
                page = Integer.parseInt(args[0]);
                if (page < 1) {
                    player.sendMessage(String.valueOf(ChatColor.RED) + "Ung\u00fcltige Seitenzahl. Muss 1 oder gr\u00f6\u00dfer sein.");
                    return true;
                }
            } catch (NumberFormatException e) {
                player.sendMessage(String.valueOf(ChatColor.RED) + "Ung\u00fcltige Seitenzahl: '" + args[0] + "'. Bitte eine Zahl angeben.");
                return true;
            }
        }
        if ((queryLocation = DatabaseManager.getCanonicalLocation(holder)) == null) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "Konnte die Container-Position nicht eindeutig bestimmen.");
            return true;
        }
        int finalPage = page;
        player.sendMessage(String.valueOf(ChatColor.GRAY) + "Suche Itemverlauf f\u00fcr Container bei " + queryLocation.getBlockX() + "," + queryLocation.getBlockY() + "," + queryLocation.getBlockZ() + " (Seite " + finalPage + ")...");
        CompletableFuture<List<DatabaseManager.ContainerLogEntry>> future = this.databaseManager.lookupContainerHistoryAsync(queryLocation, finalPage, 8);
        this.displayContainerHistory(player, future, finalPage, label);
        return true;
    }

    private void displayContainerHistory(Player player, CompletableFuture<List<DatabaseManager.ContainerLogEntry>> future, int page, String commandLabel) {
        future.whenComplete((results, throwable) -> {
            if (throwable != null) {
                player.sendMessage(String.valueOf(ChatColor.RED) + "Fehler beim Abrufen des Container-Verlaufs: " + throwable.getMessage());
                return;
            }
            if (results == null || results.isEmpty()) {
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

