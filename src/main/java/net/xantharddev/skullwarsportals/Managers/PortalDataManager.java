package net.xantharddev.skullwarsportals.Managers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.xantharddev.skullwarsportals.SkullWarsPortals;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class PortalDataManager {
    private final File dataFile;
    private final Gson gson;

    private static PortalDataManager instance;

    public PortalDataManager(SkullWarsPortals plugin) {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.dataFile = new File(plugin.getDataFolder(), "portal_locations.json");

        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        instance = this;
    }

    public static PortalDataManager getInstance() {
        return instance;
    }

    /**
     * Save portal locations to a JSON file asynchronously.
     */
    public CompletableFuture<Void> savePortalLocations(Map<String, Set<Location>> portalLocations) {
        return CompletableFuture.runAsync(() -> {
            Map<String, List<Map<String, Object>>> portalData = new HashMap<>();

            for (Map.Entry<String, Set<Location>> entry : portalLocations.entrySet()) {
                List<Map<String, Object>> locationsData = new ArrayList<>();
                for (Location loc : entry.getValue()) {
                    Map<String, Object> locationData = new HashMap<>();
                    locationData.put("x", loc.getX());
                    locationData.put("y", loc.getY());
                    locationData.put("z", loc.getZ());
                    locationData.put("world", loc.getWorld().getName());
                    locationsData.add(locationData);
                }
                portalData.put(entry.getKey(), locationsData);
            }

            try (Writer writer = new FileWriter(dataFile)) {
                gson.toJson(portalData, writer);
            } catch (IOException e) {
                Bukkit.getLogger().warning("Failed to save portal locations: " + e.getMessage());
            }
        });
    }

    /**
     * Load portal locations from the JSON file asynchronously.
     */
    public CompletableFuture<Map<String, Set<Location>>> loadPortalLocations() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Set<Location>> portalLocations = new HashMap<>();

            if (!dataFile.exists()) return portalLocations;

            try (Reader reader = new FileReader(dataFile)) {
                Type type = new TypeToken<Map<String, List<Map<String, Object>>>>() {}.getType();
                Map<String, List<Map<String, Object>>> portalData = gson.fromJson(reader, type);

                if (portalData != null) {
                    for (Map.Entry<String, List<Map<String, Object>>> entry : portalData.entrySet()) {
                        Set<Location> locations = new HashSet<>();
                        for (Map<String, Object> locationData : entry.getValue()) {
                            String worldName = (String) locationData.get("world");
                            World world = Bukkit.getWorld(worldName);
                            if (world == null) {
                                Bukkit.getLogger().warning("World not found: " + worldName + ". Skipping this portal.");
                                continue;
                            }
                            double x = ((Number) locationData.get("x")).doubleValue();
                            double y = ((Number) locationData.get("y")).doubleValue();
                            double z = ((Number) locationData.get("z")).doubleValue();
                            Location location = new Location(world, x, y, z);
                            locations.add(location);
                        }
                        portalLocations.put(entry.getKey(), locations);
                    }
                }
            } catch (IOException e) {
                Bukkit.getLogger().warning("Failed to load portal locations: " + e.getMessage());
            }

            return portalLocations;
        });
    }
}
