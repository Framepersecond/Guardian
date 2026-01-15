/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 * 
 * Could not load the following classes:
 *  org.bukkit.ChatColor
 *  org.bukkit.Location
 *  org.bukkit.block.Block
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
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
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class LookupCommand
implements CommandExecutor {
    private final DatabaseManager databaseManager;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final int RESULTS_PER_PAGE = 8;

    public LookupCommand(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(String.valueOf(ChatColor.RED) + "Dieser Befehl kann nur von Spielern ausgef\u00fchrt werden.");
            return true;
        }
        Player player = (Player)sender;
        if (!player.hasPermission("guardian.lookup")) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "Du hast keine Berechtigung, diesen Befehl zu verwenden.");
            return true;
        }
        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null) {
            player.sendMessage(String.valueOf(ChatColor.YELLOW) + "Du musst einen Block in deiner N\u00e4he anvisieren.");
            return true;
        }
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
        Location blockLocation = targetBlock.getLocation();
        int finalPage = page;
        player.sendMessage(String.valueOf(ChatColor.GRAY) + "Suche nach Verlauf f\u00fcr Block bei " + blockLocation.getBlockX() + ", " + blockLocation.getBlockY() + ", " + blockLocation.getBlockZ() + " (Seite " + finalPage + ")...");
        CompletableFuture<List<DatabaseManager.BlockLogEntry>> future = this.databaseManager.lookupBlockHistoryAsync(blockLocation, finalPage, 8);
        future.whenComplete((results, throwable) -> {
            if (throwable != null) {
                player.sendMessage(String.valueOf(ChatColor.RED) + "Fehler beim Abrufen des Verlaufs: " + throwable.getMessage());
                return;
            }
            if (results == null || results.isEmpty()) {
                if (finalPage == 1) {
                    player.sendMessage(String.valueOf(ChatColor.YELLOW) + "Kein Verlauf f\u00fcr diesen Block gefunden.");
                } else {
                    player.sendMessage(String.valueOf(ChatColor.YELLOW) + "Keine weiteren Eintr\u00e4ge auf Seite " + finalPage + " gefunden.");
                }
                return;
            }
            player.sendMessage(String.valueOf(ChatColor.GOLD) + "--- Block Verlauf (Seite " + finalPage + ") ---");
            for (DatabaseManager.BlockLogEntry entry : results) {
                String materialString;
                String actionString;
                if (entry.action == 0) {
                    actionString = String.valueOf(ChatColor.RED) + "zerst\u00f6rt";
                    materialString = entry.oldBlockType != null ? entry.oldBlockType : entry.blockType;
                } else {
                    actionString = String.valueOf(ChatColor.GREEN) + "platziert";
                    materialString = entry.blockType;
                }
                String formattedDate = this.dateFormat.format(new Date(entry.timestamp * 1000L));
                player.sendMessage(String.valueOf(ChatColor.GRAY) + "[" + formattedDate + "] " + String.valueOf(ChatColor.WHITE) + entry.playerName + " " + actionString + " " + String.valueOf(ChatColor.AQUA) + materialString.toLowerCase().replace('_', ' '));
            }
            if (results.size() == 8) {
                player.sendMessage(String.valueOf(ChatColor.GRAY) + "F\u00fcr mehr Eintr\u00e4ge: /" + label + " " + (finalPage + 1));
            }
        });
        return true;
    }
}

