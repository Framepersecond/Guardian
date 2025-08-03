package Frxne.guardian;

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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;


public class GuardianContainerLookupCommand implements CommandExecutor {

    private final DatabaseManager databaseManager;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final int RESULTS_PER_PAGE = 8;

    public GuardianContainerLookupCommand(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Dieser Befehl kann nur von Spielern ausgeführt werden.");
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("guardian.inspect")) {
            player.sendMessage(ChatColor.RED + "Du hast keine Berechtigung, diesen Befehl zu verwenden.");
            return true;
        }

        Block targetBlock = player.getTargetBlockExact(5);

        if (targetBlock == null) {
            player.sendMessage(ChatColor.YELLOW + "Du musst einen Block in deiner Nähe anvisieren.");
            return true;
        }

        BlockState blockState = targetBlock.getState();
        if (!(blockState instanceof InventoryHolder)) {
            player.sendMessage(ChatColor.RED + "Der anvisierte Block ist kein Container.");
            return true;
        }

        InventoryHolder holder = (InventoryHolder) blockState;

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

        Location queryLocation = DatabaseManager.getCanonicalLocation(holder);
        if (queryLocation == null) {
            player.sendMessage(ChatColor.RED + "Konnte die Container-Position nicht eindeutig bestimmen.");
            return true;
        }


        final int finalPage = page;
        player.sendMessage(ChatColor.GRAY + "Suche Itemverlauf für Container bei "
                + queryLocation.getBlockX() + "," + queryLocation.getBlockY() + "," + queryLocation.getBlockZ()
                + " (Seite " + finalPage + ")...");

        CompletableFuture<List<DatabaseManager.ContainerLogEntry>> future = databaseManager.lookupContainerHistoryAsync(queryLocation, finalPage, RESULTS_PER_PAGE);

        displayContainerHistory(player, future, finalPage, label);

        return true;
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
