package Frxme.guardian;

import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;


public final class Guardian extends JavaPlugin {

    private DatabaseManager databaseManager;
    private final Set<UUID> inspectingPlayers = new HashSet<>();

    @Override
    public void onEnable() {
        this.databaseManager = new DatabaseManager(this);
        if (!databaseManager.connect() || !databaseManager.initializeDatabase()) {
            getLogger().severe("Datenbank Initialisierung fehlgeschlagen! Plugin wird deaktiviert.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(new BlockListener(this, this.databaseManager), this);

        registerLookupCommand();
        registerInspectCommand();
        registerGuardianContainerLookupCommand();

        getLogger().info("Guardian wurde erfolgreich aktiviert.");
    }

    @Override
    public void onDisable() {
        inspectingPlayers.clear();
        if (this.databaseManager != null) {
            this.databaseManager.disconnect();
        }
        getLogger().info("Guardian wurde deaktiviert.");
    }

    public boolean isInspecting(Player player) {
        return inspectingPlayers.contains(player.getUniqueId());
    }

    public void setInspecting(Player player, boolean isInspecting) {
        if (isInspecting) {
            inspectingPlayers.add(player.getUniqueId());
        } else {
            inspectingPlayers.remove(player.getUniqueId());
        }
    }

    private void registerLookupCommand() {
        try {
            final Field bukkitCommandMap = getServer().getClass().getDeclaredField("commandMap");
            bukkitCommandMap.setAccessible(true);
            CommandMap commandMap = (CommandMap) bukkitCommandMap.get(getServer());

            Command lookupCmd = new Command("lookup") {
                private final CommandExecutor executor = new LookupCommand(databaseManager);
                @Override
                public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
                    return executor.onCommand(sender, this, commandLabel, args);
                }
            };
            lookupCmd.setDescription("Überprüft den Verlauf des anvisierten Blocks.");
            lookupCmd.setUsage("/lookup [seite]");
            lookupCmd.setPermission("guardian.lookup");
            lookupCmd.setAliases(Arrays.asList("guard", "glookup")); // Deine Aliase anpassen
            commandMap.register(this.getName().toLowerCase(), lookupCmd);
            getLogger().info("Befehl '/lookup' erfolgreich registriert.");
        } catch (NoSuchFieldException | IllegalAccessException e) {
            getLogger().log(Level.SEVERE, "Konnte Befehl '/lookup' nicht registrieren!", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void registerInspectCommand() {
        try {
            final Field bukkitCommandMap = getServer().getClass().getDeclaredField("commandMap");
            bukkitCommandMap.setAccessible(true);
            CommandMap commandMap = (CommandMap) bukkitCommandMap.get(getServer());

            Command inspectCmd = new Command("inspect") {
                private final CommandExecutor executor = new InspectCommand(Guardian.this);
                @Override
                public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
                    return executor.onCommand(sender, this, commandLabel, args);
                }
            };
            inspectCmd.setDescription("Schaltet den Inspector-Modus um.");
            inspectCmd.setUsage("/inspect");
            inspectCmd.setPermission("guardian.inspect");
            inspectCmd.setAliases(Arrays.asList("ginspect", "gi")); // Deine Aliase anpassen

            commandMap.register(this.getName().toLowerCase(), inspectCmd);
            getLogger().info("Befehl '/inspect' erfolgreich registriert.");
        } catch (NoSuchFieldException | IllegalAccessException e) {
            getLogger().log(Level.SEVERE, "Konnte Befehl '/inspect' nicht registrieren!", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void registerGuardianContainerLookupCommand() {
        try {
            final Field bukkitCommandMap = getServer().getClass().getDeclaredField("commandMap");
            bukkitCommandMap.setAccessible(true);
            CommandMap commandMap = (CommandMap) bukkitCommandMap.get(getServer());

            Command guardianCmd = new Command("guardian") {
                private final CommandExecutor executor = new GuardianContainerLookupCommand(databaseManager);
                @Override
                public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
                    return executor.onCommand(sender, this, commandLabel, args);
                }
            };
            guardianCmd.setDescription("Überprüft den Item-Verlauf des anvisierten Containers.");
            guardianCmd.setUsage("/guardian [seite]");
            guardianCmd.setPermission("guardian.inspect");
            guardianCmd.setAliases(Arrays.asList("gcont")); // Deine Aliase anpassen

            commandMap.register(this.getName().toLowerCase(), guardianCmd);
            getLogger().info("Befehl '/guardian' erfolgreich registriert.");
        } catch (NoSuchFieldException | IllegalAccessException e) {
            getLogger().log(Level.SEVERE, "Konnte Befehl '/guardian' nicht registrieren!", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
}