package Frxme.guardian.web;

import Frxme.guardian.DatabaseManager;
import Frxme.guardian.Guardian;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.staticfiles.Location;

import java.util.logging.Level;

public class WebServer {
    private final Guardian plugin;
    private final DatabaseManager db;
    private final JwtUtil jwtUtil;
    private final Gson gson;
    private Javalin app;
    private final int port;
    private final String host;

    public WebServer(Guardian plugin, int port, String host, String jwtSecret) {
        this.plugin = plugin;
        this.db = plugin.getDatabaseManager();
        this.port = port;
        this.host = host;
        this.jwtUtil = new JwtUtil(jwtSecret, 60 * 24); // 24 hours
        this.gson = new GsonBuilder().create();
    }

    public void start() {
        try {
            app = Javalin.create(config -> {
                config.staticFiles.add(staticFiles -> {
                    staticFiles.hostedPath = "/";
                    staticFiles.directory = "/web";
                    staticFiles.location = Location.CLASSPATH;
                });
                config.http.defaultContentType = "application/json";
            });

            // CORS
            app.before(ctx -> {
                ctx.header("Access-Control-Allow-Origin", "*");
                ctx.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                ctx.header("Access-Control-Allow-Headers", "Content-Type, Authorization");
            });

            app.options("/*", ctx -> ctx.status(200));

            // Auth routes (public)
            app.post("/api/register", this::handleRegister);
            app.post("/api/login", this::handleLogin);

            // Protected route - requires valid token
            app.get("/api/me", ctx -> {
                if (!requireAuth(ctx))
                    return;
                handleGetMe(ctx);
            });

            // Log routes (protected)
            app.get("/api/logs/blocks", ctx -> {
                if (!requireAuth(ctx))
                    return;
                handleGetBlockLogs(ctx);
            });
            app.get("/api/logs/containers", ctx -> {
                if (!requireAuth(ctx))
                    return;
                handleGetContainerLogs(ctx);
            });

            // Admin routes (protected, admin only)
            // Note: stats and heatmap are accessible to all authenticated users
            app.get("/api/admin/stats", ctx -> {
                if (!requireAuth(ctx))
                    return;
                handleGetStats(ctx);
            });
            app.get("/api/admin/heatmap", ctx -> {
                if (!requireAuth(ctx))
                    return;
                handleGetHeatmap(ctx);
            });
            app.get("/api/admin/suspicious", ctx -> {
                if (!requireAdmin(ctx))
                    return;
                handleGetSuspicious(ctx);
            });
            app.get("/api/admin/users", ctx -> {
                if (!requireAdmin(ctx))
                    return;
                handleGetUsers(ctx);
            });
            app.delete("/api/admin/users/{id}", ctx -> {
                if (!requireAdmin(ctx))
                    return;
                handleDeleteUser(ctx);
            });

            // Admin settings routes
            app.get("/api/admin/settings", ctx -> {
                if (!requireAdmin(ctx))
                    return;
                handleGetAdminSettings(ctx);
            });
            app.post("/api/admin/settings", ctx -> {
                if (!requireAdmin(ctx))
                    return;
                handleSaveAdminSettings(ctx);
            });

            // Statistics routes (protected)
            app.get("/api/stats/timeline", ctx -> {
                if (!requireAuth(ctx))
                    return;
                handleGetTimeline(ctx);
            });

            // Export routes (protected)
            app.get("/api/export/blocks", ctx -> {
                if (!requireAuth(ctx))
                    return;
                handleExportBlocks(ctx);
            });
            app.get("/api/export/containers", ctx -> {
                if (!requireAuth(ctx))
                    return;
                handleExportContainers(ctx);
            });

            // Utility routes (protected)
            app.get("/api/worlds", ctx -> {
                if (!requireAuth(ctx))
                    return;
                handleGetWorlds(ctx);
            });

            // App Config (protected)
            app.get("/api/config", ctx -> {
                if (!requireAuth(ctx))
                    return;
                handleGetConfig(ctx);
            });

            // Setup routes (no auth required for first-time setup)
            app.get("/api/setup/status", this::handleGetSetupStatus);
            app.get("/api/setup/config", this::handleGetSetupConfig);
            app.post("/api/setup/save", this::handleSaveSetup);
            app.get("/api/setup/timezones", this::handleGetTimezones);
            app.get("/api/setup/test-bluemap", this::handleTestBlueMap);

            // Heatmap data endpoint (protected) - Admin only with coords
            app.get("/api/stats/heatmap", ctx -> {
                if (!requireAuth(ctx))
                    return;
                handleGetHeatmap(ctx);
            });

            // Analytics endpoints
            app.get("/api/stats/peak-hours", ctx -> {
                if (!requireAuth(ctx))
                    return;
                handleGetPeakHours(ctx);
            });

            app.get("/api/stats/top-players", ctx -> {
                if (!requireAuth(ctx))
                    return;
                handleGetTopPlayers(ctx);
            });

            app.get("/api/stats/block-types", ctx -> {
                if (!requireAuth(ctx))
                    return;
                handleGetBlockTypes(ctx);
            });

            app.get("/api/stats/custom", ctx -> {
                if (!requireAuth(ctx))
                    return;
                handleGetCustomStats(ctx);
            });

            // BlueMap Proxy - allows BlueMap to run on localhost while being accessible
            // through Guardian
            app.get("/bluemap", ctx -> ctx.redirect("/bluemap/"));
            app.get("/bluemap/", ctx -> handleBlueMapProxy(ctx, "/"));
            app.get("/bluemap/*", ctx -> handleBlueMapProxy(ctx, ctx.pathParam("*")));
            app.post("/bluemap/*", ctx -> handleBlueMapProxy(ctx, ctx.pathParam("*")));

            app.start(host, port);
            plugin.getLogger().info("Web-Interface gestartet auf http://" + host + ":" + port);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Starten des Web-Interfaces!", e);
        }
    }

