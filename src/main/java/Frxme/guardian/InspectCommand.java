/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 * 
 * Could not load the following classes:
 *  org.bukkit.ChatColor
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 */
package Frxme.guardian;

import Frxme.guardian.Guardian;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class InspectCommand
implements CommandExecutor {
    private final Guardian plugin;

    public InspectCommand(Guardian plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(String.valueOf(ChatColor.RED) + "Dieser Befehl kann nur von Spielern ausgef\u00fchrt werden.");
            return true;
        }
        Player player = (Player)sender;
        if (!player.hasPermission("guardian.inspect")) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "Du hast keine Berechtigung, diesen Befehl zu verwenden.");
            return true;
        }
        boolean currentlyInspecting = this.plugin.isInspecting(player);
        this.plugin.setInspecting(player, !currentlyInspecting);
        if (!currentlyInspecting) {
            player.sendMessage(String.valueOf(ChatColor.GREEN) + "Inspector aktiviert. " + String.valueOf(ChatColor.GRAY) + "Linksklick: Blockverlauf, Rechtsklick (Container): Itemverlauf.");
        } else {
            player.sendMessage(String.valueOf(ChatColor.RED) + "Inspector deaktiviert.");
        }
        return true;
    }
}

