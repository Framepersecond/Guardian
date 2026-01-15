/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 * 
 * Could not load the following classes:
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandMap
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 *  org.bukkit.event.Listener
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.java.JavaPlugin
 *  org.jetbrains.annotations.NotNull
 */
package Frxme.guardian;

import Frxme.guardian.BlockListener;
import Frxme.guardian.DatabaseManager;
import Frxme.guardian.GuardianContainerLookupCommand;
import Frxme.guardian.InspectCommand;
import Frxme.guardian.LookupCommand;
import Frxme.guardian.SettingsGUI;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public final class Guardian
extends JavaPlugin {
    private DatabaseManager databaseManager;
    private final Set<UUID> inspectingPlayers = new HashSet<UUID>();
    private final Set<UUID> hideOwnActions = new HashSet<UUID>();
    private SettingsGUI settingsGUI;

    public void onEnable() {
        this.databaseManager = new DatabaseManager(this);
        if (!this.databaseManager.connect() || !this.databaseManager.initializeDatabase()) {
            this.getLogger().severe("Datenbank Initialisierung fehlgeschlagen! Plugin wird deaktiviert.");
            this.getServer().getPluginManager().disablePlugin((Plugin)this);
            return;
        }
        this.getServer().getPluginManager().registerEvents((Listener)new BlockListener(this, this.databaseManager), (Plugin)this);
        this.settingsGUI = new SettingsGUI(this);
        this.getServer().getPluginManager().registerEvents((Listener)this.settingsGUI, (Plugin)this);
        this.registerLookupCommand();
        this.registerInspectCommand();
        this.registerGuardianContainerLookupCommand();
        this.registerSettingsCommand();
        this.getLogger().info("Guardian wurde erfolgreich aktiviert.");
    }

    public void onDisable() {
        this.inspectingPlayers.clear();
        this.hideOwnActions.clear();
        if (this.databaseManager != null) {
            this.databaseManager.disconnect();
        }
        this.getLogger().info("Guardian wurde deaktiviert.");
    }

    public boolean isInspecting(Player player) {
        return this.inspectingPlayers.contains(player.getUniqueId());
    }

    public void setInspecting(Player player, boolean isInspecting) {
        if (isInspecting) {
            this.inspectingPlayers.add(player.getUniqueId());
        } else {
            this.inspectingPlayers.remove(player.getUniqueId());
        }
    }

    public boolean isHideOwnActions(Player player) {
        return this.hideOwnActions.contains(player.getUniqueId());
    }

    public void setHideOwnActions(Player player, boolean hide) {
        if (hide) {
            this.hideOwnActions.add(player.getUniqueId());
        } else {
            this.hideOwnActions.remove(player.getUniqueId());
        }
    }

    private void registerLookupCommand() {
        try {
            Field bukkitCommandMap = this.getServer().getClass().getDeclaredField("commandMap");
            bukkitCommandMap.setAccessible(true);
            CommandMap commandMap = (CommandMap)bukkitCommandMap.get(this.getServer());
            Command lookupCmd = new Command("lookup"){
                private final CommandExecutor executor;
                {
                    this.executor = new LookupCommand(Guardian.this.databaseManager);
                }

                public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
                    return this.executor.onCommand(sender, (Command)this, commandLabel, args);
                }
            };
            lookupCmd.setDescription("\u00dcberpr\u00fcft den Verlauf des anvisierten Blocks.");
            lookupCmd.setUsage("/lookup [seite]");
            lookupCmd.setPermission("guardian.lookup");
            lookupCmd.setAliases(Arrays.asList("guard", "glookup"));
            commandMap.register(this.getName().toLowerCase(), lookupCmd);
            this.getLogger().info("Befehl '/lookup' erfolgreich registriert.");
        } catch (IllegalAccessException | NoSuchFieldException e) {
            this.getLogger().log(Level.SEVERE, "Konnte Befehl '/lookup' nicht registrieren!", e);
            this.getServer().getPluginManager().disablePlugin((Plugin)this);
        }
    }

    private void registerInspectCommand() {
        try {
            Field bukkitCommandMap = this.getServer().getClass().getDeclaredField("commandMap");
            bukkitCommandMap.setAccessible(true);
            CommandMap commandMap = (CommandMap)bukkitCommandMap.get(this.getServer());
            Command inspectCmd = new Command("inspect"){
                private final CommandExecutor executor;
                {
                    this.executor = new InspectCommand(Guardian.this);
                }

                public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
                    return this.executor.onCommand(sender, (Command)this, commandLabel, args);
                }
            };
            inspectCmd.setDescription("Schaltet den Inspector-Modus um.");
            inspectCmd.setUsage("/inspect");
            inspectCmd.setPermission("guardian.inspect");
            inspectCmd.setAliases(Arrays.asList("ginspect", "gi"));
            commandMap.register(this.getName().toLowerCase(), inspectCmd);
            this.getLogger().info("Befehl '/inspect' erfolgreich registriert.");
        } catch (IllegalAccessException | NoSuchFieldException e) {
            this.getLogger().log(Level.SEVERE, "Konnte Befehl '/inspect' nicht registrieren!", e);
            this.getServer().getPluginManager().disablePlugin((Plugin)this);
        }
    }

    private void registerGuardianContainerLookupCommand() {
        try {
            Field bukkitCommandMap = this.getServer().getClass().getDeclaredField("commandMap");
            bukkitCommandMap.setAccessible(true);
            CommandMap commandMap = (CommandMap)bukkitCommandMap.get(this.getServer());
            Command guardianCmd = new Command("guardian"){
                private final CommandExecutor executor;
                {
                    this.executor = new GuardianContainerLookupCommand(Guardian.this.databaseManager);
                }

                public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
                    return this.executor.onCommand(sender, (Command)this, commandLabel, args);
                }
            };
            guardianCmd.setDescription("\u00dcberpr\u00fcft den Item-Verlauf des anvisierten Containers.");
            guardianCmd.setUsage("/guardian [seite]");
            guardianCmd.setPermission("guardian.inspect");
            guardianCmd.setAliases(Arrays.asList("gcont"));
            commandMap.register(this.getName().toLowerCase(), guardianCmd);
            this.getLogger().info("Befehl '/guardian' erfolgreich registriert.");
        } catch (IllegalAccessException | NoSuchFieldException e) {
            this.getLogger().log(Level.SEVERE, "Konnte Befehl '/guardian' nicht registrieren!", e);
            this.getServer().getPluginManager().disablePlugin((Plugin)this);
        }
    }

    private void registerSettingsCommand() {
        try {
            Field bukkitCommandMap = this.getServer().getClass().getDeclaredField("commandMap");
            bukkitCommandMap.setAccessible(true);
            CommandMap commandMap = (CommandMap)bukkitCommandMap.get(this.getServer());
            Command settingsCmd = new Command("gsettings"){

                public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
                    if (!(sender instanceof Player)) {
                        sender.sendMessage("Dieser Befehl kann nur von Spielern verwendet werden.");
                        return true;
                    }
                    Player player = (Player)sender;
                    if (!player.hasPermission("guardian.settings")) {
                        player.sendMessage("\u00a7cDu hast keine Berechtigung, diesen Befehl zu verwenden.");
                        return true;
                    }
                    Guardian.this.settingsGUI.open(player);
                    return true;
                }
            };
            settingsCmd.setDescription("\u00d6ffnet die Guardian Einstellungen.");
            settingsCmd.setUsage("/gsettings");
            settingsCmd.setPermission("guardian.settings");
            settingsCmd.setAliases(Arrays.asList("guardiansettings", "gset"));
            commandMap.register(this.getName().toLowerCase(), settingsCmd);
            this.getLogger().info("Befehl '/gsettings' erfolgreich registriert.");
        } catch (IllegalAccessException | NoSuchFieldException e) {
            this.getLogger().log(Level.SEVERE, "Konnte Befehl '/gsettings' nicht registrieren!", e);
            this.getServer().getPluginManager().disablePlugin((Plugin)this);
        }
    }

    public DatabaseManager getDatabaseManager() {
        return this.databaseManager;
    }
}