    /**
     * Validates the JWT token from the Authorization header
     * 
     * @return TokenPayload if valid, null if invalid
     */
    private JwtUtil.TokenPayload getAuthenticatedUser(Context ctx) {
        String authHeader = ctx.header("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authHeader.substring(7);
        return jwtUtil.extractPayload(token);
    }

    /**
     * Requires authentication - returns true if authenticated, false and sends
     * error if not
     */
    private boolean requireAuth(Context ctx) {
        JwtUtil.TokenPayload user = getAuthenticatedUser(ctx);
        if (user == null) {
            ctx.status(401);
            sendJson(ctx, 401, new ErrorResponse("Nicht authentifiziert. Bitte erneut anmelden."));
            return false;
        }
        ctx.attribute("user", user);
        return true;
    }

    /**
     * Requires admin role - returns true if admin, false and sends error if not
     */
    private boolean requireAdmin(Context ctx) {
        if (!requireAuth(ctx)) {
            return false;
        }
        JwtUtil.TokenPayload user = ctx.attribute("user");
        if (user == null || !"admin".equals(user.role)) {
            ctx.status(403);
            sendJson(ctx, 403, new ErrorResponse("Keine Berechtigung. Admin-Rolle erforderlich."));
            return false;
        }
        return true;
    }

    public void stop() {
        if (app != null) {
            app.stop();
            plugin.getLogger().info("Web-Interface gestoppt.");
        }
    }

    // Helper for Gson serialization
    private void sendJson(Context ctx, Object response) {
        ctx.contentType("application/json").result(gson.toJson(response));
    }

    private void sendJson(Context ctx, int status, Object response) {
        ctx.status(status);
        sendJson(ctx, response);
    }

    // Auth handlers
    private void handleRegister(Context ctx) {
        RegisterRequest req = gson.fromJson(ctx.body(), RegisterRequest.class);

        if (req.code == null || req.username == null || req.password == null) {
            sendJson(ctx, 400, new ErrorResponse("Alle Felder sind erforderlich."));
            return;
        }

        if (req.username.length() < 3 || req.username.length() > 32) {
            sendJson(ctx, 400, new ErrorResponse("Benutzername muss zwischen 3 und 32 Zeichen lang sein."));
            return;
        }

        if (req.password.length() < 6) {
            sendJson(ctx, 400, new ErrorResponse("Passwort muss mindestens 6 Zeichen lang sein."));
            return;
        }

        // Validate registration code
        DatabaseManager.RegistrationCodeInfo codeInfo = db.validateRegistrationCode(req.code);
        if (codeInfo == null) {
            sendJson(ctx, 400, new ErrorResponse("Ungültiger oder abgelaufener Registrierungscode."));
            return;
        }

        // Check if username is taken
        if (db.getWebUserByUsername(req.username) != null) {
            sendJson(ctx, 400, new ErrorResponse("Benutzername bereits vergeben."));
            return;
        }

        // Hash password
        String passwordHash = org.mindrot.jbcrypt.BCrypt.hashpw(req.password, org.mindrot.jbcrypt.BCrypt.gensalt());

        // Create user
        boolean success = db.createWebUser(req.username, passwordHash, codeInfo.minecraftUUID, codeInfo.minecraftName,
                codeInfo.role);
        if (!success) {
            sendJson(ctx, 500, new ErrorResponse("Fehler beim Erstellen des Benutzers."));
            return;
        }

        // Mark code as used
        db.markRegistrationCodeUsed(req.code);

        // Get created user and generate token
        DatabaseManager.WebUser user = db.getWebUserByUsername(req.username);
        String token = jwtUtil.generateToken(user.id, user.username, user.role);

        sendJson(ctx, new AuthResponse(token, user.username, user.role, user.minecraftName));
    }

    private void handleLogin(Context ctx) {
        LoginRequest req = gson.fromJson(ctx.body(), LoginRequest.class);

        if (req.username == null || req.password == null) {
            sendJson(ctx, 400, new ErrorResponse("Benutzername und Passwort sind erforderlich."));
            return;
        }

        DatabaseManager.WebUser user = db.getWebUserByUsername(req.username);
        if (user == null) {
            sendJson(ctx, 401, new ErrorResponse("Ungültiger Benutzername oder Passwort."));
            return;
        }

        if (!org.mindrot.jbcrypt.BCrypt.checkpw(req.password, user.passwordHash)) {
            sendJson(ctx, 401, new ErrorResponse("Ungültiger Benutzername oder Passwort."));
            return;
        }

        db.updateLastLogin(user.id);
        String token = jwtUtil.generateToken(user.id, user.username, user.role);

        sendJson(ctx, new AuthResponse(token, user.username, user.role, user.minecraftName));
    }

    private void handleGetMe(Context ctx) {
        JwtUtil.TokenPayload payload = authenticateRequest(ctx);
        if (payload == null)
            return;

        DatabaseManager.WebUser user = db.getWebUserByUsername(payload.username);
        if (user == null) {
            sendJson(ctx, 404, new ErrorResponse("Benutzer nicht gefunden."));
            return;
        }

        sendJson(ctx, new UserInfo(user.id, user.username, user.role, user.minecraftName, user.createdAt));
    }

    private void handleGetConfig(Context ctx) {
        // Only return safe config values (no secrets!)
        boolean blueMapEnabled = plugin.getConfig().getBoolean("map.enabled", false);
        String blueMapUrl = plugin.getConfig().getString("map.bluemap-url", "");
        String defaultWorld = plugin.getConfig().getString("map.default-world", "world");
        sendJson(ctx, new ConfigResponse(blueMapEnabled, blueMapUrl, defaultWorld));
    }

    // Log handlers
    private void handleGetBlockLogs(Context ctx) {
        JwtUtil.TokenPayload payload = authenticateRequest(ctx);
        if (payload == null)
            return;

        String playerName = ctx.queryParam("player");
        String world = ctx.queryParam("world");
        String fromTimeStr = ctx.queryParam("from");
        String toTimeStr = ctx.queryParam("to");
        int page = ctx.queryParamAsClass("page", Integer.class).getOrDefault(1);
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(50);

        Long fromTime = fromTimeStr != null ? Long.parseLong(fromTimeStr) : null;
        Long toTime = toTimeStr != null ? Long.parseLong(toTimeStr) : null;

        // Non-admins can only see their own actions
        if (!"admin".equals(payload.role) && playerName == null) {
            DatabaseManager.WebUser user = db.getWebUserByUsername(payload.username);
            if (user != null) {
                playerName = user.minecraftName;
            }
        }

        String finalPlayerName = playerName;
        ctx.future(() -> db.searchBlockLogs(finalPlayerName, world, fromTime, toTime, page, limit)
                .thenAccept(logs -> sendJson(ctx, logs))
                .exceptionally(e -> {
                    sendJson(ctx, 500, new ErrorResponse("Fehler beim Abrufen der Logs."));
                    return null;
                }));
    }

    private void handleGetContainerLogs(Context ctx) {
        JwtUtil.TokenPayload payload = authenticateRequest(ctx);
        if (payload == null)
            return;

        String playerName = ctx.queryParam("player");
        String world = ctx.queryParam("world");
        String fromTimeStr = ctx.queryParam("from");
        String toTimeStr = ctx.queryParam("to");
        int page = ctx.queryParamAsClass("page", Integer.class).getOrDefault(1);
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(50);

        Long fromTime = fromTimeStr != null ? Long.parseLong(fromTimeStr) : null;
        Long toTime = toTimeStr != null ? Long.parseLong(toTimeStr) : null;

        // Non-admins can only see their own actions
        if (!"admin".equals(payload.role) && playerName == null) {
            DatabaseManager.WebUser user = db.getWebUserByUsername(payload.username);
            if (user != null) {
                playerName = user.minecraftName;
            }
        }

        String finalPlayerName = playerName;
        ctx.future(() -> db.searchContainerLogs(finalPlayerName, world, fromTime, toTime, page, limit)
                .thenAccept(logs -> sendJson(ctx, logs))
                .exceptionally(e -> {
                    sendJson(ctx, 500, new ErrorResponse("Fehler beim Abrufen der Logs."));
                    return null;
                }));
    }

    // Admin handlers
    private void handleGetStats(Context ctx) {
        JwtUtil.TokenPayload payload = authenticateRequest(ctx);
        if (payload == null)
            return;

        if (!"admin".equals(payload.role)) {
            sendJson(ctx, 403, new ErrorResponse("Nur Administratoren haben Zugriff auf diese Funktion."));
            return;
        }

        sendJson(ctx, db.getServerStats());
    }

    private void handleGetHeatmap(Context ctx) {
        JwtUtil.TokenPayload payload = authenticateRequest(ctx);
        if (payload == null)
            return;

        if (!"admin".equals(payload.role)) {
            sendJson(ctx, 403, new ErrorResponse("Nur Administratoren haben Zugriff auf diese Funktion."));
            return;
        }

        long since = System.currentTimeMillis() / 1000L - (24 * 60 * 60); // Default 24h
        String sinceStr = ctx.queryParam("since");
        if (sinceStr != null) {
            since = Long.parseLong(sinceStr);
        }

        long finalSince = since;
        ctx.future(() -> db.getHeatmapData(finalSince)
                .thenAccept(data -> sendJson(ctx, data))
                .exceptionally(e -> {
                    sendJson(ctx, 500, new ErrorResponse("Fehler beim Abrufen der Heatmap."));
                    return null;
                }));
    }

    private void handleGetSuspicious(Context ctx) {
        JwtUtil.TokenPayload payload = authenticateRequest(ctx);
        if (payload == null)
            return;

        if (!"admin".equals(payload.role)) {
            sendJson(ctx, 403, new ErrorResponse("Nur Administratoren haben Zugriff auf diese Funktion."));
            return;
        }

        long since = System.currentTimeMillis() / 1000L - (24 * 60 * 60); // Default 24h
        String sinceStr = ctx.queryParam("since");
        if (sinceStr != null) {
            since = Long.parseLong(sinceStr);
        }

        long finalSince = since;
        ctx.future(() -> db.getSuspiciousPlayers(finalSince)
                .thenAccept(data -> sendJson(ctx, data))
                .exceptionally(e -> {
                    sendJson(ctx, 500, new ErrorResponse("Fehler beim Abrufen der verdächtigen Spieler."));
                    return null;
                }));
    }

    private void handleGetTimeline(Context ctx) {
        JwtUtil.TokenPayload payload = authenticateRequest(ctx);
        if (payload == null)
            return;

        // Everyone can see their own timeline, or global if admin?
        // Let's allow everyone to see global for now as per "Stats" usually being
        // public or user specific.
        // The plan said "Visual graph of activity". I'll implement global for now or
        // based on filters?
        // Simple global timeline for now.

        // Default to last 24 hours
        String fromTimeStr = ctx.queryParam("from");
        String toTimeStr = ctx.queryParam("to");

        long to = toTimeStr != null ? Long.parseLong(toTimeStr) : System.currentTimeMillis() / 1000L;
        long from = fromTimeStr != null ? Long.parseLong(fromTimeStr) : to - (24 * 60 * 60);

        ctx.future(() -> db.getTimelineStats(from, to)
                .thenAccept(stats -> sendJson(ctx, stats))
                .exceptionally(e -> {
                    sendJson(ctx, 500, new ErrorResponse("Fehler beim Abrufen der Timeline."));
                    return null;
                }));
    }

    private void handleExportBlocks(Context ctx) {
        JwtUtil.TokenPayload payload = authenticateRequest(ctx);
        if (payload == null)
            return;

        String playerName = ctx.queryParam("player");
        String world = ctx.queryParam("world");
        String fromTimeStr = ctx.queryParam("from");
        String toTimeStr = ctx.queryParam("to");
        Long fromTime = fromTimeStr != null && !fromTimeStr.isEmpty() ? Long.parseLong(fromTimeStr) : null;
        Long toTime = toTimeStr != null && !toTimeStr.isEmpty() ? Long.parseLong(toTimeStr) : null;

        if (!"admin".equals(payload.role) && playerName == null) {
            DatabaseManager.WebUser user = db.getWebUserByUsername(payload.username);
            if (user != null)
                playerName = user.minecraftName;
        }

        String finalPlayerName = playerName;
        ctx.future(() -> db.searchBlockLogs(finalPlayerName, world, fromTime, toTime, 1, 100000) // Limit 100k for
                                                                                                 // safety
                .thenAccept(logs -> {
                    StringBuilder csv = new StringBuilder("Timestamp,Player,Action,Block,OldBlock,World,X,Y,Z\n");
                    for (DatabaseManager.BlockLogEntry log : logs) {
                        csv.append(log.timestamp).append(",")
                                .append(log.playerName).append(",")
                                .append(log.action == 0 ? "BREAK" : "PLACE").append(",")
                                .append(log.blockType).append(",")
                                .append(log.oldBlockType).append(",")
                                .append(log.world).append(",")
                                .append(log.x).append(",")
                                .append(log.y).append(",")
                                .append(log.z).append("\n");
                    }
                    ctx.header("Content-Disposition", "attachment; filename=\"block_logs.csv\"");
                    ctx.contentType("text/csv");
                    ctx.result(csv.toString());
                }));
    }

    private void handleExportContainers(Context ctx) {
        JwtUtil.TokenPayload payload = authenticateRequest(ctx);
        if (payload == null)
            return;

        String playerName = ctx.queryParam("player");
        String world = ctx.queryParam("world");
        String fromTimeStr = ctx.queryParam("from");
        String toTimeStr = ctx.queryParam("to");
        Long fromTime = fromTimeStr != null && !fromTimeStr.isEmpty() ? Long.parseLong(fromTimeStr) : null;
        Long toTime = toTimeStr != null && !toTimeStr.isEmpty() ? Long.parseLong(toTimeStr) : null;

        if (!"admin".equals(payload.role) && playerName == null) {
            DatabaseManager.WebUser user = db.getWebUserByUsername(payload.username);
            if (user != null)
                playerName = user.minecraftName;
        }

        String finalPlayerName = playerName;
        ctx.future(() -> db.searchContainerLogs(finalPlayerName, world, fromTime, toTime, 1, 100000)
                .thenAccept(logs -> {
                    StringBuilder csv = new StringBuilder("Timestamp,Player,Action,Item,Amount,World,X,Y,Z\n");
                    for (DatabaseManager.ContainerLogEntry log : logs) {
                        csv.append(log.timestamp).append(",")
                                .append(log.playerName).append(",")
                                .append(log.action == 0 ? "REMOVE" : "ADD").append(",")
                                .append(log.itemMaterial).append(",")
                                .append(log.itemAmount).append(",")
                                .append(log.world).append(",")
                                .append(log.x).append(",")
                                .append(log.y).append(",")
                                .append(log.z).append("\n");
                    }
                    ctx.header("Content-Disposition", "attachment; filename=\"container_logs.csv\"");
                    ctx.contentType("text/csv");
                    ctx.result(csv.toString());
                }));
    }

    private void handleGetUsers(Context ctx) {
        JwtUtil.TokenPayload payload = authenticateRequest(ctx);
        if (payload == null)
            return;

        if (!"admin".equals(payload.role)) {
            sendJson(ctx, 403, new ErrorResponse("Nur Administratoren haben Zugriff auf diese Funktion."));
            return;
        }

        sendJson(ctx, db.getAllWebUsers().stream()
                .map(u -> new UserInfo(u.id, u.username, u.role, u.minecraftName, u.createdAt))
                .toList());
    }

    private void handleDeleteUser(Context ctx) {
        JwtUtil.TokenPayload payload = authenticateRequest(ctx);
        if (payload == null)
            return;

        if (!"admin".equals(payload.role)) {
            sendJson(ctx, 403, new ErrorResponse("Nur Administratoren haben Zugriff auf diese Funktion."));
            return;
        }

        int userId = Integer.parseInt(ctx.pathParam("id"));

        if (userId == payload.userId) {
            sendJson(ctx, 400, new ErrorResponse("Du kannst dich nicht selbst löschen."));
            return;
        }

        if (db.deleteWebUser(userId)) {
            sendJson(ctx, new SuccessResponse("Benutzer erfolgreich gelöscht."));
        } else {
            sendJson(ctx, 404, new ErrorResponse("Benutzer nicht gefunden."));
        }
    }

    private void handleGetWorlds(Context ctx) {
        JwtUtil.TokenPayload payload = authenticateRequest(ctx);
        if (payload == null)
            return;

        sendJson(ctx, db.getDistinctWorlds());
    }

    // Auth helper
    private JwtUtil.TokenPayload authenticateRequest(Context ctx) {
        String authHeader = ctx.header("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendJson(ctx, 401, new ErrorResponse("Nicht authentifiziert."));
            return null;
        }

        String token = authHeader.substring(7);
        JwtUtil.TokenPayload payload = jwtUtil.extractPayload(token);
        if (payload == null) {
            sendJson(ctx, 401, new ErrorResponse("Ungültiges oder abgelaufenes Token."));
            return null;
        }

        return payload;
    }

    // Setup handlers
    private void handleGetSetupStatus(Context ctx) {
        sendJson(ctx, new SetupStatusResponse(plugin.isSetupCompleted()));
    }

    private void handleGetSetupConfig(Context ctx) {
        // Only allow access if setup is not completed
        if (plugin.isSetupCompleted()) {
            sendJson(ctx, 403, new ErrorResponse("Setup wurde bereits abgeschlossen."));
            return;
        }

        // Return current config values
        SetupConfigResponse config = new SetupConfigResponse();
        config.webPort = plugin.getConfig().getInt("web.port", 6746);
        config.webHost = plugin.getConfig().getString("web.host", "0.0.0.0");
        config.jwtSecret = plugin.getConfig().getString("web.jwt-secret", "");
        config.timezone = plugin.getConfig().getString("timezone", "Europe/Berlin");
        config.mapEnabled = plugin.getConfig().getBoolean("map.enabled", true);

        // Auto-fill BlueMap URL with detected server IP + default BlueMap port
        String configuredBluemapUrl = plugin.getConfig().getString("map.bluemap-url", "");
        if (configuredBluemapUrl == null || configuredBluemapUrl.isEmpty()) {
            String serverIp = plugin.getServerIp();
            config.bluemapUrl = "http://" + serverIp + ":8100";
        } else {
            config.bluemapUrl = configuredBluemapUrl;
        }

        config.defaultWorld = plugin.getConfig().getString("map.default-world", "world");
        config.markerUpdateInterval = plugin.getConfig().getInt("map.marker-update-interval", 5);

        // Get available worlds from server
        config.availableWorlds = plugin.getServer().getWorlds().stream()
                .map(w -> w.getName())
                .toList();

        sendJson(ctx, config);
    }

    private void handleSaveSetup(Context ctx) {
        // Only allow if setup is not completed
        if (plugin.isSetupCompleted()) {
            sendJson(ctx, 403, new ErrorResponse("Setup wurde bereits abgeschlossen."));
            return;
        }

        SetupSaveRequest req = gson.fromJson(ctx.body(), SetupSaveRequest.class);

        // Validate required fields
        if (req.jwtSecret == null || req.jwtSecret.length() < 16) {
            sendJson(ctx, 400, new ErrorResponse("JWT Secret muss mindestens 16 Zeichen lang sein."));
            return;
        }

        // Save config values
        plugin.getConfig().set("web.port", req.webPort);
        plugin.getConfig().set("web.host", req.webHost);
        plugin.getConfig().set("web.jwt-secret", req.jwtSecret);
        plugin.getConfig().set("timezone", req.timezone);
        plugin.getConfig().set("map.enabled", req.mapEnabled);
        plugin.getConfig().set("map.bluemap-url", req.bluemapUrl);
        plugin.getConfig().set("map.default-world", req.defaultWorld);
        plugin.getConfig().set("map.marker-update-interval", req.markerUpdateInterval);

        // Mark setup as completed
        plugin.completeSetup();

        sendJson(ctx, new SuccessResponse(
                "Setup erfolgreich abgeschlossen! Bitte starte den Server neu für Port-Änderungen."));
    }

    private void handleGetTimezones(Context ctx) {
        // Return list of common timezones
        java.util.List<String> timezones = java.util.Arrays.asList(
                "Europe/Berlin",
                "Europe/London",
                "Europe/Paris",
                "Europe/Vienna",
                "Europe/Zurich",
                "Europe/Amsterdam",
                "Europe/Brussels",
                "Europe/Copenhagen",
                "Europe/Dublin",
                "Europe/Helsinki",
                "Europe/Lisbon",
                "Europe/Madrid",
                "Europe/Oslo",
                "Europe/Prague",
                "Europe/Rome",
                "Europe/Stockholm",
                "Europe/Warsaw",
                "America/New_York",
                "America/Chicago",
                "America/Denver",
                "America/Los_Angeles",
                "America/Toronto",
                "America/Vancouver",
                "America/Sao_Paulo",
                "Asia/Tokyo",
                "Asia/Shanghai",
                "Asia/Singapore",
                "Asia/Hong_Kong",
                "Asia/Seoul",
                "Asia/Dubai",
                "Australia/Sydney",
                "Australia/Melbourne",
                "Pacific/Auckland",
                "UTC");
        sendJson(ctx, timezones);
    }

    private void handleTestBlueMap(Context ctx) {
        String url = ctx.queryParam("url");
        if (url == null || url.isEmpty()) {
            sendJson(ctx, 400, new ErrorResponse("URL ist erforderlich."));
            return;
        }

        // Try to connect to BlueMap URL
        try {
            // Ensure URL has protocol
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "http://" + url;
            }

            java.net.URL bluemapUrl = new java.net.URL(url);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) bluemapUrl.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int responseCode = conn.getResponseCode();
            conn.disconnect();

            if (responseCode == 200) {
                sendJson(ctx, new SuccessResponse("Verbindung zu BlueMap erfolgreich!"));
            } else {
                sendJson(ctx, 400, new ErrorResponse("BlueMap antwortet mit Status " + responseCode));
            }
        } catch (java.net.MalformedURLException e) {
            sendJson(ctx, 400, new ErrorResponse("Ungültige URL: " + e.getMessage()));
        } catch (java.net.SocketTimeoutException e) {
            sendJson(ctx, 400, new ErrorResponse("Zeitüberschreitung - BlueMap nicht erreichbar."));
        } catch (java.io.IOException e) {
            sendJson(ctx, 400, new ErrorResponse("Verbindungsfehler: " + e.getMessage()));
        }
    }

    // Request/Response classes
    private static class RegisterRequest {
        String code;
        String username;
        String password;
    }

    private static class LoginRequest {
        String username;
        String password;
    }

    public static class AuthResponse {
        public String token;
        public String username;
        public String role;
        public String minecraftName;

        AuthResponse(String token, String username, String role, String minecraftName) {
            this.token = token;
            this.username = username;
            this.role = role;
            this.minecraftName = minecraftName;
        }
    }

    public static class UserInfo {
        public int id;
        public String username;
        public String role;
        public String minecraftName;
        public long createdAt;

        UserInfo(int id, String username, String role, String minecraftName, long createdAt) {
            this.id = id;
            this.username = username;
            this.role = role;
            this.minecraftName = minecraftName;
            this.createdAt = createdAt;
        }
    }

    static class ConfigResponse {
        public boolean blueMapEnabled;
        public String blueMapUrl;
        public String defaultWorld;

        public ConfigResponse(boolean blueMapEnabled, String blueMapUrl, String defaultWorld) {
            this.blueMapEnabled = blueMapEnabled;
            this.blueMapUrl = blueMapUrl;
            this.defaultWorld = defaultWorld;
        }
    }

    public static class ErrorResponse {
        public String error;

        ErrorResponse(String error) {
            this.error = error;
        }
    }

    public static class SuccessResponse {
        public String message;

        SuccessResponse(String message) {
            this.message = message;
        }
    }

    // Setup classes
    private static class SetupStatusResponse {
        public boolean completed;

        SetupStatusResponse(boolean completed) {
            this.completed = completed;
        }
    }

    private static class SetupConfigResponse {
        public int webPort;
        public String webHost;
        public String jwtSecret;
        public String timezone;
        public boolean mapEnabled;
        public String bluemapUrl;
        public String defaultWorld;
        public int markerUpdateInterval;
        public java.util.List<String> availableWorlds;
    }

    private static class SetupSaveRequest {
        int webPort;
        String webHost;
        String jwtSecret;
        String timezone;
        boolean mapEnabled;
        String bluemapUrl;
        String defaultWorld;
        int markerUpdateInterval;
    }

    // Admin Settings
    private void handleGetAdminSettings(Context ctx) {
        AdminSettingsResponse response = new AdminSettingsResponse();
        response.timezone = plugin.getConfig().getString("timezone", "Europe/Berlin");
        response.mapEnabled = plugin.getConfig().getBoolean("map.enabled", false);
        response.bluemapUrl = plugin.getConfig().getString("map.bluemap-url", "");
        response.defaultWorld = plugin.getConfig().getString("map.default-world", "world");
        response.markerUpdateInterval = plugin.getConfig().getInt("map.marker-update-interval", 5);
        response.webPort = plugin.getConfig().getInt("web.port", 6746);
        response.webHost = plugin.getConfig().getString("web.host", "0.0.0.0");
        response.serverIp = plugin.getConfig().getString("web.server-ip", "");
        sendJson(ctx, response);
    }

    private void handleSaveAdminSettings(Context ctx) {
        try {
            AdminSettingsRequest req = gson.fromJson(ctx.body(), AdminSettingsRequest.class);

            // Update config values
            plugin.getConfig().set("timezone", req.timezone);
            plugin.getConfig().set("map.enabled", req.mapEnabled);
            plugin.getConfig().set("map.bluemap-url", req.bluemapUrl);
            plugin.getConfig().set("map.default-world", req.defaultWorld);
            plugin.getConfig().set("map.marker-update-interval", req.markerUpdateInterval);

            // Server IP override (optional)
            if (req.serverIp != null) {
                plugin.getConfig().set("web.server-ip", req.serverIp);
            }

            // Save config
            plugin.saveConfig();

            sendJson(ctx, new SuccessResponse(
                    "Einstellungen gespeichert. Manche Änderungen erfordern einen Server-Neustart."));
        } catch (Exception e) {
            sendJson(ctx, 400, new ErrorResponse("Fehler beim Speichern: " + e.getMessage()));
        }
    }

    private static class AdminSettingsResponse {
        public String timezone;
        public boolean mapEnabled;
        public String bluemapUrl;
        public String defaultWorld;
        public int markerUpdateInterval;
        public int webPort;
        public String webHost;
        public String serverIp;
    }

    private static class AdminSettingsRequest {
        String timezone;
        boolean mapEnabled;
        String bluemapUrl;
        String defaultWorld;
        int markerUpdateInterval;
        String serverIp;
    }

    // BlueMap Proxy Handler
    private void handleBlueMapProxy(Context ctx, String path) {
        try {
            // Get BlueMap internal URL (always localhost)
            String bluemapPort = "8100";
            String targetUrl = "http://127.0.0.1:" + bluemapPort + "/" + path;

            // Add query string if present
            String queryString = ctx.queryString();
            if (queryString != null && !queryString.isEmpty()) {
                targetUrl += "?" + queryString;
            }

            // Create connection
            java.net.URL url = new java.net.URL(targetUrl);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod(ctx.method().name());
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            conn.setInstanceFollowRedirects(false); // Don't follow redirects automatically

            // Forward request headers
            ctx.headerMap().forEach((key, value) -> {
                if (!key.equalsIgnoreCase("Host") && !key.equalsIgnoreCase("Connection")) {
                    conn.setRequestProperty(key, value);
                }
            });

            // Get response
            int status = conn.getResponseCode();
            ctx.status(status);

            // Handle redirects - rewrite Location header to use proxy path
            String location = conn.getHeaderField("Location");
            if (location != null) {
                // Rewrite redirect URLs to go through proxy
                if (location.startsWith("http://127.0.0.1:" + bluemapPort)) {
                    location = "/bluemap" + location.substring(("http://127.0.0.1:" + bluemapPort).length());
                } else if (location.startsWith("/")) {
                    location = "/bluemap" + location;
                }
                ctx.header("Location", location);
            }

            // Forward response headers
            String contentType = conn.getContentType();
            if (contentType != null) {
                ctx.contentType(contentType);
            }

            // Read and forward response body
            java.io.InputStream inputStream;
            try {
                inputStream = conn.getInputStream();
            } catch (java.io.IOException e) {
                inputStream = conn.getErrorStream();
            }

            if (inputStream != null) {
                byte[] data = inputStream.readAllBytes();
                inputStream.close();
                ctx.result(data);
            }

            conn.disconnect();
        } catch (Exception e) {
            ctx.status(502);
            ctx.result("BlueMap nicht erreichbar. Stelle sicher, dass BlueMap auf localhost:8100 läuft.");
        }
    }

    // Analytics Handlers

    private void handleGetPeakHours(Context ctx) {
        try {
            // Get activity grouped by hour of day
            java.util.Map<Integer, Integer> peakHours = db.getPeakHoursData();
            sendJson(ctx, peakHours);
        } catch (Exception e) {
            sendJson(ctx, 500, new ErrorResponse("Failed to get peak hours: " + e.getMessage()));
        }
    }

    private void handleGetTopPlayers(Context ctx) {
        int limit = 10;
        try {
            String limitStr = ctx.queryParam("limit");
            if (limitStr != null)
                limit = Integer.parseInt(limitStr);
        } catch (NumberFormatException ignored) {
        }

        try {
            java.util.List<DatabaseManager.PlayerActivity> topPlayers = db.getTopPlayersData(limit);
            sendJson(ctx, topPlayers);
        } catch (Exception e) {
            sendJson(ctx, 500, new ErrorResponse("Failed to get top players: " + e.getMessage()));
        }
    }

    private void handleGetBlockTypes(Context ctx) {
        String action = ctx.queryParam("action"); // break, place, or null for both
        try {
            java.util.Map<String, Integer> blockTypes = db.getBlockTypesData(action);
            sendJson(ctx, blockTypes);
        } catch (Exception e) {
            sendJson(ctx, 500, new ErrorResponse("Failed to get block types: " + e.getMessage()));
        }
    }

    private void handleGetCustomStats(Context ctx) {
        // Chart Builder - flexible query builder for custom charts
        String xAxis = ctx.queryParam("xAxis"); // time, player, world, block
        String yAxis = ctx.queryParam("yAxis"); // count, blocks_broken, blocks_placed, container_items
        String period = ctx.queryParam("period"); // 24h, 7d, 30d
        String filter = ctx.queryParam("filter"); // player:name, world:name, action:break/place

        if (xAxis == null)
            xAxis = "time";
        if (yAxis == null)
            yAxis = "count";
        if (period == null)
            period = "24h";

        try {
            java.util.List<java.util.Map<String, Object>> data = db.getCustomChartData(xAxis, yAxis, period, filter);
            sendJson(ctx, data);
        } catch (Exception e) {
            sendJson(ctx, 500, new ErrorResponse("Failed to get custom stats: " + e.getMessage()));
        }
    }
}
