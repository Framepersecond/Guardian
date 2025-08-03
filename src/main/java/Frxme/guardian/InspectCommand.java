package Frxme.guardian;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class InspectCommand implements CommandExecutor {

    private final Guardian plugin;

    public InspectCommand(Guardian plugin) {
        this.plugin = plugin;
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

        boolean currentlyInspecting = plugin.isInspecting(player);
        plugin.setInspecting(player, !currentlyInspecting);

        if (!currentlyInspecting) {
            player.sendMessage(ChatColor.GREEN + "Inspector aktiviert. " + ChatColor.GRAY + "Linksklick: Blockverlauf, Rechtsklick (Container): Itemverlauf.");
        } else {
            player.sendMessage(ChatColor.RED + "Inspector deaktiviert.");
        }

        return true;
    }
}