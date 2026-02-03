/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.ChatColor
 *  org.bukkit.Material
 *  org.bukkit.entity.HumanEntity
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.inventory.InventoryClickEvent
 *  org.bukkit.inventory.Inventory
 *  org.bukkit.inventory.ItemFlag
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 */
package Frxme.guardian;

import Frxme.guardian.Guardian;
import java.util.Arrays;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class SettingsGUI
implements Listener {
    private final Guardian plugin;
    public static final String TITLE = String.valueOf(ChatColor.DARK_AQUA) + "Guardian Einstellungen";

    public SettingsGUI(Guardian plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, (int)9, (String)TITLE);
        inv.setItem(4, this.buildToggleItem(player));
        player.openInventory(inv);
    }

    private ItemStack buildToggleItem(Player player) {
        boolean onlyOthers = this.plugin.isHideOwnActions(player);
        Material mat = onlyOthers ? Material.LIME_DYE : Material.RED_DYE;
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(String.valueOf(ChatColor.GOLD) + "Nur andere anzeigen: " + (onlyOthers ? String.valueOf(ChatColor.GREEN) + "AN" : String.valueOf(ChatColor.RED) + "AUS"));
            meta.setLore(Arrays.asList(String.valueOf(ChatColor.GRAY) + "Wenn AN, werden deine eigenen", String.valueOf(ChatColor.GRAY) + "Aktionen in Anzeigen gefiltert.", String.valueOf(ChatColor.DARK_GRAY) + "Klicke zum Umschalten"));
            meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ATTRIBUTES});
            stack.setItemMeta(meta);
        }
        return stack;
    }

    @EventHandler(priority=EventPriority.NORMAL)
    public void onInventoryClick(InventoryClickEvent event) {
        HumanEntity who = event.getWhoClicked();
        if (!(who instanceof Player)) {
            return;
        }
        if (!event.getView().getTitle().equals(TITLE)) {
            return;
        }
        event.setCancelled(true);
        if (event.getRawSlot() == 4) {
            Player player;
            boolean current = this.plugin.isHideOwnActions(player = (Player)who);
            this.plugin.setHideOwnActions(player, !current);
            Inventory inv = event.getInventory();
            inv.setItem(4, this.buildToggleItem(player));
            player.sendMessage(String.valueOf(ChatColor.GRAY) + "Einstellung 'Nur andere anzeigen' ist nun " + (!current ? String.valueOf(ChatColor.GREEN) + "AN" : String.valueOf(ChatColor.RED) + "AUS"));
        }
    }
}

