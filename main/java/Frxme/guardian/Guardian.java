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
import Frxme.guardian.RegisterCommand;
import Frxme.guardian.SettingsGUI;
import Frxme.guardian.web.WebServer;
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
    private WebServer webServer;
    private final Set<UUID> inspectingPlayers = new HashSet<UUID>();
    private final Set<UUID> hideOwnActions = new HashSet<UUID>();
    private SettingsGUI settingsGUI;

    public void onEnable() {
        // Save default config
        saveDefaultConfig();

        this.databaseManager = new DatabaseManager(this);
        if (!this.databaseManager.connect() || !this.databaseManager.initializeDatabase()) {
            this.getLogger().severe("Datenbank Initialisierung fehlgeschlagen! Plugin wird deaktiviert.");
            this.getServer().getPluginManager().disablePlugin((Plugin) this);
            return;
        }
        this.getServer().getPluginManager().registerEvents((Listener) new BlockListener(this, this.databaseManager),
                (Plugin) this);

        // Initialize BlueMap integration (if present) - delayed to ensure BlueMap is
        // loaded
        if (getServer().getPluginManager().getPlugin("BlueMap") != null) {
            // Delay initialization by 5 seconds to ensure BlueMap API is fully available
            getServer().getScheduler().runTaskLater(this, () -> {
                try {
                    getLogger().info("Attempting to initialize BlueMap integration...");
                    new Frxme.guardian.integrations.BlueMapIntegration(this).initialize();
                    getLogger().info("BlueMap integration initialized successfully!");
                } catch (NoClassDefFoundError e) {
                    getLogger().warning("BlueMap API classes not found: " + e.getMessage());
                    getLogger().info("BlueMap marker integration skipped.");
                } catch (Exception e) {
                    getLogger().warning("Failed to initialize BlueMap integration: " + e.getMessage());
                }
            }, 100L); // 100 ticks = 5 seconds delay
        }

        this.settingsGUI = new SettingsGUI(this);
        this.getServer().getPluginManager().registerEvents((Listener) this.settingsGUI, (Plugin) this);
        this.registerLookupCommand();
        this.registerInspectCommand();
        this.registerGuardianCommand();
        this.registerSettingsCommand();

        // Start web server if enabled
        if (getConfig().getBoolean("web.enabled", true)) {
            int port = getConfig().getInt("web.port", 8080);
            String host = getConfig().getString("web.host", "0.0.0.0");
            String jwtSecret = getConfig().getString("web.jwt-secret", "CHANGE-ME-TO-SOMETHING-RANDOM");

            if (jwtSecret.equals("CHANGE-ME-TO-SOMETHING-RANDOM-AND-SECURE")) {
                getLogger().warning("WARNUNG: Bitte ändere das JWT-Secret in der config.yml!");
            }

            this.webServer = new WebServer(this, port, host, jwtSecret);
            this.webServer.start();

            // Start setup reminder if setup is not completed
            startSetupReminder(port);
        }

        this.getLogger().info("Guardian wurde erfolgreich aktiviert.");
    }

    public void onDisable() {
        this.inspectingPlayers.clear();
        this.hideOwnActions.clear();

        if (this.webServer != null) {
            this.webServer.stop();
        }

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
            CommandMap commandMap = (CommandMap) bukkitCommandMap.get(this.getServer());
            Command lookupCmd = new Command("lookup") {
                private final CommandExecutor executor;
                {
                    this.executor = new LookupCommand(Guardian.this.databaseManager);
                }

                public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel,
                        @NotNull String[] args) {
                    return this.executor.onCommand(sender, (Command) this, commandLabel, args);
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
            this.getServer().getPluginManager().disablePlugin((Plugin) this);
        }
    }

    private void registerInspectCommand() {
        try {
            Field bukkitCommandMap = this.getServer().getClass().getDeclaredField("commandMap");
            bukkitCommandMap.setAccessible(true);
            CommandMap commandMap = (CommandMap) bukkitCommandMap.get(this.getServer());
            Command inspectCmd = new Command("inspect") {
                private final CommandExecutor executor;
                {
                    this.executor = new InspectCommand(Guardian.this);
                }

                public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel,
                        @NotNull String[] args) {
                    return this.executor.onCommand(sender, (Command) this, commandLabel, args);
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
            this.getServer().getPluginManager().disablePlugin((Plugin) this);
        }
    }

    private void registerGuardianCommand() {
        try {
            Field bukkitCommandMap = this.getServer().getClass().getDeclaredField("commandMap");
            bukkitCommandMap.setAccessible(true);
            CommandMap commandMap = (CommandMap) bukkitCommandMap.get(this.getServer());

            final CommandExecutor containerExecutor = new GuardianContainerLookupCommand(Guardian.this.databaseManager);
            final RegisterCommand registerCommand = new RegisterCommand(Guardian.this);

            Command guardianCmd = new Command("guardian") {
                public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel,
                        @NotNull String[] args) {
                    // Handle subcommands
                    if (args.length > 0 && args[0].equalsIgnoreCase("register")) {
                        return registerCommand.onCommand(sender, (Command) this, commandLabel, new String[0]);
                    }
                    // Default: container lookup
                    return containerExecutor.onCommand(sender, (Command) this, commandLabel, args);
                }

                @Override
                public @NotNull java.util.List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias,
                        @NotNull String[] args) {
                    java.util.List<String> completions = new java.util.ArrayList<>();

                    if (args.length == 1) {
                        String partial = args[0].toLowerCase();
                        if ("register".startsWith(partial)) {
                            completions.add("register");
                        }
                    }

                    return completions;
                }
            };
            guardianCmd.setDescription("Guardian Hauptbefehl - Container-Lookup oder Registrierung.");
            guardianCmd.setUsage("/guardian [register|seite]");
            guardianCmd.setAliases(Arrays.asList("gcont"));
            commandMap.register(this.getName().toLowerCase(), guardianCmd);
            this.getLogger().info("Befehl '/guardian' erfolgreich registriert.");
        } catch (IllegalAccessException | NoSuchFieldException e) {
            this.getLogger().log(Level.SEVERE, "Konnte Befehl '/guardian' nicht registrieren!", e);
            this.getServer().getPluginManager().disablePlugin((Plugin) this);
        }
    }

    private void registerSettingsCommand() {
        try {
            Field bukkitCommandMap = this.getServer().getClass().getDeclaredField("commandMap");
            bukkitCommandMap.setAccessible(true);
            CommandMap commandMap = (CommandMap) bukkitCommandMap.get(this.getServer());
            Command settingsCmd = new Command("gsettings") {

                public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel,
                        @NotNull String[] args) {
                    if (!(sender instanceof Player)) {
                        sender.sendMessage("Dieser Befehl kann nur von Spielern verwendet werden.");
                        return true;
                    }
                    Player player = (Player) sender;
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
            this.getServer().getPluginManager().disablePlugin((Plugin) this);
        }
    }

    public DatabaseManager getDatabaseManager() {
        return this.databaseManager;
    }

    public WebServer getWebServer() {
        return this.webServer;
    }

    /**
     * Check if initial setup has been completed
     */
    public boolean isSetupCompleted() {
        return getConfig().getBoolean("setup.completed", false);
    }

    /**
     * Mark setup as completed and save config
     */
    public void completeSetup() {
        getConfig().set("setup.completed", true);
        saveConfig();
        getLogger().info("Guardian Setup abgeschlossen!");
    }

    /**
     * Get configured timezone
     */
    public String getTimezone() {
        return getConfig().getString("timezone", "Europe/Berlin");
    }

    /**
     * Start the setup reminder that notifies OPs every 5 minutes
     */
    private void startSetupReminder(int port) {
        if (isSetupCompleted()) {
            return;
        }

        // Initial reminder after 30 seconds
        getServer().getScheduler().runTaskLater(this, () -> sendSetupReminder(port), 20L * 30);

        // Repeat every 5 minutes (6000 ticks)
        getServer().getScheduler().runTaskTimer(this, () -> {
            if (!isSetupCompleted()) {
                sendSetupReminder(port);
            }
        }, 20L * 60 * 5, 20L * 60 * 5);
    }

    /**
     * Send setup reminder to all online OPs
     */
    private void sendSetupReminder(int port) {
        if (isSetupCompleted()) {
            return;
        }

        // Get server IP address
        String serverIp = getServerIp();
        String setupUrl = "http://" + serverIp + ":" + port + "/setup.html";

        for (Player player : getServer().getOnlinePlayers()) {
            if (player.isOp() || player.hasPermission("guardian.admin")) {
                player.sendMessage("");
                player.sendMessage("§6§l[Guardian] §e⚠ Das Plugin wurde noch nicht eingerichtet!");
                player.sendMessage("§7Öffne die Setup-Seite: §b§n" + setupUrl);
                player.sendMessage("");

                // Send clickable message using Spigot API
                try {
                    net.md_5.bungee.api.chat.TextComponent message = new net.md_5.bungee.api.chat.TextComponent(
                            "§a§l[Hier klicken zum Einrichten]");
                    message.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                            net.md_5.bungee.api.chat.ClickEvent.Action.OPEN_URL, setupUrl));
                    message.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                            net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                            new net.md_5.bungee.api.chat.ComponentBuilder("§7Klicke um die Setup-Seite zu öffnen")
                                    .create()));
                    player.spigot().sendMessage(message);
                } catch (Exception e) {
                    // Fallback if Spigot API not available
                    player.sendMessage("§aSetup-URL: §n" + setupUrl);
                }
                player.sendMessage("");
            }
        }
    }

    // Cached public IP to avoid repeated HTTP calls
    private String cachedPublicIp = null;

    /**
     * Get the server's public IP address
     * Uses external IP echo service for accurate detection
     */
    public String getServerIp() {
        // First check if manually configured in Guardian config
        String configuredIp = getConfig().getString("web.server-ip", "");
        if (configuredIp != null && !configuredIp.isEmpty()) {
            return configuredIp;
        }

        // Return cached IP if available
        if (cachedPublicIp != null) {
            return cachedPublicIp;
        }

        // Try to get the server's configured IP from server.properties
        String serverIp = getServer().getIp();
        if (serverIp != null && !serverIp.isEmpty() && !serverIp.equals("0.0.0.0")) {
            cachedPublicIp = serverIp;
            return serverIp;
        }

        // Use external IP echo service to get public IP
        cachedPublicIp = fetchPublicIp();
        return cachedPublicIp;
    }

    /**
     * Fetch public IP from external echo service
     */
    private String fetchPublicIp() {
        // List of IP echo services to try
        String[] services = {
                "http://checkip.amazonaws.com",
                "https://ipv4.icanhazip.com",
                "http://myexternalip.com/raw"
        };

        for (String serviceUrl : services) {
            try {
                java.net.URL url = new java.net.URL(serviceUrl);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                conn.setRequestMethod("GET");

                if (conn.getResponseCode() == 200) {
                    try (java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(conn.getInputStream()))) {
                        String ip = reader.readLine();
                        if (ip != null) {
                            ip = ip.trim();
                            // Validate it looks like an IP
                            if (ip.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) {
                                getLogger().info("Detected public IP: " + ip);
                                return ip;
                            }
                        }
                    }
                }
                conn.disconnect();
            } catch (Exception e) {
                // Try next service
                getLogger().fine("IP service " + serviceUrl + " failed: " + e.getMessage());
            }
        }

        // Fallback to localhost if all services fail
        getLogger().warning("Could not detect public IP, using localhost. Set web.server-ip in config.yml manually.");
        return "localhost";
    }
}
