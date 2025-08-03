package Frxne.guardian;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class LookupCommand implements CommandExecutor {

    private final DatabaseManager databaseManager;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final int RESULTS_PER_PAGE = 8;

    public LookupCommand(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Dieser Befehl kann nur von Spielern ausgeführt werden.");
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("guardian.lookup")) {
            player.sendMessage(ChatColor.RED + "Du hast keine Berechtigung, diesen Befehl zu verwenden.");
            return true;
        }

        Block targetBlock = player.getTargetBlockExact(5);

        if (targetBlock == null) {
            player.sendMessage(ChatColor.YELLOW + "Du musst einen Block in deiner Nähe anvisieren.");
            return true;
        }

        int page = 1;
        if (args.length > 0) {
            try {
                page = Integer.parseInt(args[0]);
                if (page < 1) {
                    player.sendMessage(ChatColor.RED + "Ungültige Seitenzahl. Muss 1 oder größer sein.");
                    return true;
                }
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Ungültige Seitenzahl: '" + args[0] + "'. Bitte eine Zahl angeben.");
                return true;
            }
        }

        Location blockLocation = targetBlock.getLocation();
        final int finalPage = page;
        player.sendMessage(ChatColor.GRAY + "Suche nach Verlauf für Block bei "
                + blockLocation.getBlockX() + ", " + blockLocation.getBlockY() + ", " + blockLocation.getBlockZ()
                + " (Seite " + finalPage + ")...");

        CompletableFuture<List<DatabaseManager.BlockLogEntry>> future = databaseManager.lookupBlockHistoryAsync(blockLocation, finalPage, RESULTS_PER_PAGE);

        future.whenComplete((results, throwable) -> {
            if (throwable != null) {
                player.sendMessage(ChatColor.RED + "Fehler beim Abrufen des Verlaufs: " + throwable.getMessage());
                return;
            }

            if (results == null || results.isEmpty()) {
                if(finalPage == 1) {
                    player.sendMessage(ChatColor.YELLOW + "Kein Verlauf für diesen Block gefunden.");
                } else {
                    player.sendMessage(ChatColor.YELLOW + "Keine weiteren Einträge auf Seite " + finalPage + " gefunden.");
                }
                return;
            }

            player.sendMessage(ChatColor.GOLD + "--- Block Verlauf (Seite " + finalPage + ") ---");
            for (DatabaseManager.BlockLogEntry entry : results) {
                String actionString;
                String materialString;
                if (entry.action == DatabaseManager.ACTION_BREAK) {
                    actionString = ChatColor.RED + "zerstört";
                    materialString = entry.oldBlockType != null ? entry.oldBlockType : entry.blockType;
                } else {
                    actionString = ChatColor.GREEN + "platziert";
                    materialString = entry.blockType;
                }
                String formattedDate = dateFormat.format(new Date(entry.timestamp * 1000L));

                player.sendMessage(ChatColor.GRAY + "[" + formattedDate + "] "
                        + ChatColor.WHITE + entry.playerName + " "
                        + actionString + " "
                        + ChatColor.AQUA + materialString.toLowerCase().replace('_', ' '));
            }
            if (results.size() == RESULTS_PER_PAGE) {
                player.sendMessage(ChatColor.GRAY + "Für mehr Einträge: /" + label + " " + (finalPage + 1));
            }
        });

        return true;
    }
}