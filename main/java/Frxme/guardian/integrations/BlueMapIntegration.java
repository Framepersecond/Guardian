package Frxme.guardian.integrations;

import Frxme.guardian.Guardian;
import Frxme.guardian.DatabaseManager;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.markers.ShapeMarker;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Shape;
import com.flowpowered.math.vector.Vector2d;

import java.util.List;
import java.util.logging.Level;

/**
 * Integration with BlueMap to display activity heatmap markers directly on the
 * map.
 * This is a soft-dependency - Guardian works without BlueMap installed.
 */
public class BlueMapIntegration {

    private final Guardian plugin;
    private final DatabaseManager db;
    private static final String MARKER_SET_ID = "guardian-activity";
    private static final String MARKER_SET_LABEL = "Guardian Activity";

    private MarkerSet markerSet;
    private boolean enabled = false;

    public BlueMapIntegration(Guardian plugin) {
        this.plugin = plugin;
        this.db = plugin.getDatabaseManager();
    }

    /**
     * Initialize the BlueMap integration. Call this from onEnable.
     */
    public void initialize() {
        BlueMapAPI.onEnable(api -> {
            plugin.getLogger().info("BlueMap detected! Initializing Guardian markers...");
            enabled = true;

            // Create marker set
            markerSet = MarkerSet.builder()
                    .label(MARKER_SET_LABEL)
                    .toggleable(true)
                    .defaultHidden(false)
                    .build();

            // Add marker set to all maps
            for (BlueMapWorld world : api.getWorlds()) {
                for (BlueMapMap map : world.getMaps()) {
                    map.getMarkerSets().put(MARKER_SET_ID, markerSet);
                }
            }

            // Initial marker update
            updateMarkers();

            // Schedule periodic updates every 5 minutes (6000 ticks)
            // Run on main thread to ensure SQLite connection is accessed safely
            plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                if (enabled && markerSet != null) {
                    updateMarkers();
                }
            }, 6000L, 6000L); // 5 minutes = 6000 ticks

            plugin.getLogger().info("Guardian BlueMap markers initialized with 5-minute auto-refresh!");
        });

        BlueMapAPI.onDisable(api -> {
            enabled = false;
            markerSet = null;
            plugin.getLogger().info("BlueMap disabled, Guardian markers removed.");
        });
    }

    /**
     * Update all activity markers on the map.
     * Call this periodically or when data changes.
     */
    public void updateMarkers() {
        if (!enabled) {
            plugin.getLogger().warning("BlueMap markers update skipped: not enabled");
            return;
        }
        if (markerSet == null) {
            plugin.getLogger().warning("BlueMap markers update skipped: markerSet is null");
            return;
        }

        try {
            // Clear existing markers
            markerSet.getMarkers().clear();
            plugin.getLogger().info("BlueMap: Cleared existing markers, fetching new data...");

            // Get activity data from database (synchronous call)
            List<DatabaseManager.ChunkActivity> activities = db.getChunkActivityData();

            plugin.getLogger()
                    .info("BlueMap: Retrieved " + activities.size() + " chunk activity records from database");

            if (activities.isEmpty()) {
                plugin.getLogger().info("No activity data for BlueMap markers in last 24 hours.");
                return;
            }

            // Find max count for intensity calculation
            int maxCount = activities.stream()
                    .mapToInt(a -> a.count)
                    .max()
                    .orElse(1);

            plugin.getLogger().info("BlueMap: Max activity count is " + maxCount);

            // Create markers for each chunk
            int markersCreated = 0;
            for (DatabaseManager.ChunkActivity activity : activities) {
                String markerId = "chunk_" + activity.world + "_" + activity.chunkX + "_" + activity.chunkZ;

                // Calculate chunk boundaries (16x16 blocks)
                double minX = activity.chunkX * 16;
                double maxX = minX + 16;
                double minZ = activity.chunkZ * 16;
                double maxZ = minZ + 16;

                // Create a rectangle shape for the chunk
                Shape chunkShape = Shape.builder()
                        .addPoint(new Vector2d(minX, minZ))
                        .addPoint(new Vector2d(maxX, minZ))
                        .addPoint(new Vector2d(maxX, maxZ))
                        .addPoint(new Vector2d(minX, maxZ))
                        .build();

                // Calculate intensity for color
                double intensity = (double) activity.count / maxCount;
                Color fillColor = getFillColor(intensity);
                Color lineColor = getLineColor(intensity);

                // Create chunk-sized shape marker
                ShapeMarker marker = ShapeMarker.builder()
                        .label(activity.count + " Aktionen")
                        .detail("<div style='font-size:14px;padding:5px;'><b>" + activity.count + " Aktionen</b><br>" +
                                "<span style='color:#888;'>Chunk (" + activity.chunkX + ", " + activity.chunkZ
                                + ")</span></div>")
                        .shape(chunkShape, 64) // Y-level 64
                        .fillColor(fillColor)
                        .lineColor(lineColor)
                        .lineWidth(1)
                        .depthTestEnabled(false) // Always visible
                        .build();

                markerSet.getMarkers().put(markerId, marker);
                markersCreated++;
            }

            plugin.getLogger().info("BlueMap: Created " + markersCreated + " chunk markers successfully!");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to update BlueMap markers", e);
        }
    }

    /**
     * Get fill color based on activity intensity.
     * Higher intensity = more red, lower = more cyan/green
     * Alpha uses float: 0.0 = transparent, 1.0 = opaque
     */
    private Color getFillColor(double intensity) {
        if (intensity > 0.7) {
            // High activity: Red, transparent
            return new Color(239, 68, 68, 0.25f); // Red, 25% opacity
        } else if (intensity > 0.4) {
            // Medium activity: Yellow/Orange
            return new Color(251, 191, 36, 0.25f); // Yellow, 25% opacity
        } else {
            // Low activity: Cyan/Green
            return new Color(0, 240, 232, 0.25f); // Cyan, 25% opacity
        }
    }

    /**
     * Get border/line color based on activity intensity.
     */
    private Color getLineColor(double intensity) {
        if (intensity > 0.7) {
            return new Color(239, 68, 68, 0.7f); // Red border
        } else if (intensity > 0.4) {
            return new Color(251, 191, 36, 0.7f); // Yellow border
        } else {
            return new Color(0, 240, 232, 0.7f); // Cyan border
        }
    }

    public boolean isEnabled() {
        return enabled;
    }
}
