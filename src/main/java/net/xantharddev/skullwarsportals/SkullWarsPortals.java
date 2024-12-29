package net.xantharddev.skullwarsportals;

import com.google.gson.Gson;
import com.massivecraft.factions.Board;
import com.massivecraft.factions.FLocation;
import com.massivecraft.factions.Faction;
import de.tr7zw.nbtapi.NBT;
import eu.decentsoftware.holograms.api.DHAPI;
import net.xantharddev.skullwarsportals.Managers.PortalDataManager;
import net.xantharddev.skullwarsportals.Utils.ChatUtils;
import net.xantharddev.skullwarsportals.cmds.PortalCommand;
import net.xantharddev.skullwarsportals.cmds.ReloadCommand;
import net.xantharddev.skullwarsportals.cmds.RemovePortalsCmd;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.*;
import java.lang.reflect.Method;
import java.util.*;

public class SkullWarsPortals extends JavaPlugin implements Listener {
    private final Set<UUID> teleportCooldown = new HashSet<>();
    private final Map<String, BukkitTask> detectionTasks = new HashMap<>();
    private final Map<String, Set<Location>> portalLocations = new HashMap<>();
    private final ChatUtils chatUtils = new ChatUtils();
    private File dataFile;
    private Gson gson;
    private PortalDataManager portalDataManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);

        portalDataManager = new PortalDataManager(this);
        portalLocations.putAll(portalDataManager.loadPortalLocations());
        Bukkit.getScheduler().runTaskLater(this, this::recreateHolograms, 40L);

        getCommand("skullwarsportals").setExecutor(new PortalCommand(this));
        getCommand("skullwarsportalsreload").setExecutor(new ReloadCommand(this));
        getCommand("skportalsremoveall").setExecutor(new RemovePortalsCmd(this));


        getLogger().info("SkullWarsPortals successfully enabled!");
    }

    @Override
    public void onDisable() {
        // Save portal locations when the plugin is disabled
        portalDataManager.savePortalLocations(portalLocations);
    }

    /**
     * Used to recreate the holograms on startup
     */
    private void recreateHolograms() {
        for (Map.Entry<String, Set<Location>> entry : portalLocations.entrySet()) {
            Location portalCenter = findPortalCenter(entry.getValue());
            createHologram(portalCenter, entry.getKey());
        }
    }


    @EventHandler
    public void onPlayerInteractR(PlayerInteractEvent event) {
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                if (event.getClickedBlock() == null) return;
                if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
                if (event.getClickedBlock().getType() != Material.ENDER_PORTAL_FRAME && event.getClickedBlock().getType() != Material.ENDER_PORTAL)
                    return;
                if (event.getItem() == null) return;

                if (!NBT.get(event.getItem(), nbt -> (boolean) nbt.getBoolean("isPortalBreaker"))) return;
                Location clickedLoc = event.getClickedBlock().getLocation();

                for (Map.Entry<String, Set<Location>> entry : portalLocations.entrySet()) {
                    Set<Location> allPortalLocations = getAllPortalBlocks(entry.getValue());
                    if (allPortalLocations.contains(clickedLoc)) {
                        portalLocations.remove(entry.getKey());
                        Bukkit.getScheduler().runTask(this, () -> {
                            allPortalLocations.forEach(loc -> loc.getBlock().setType(Material.AIR));
                            DHAPI.removeHologram(entry.getKey());
                        });
                    }
                }
            });
        }

    @EventHandler
    public void onPlayerInteractL(PlayerInteractEvent event) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            if (event.getClickedBlock() == null) return;
            if (event.getAction() != Action.LEFT_CLICK_BLOCK) return;
            if (event.getClickedBlock().getType() != Material.ENDER_PORTAL_FRAME && event.getClickedBlock().getType() != Material.ENDER_PORTAL)
                return;
            if (event.getItem() == null) return;

            if (!NBT.get(event.getItem(), nbt -> (boolean) nbt.getBoolean("isPortalBreaker"))) return;
            Location clickedLoc = event.getClickedBlock().getLocation();

            Bukkit.getScheduler().runTask(this, () -> clickedLoc.getBlock().setType(Material.AIR));
            event.getPlayer().sendMessage("conducting a test");
        });
    }
    /**
     * @param portalBlocks Base blocks from the portal (the 3x3 inside)
     * @return All of the portal blocks - INCLUDING the outside frames
     */
    private Set<Location> getAllPortalBlocks(Set<Location> portalBlocks) {
        Set<Location> allBlocksToRemove = new HashSet<>(portalBlocks);

        for (Location portalBlock : portalBlocks) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue; // Skip the portal block itself
                    Location checkLoc = portalBlock.clone().add(dx, 0, dz);
                    if (checkLoc.getBlock().getType() == Material.ENDER_PORTAL_FRAME) {
                        allBlocksToRemove.add(checkLoc);
                    }
                }
            }
        }

        return allBlocksToRemove;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.ENDER_PORTAL_FRAME && event.getBlock().getType() != Material.ENDER_PORTAL)
            return;
        // Check for staff in creative making sure they cant nuke someones portal accidentally (Safety Feature)
        if (isPortalWithinRadius(event.getBlock().getLocation(), 1)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(chatUtils.colour(getConfig().getString("messages.cannot-break")));
        }

    }

    @EventHandler
    public void onEyeOfEnderPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.ENDER_PORTAL_FRAME) return;

        Location placedLocation = event.getBlockPlaced().getLocation();

        // If we do not check correctly players end up being able creating duplicate of our stored "portals" and breaking the system
        if (isPortalWithinRadius(placedLocation, 6)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(chatUtils.colour(getConfig().getString("messages.cannot-place")));
            return;
        }

        Faction fac = Board.getInstance().getFactionAt(new FLocation(placedLocation));
        if (detectionTasks.containsKey(fac.getId())) {
            detectionTasks.remove(fac.getId()).cancel();
        }

        detectionTasks.put(fac.getId(), Bukkit.getScheduler().runTaskLater(this, () -> checkForEndPortal(event.getBlockPlaced(), fac), 20L));
    }

    /**
     * Algorithm to check for portals inside of a radius
     *
     * @param interactedLocation Location the player interacted with
     * @param radius             Radius to check in
     * @return Whether or not a portal is inside of the radius from the interacted Loc
     */
    private boolean isPortalWithinRadius(Location interactedLocation, int radius) {
        for (Map.Entry<String, Set<Location>> entry : portalLocations.entrySet()) {
            if (entry.getValue().contains(interactedLocation)) return true;
            for (Location location : entry.getValue()) {
                if (isWithinRadius(interactedLocation, location, radius)) return true;
            }
        }
        return false;
    }

    /**
     * Helper method for isPortalWithinRadius()
     */
    private boolean isWithinRadius(Location loc1, Location loc2, int radius) {
        if (!loc1.getWorld().equals(loc2.getWorld())) return false;

        double distanceSquared = loc1.distanceSquared(loc2);
        return distanceSquared <= (radius * radius);
    }


    /**
     * Logic to work out if an end portal has been created
     *
     * @param placedBlock Block placed by player
     * @param fac         Faction at that location
     */
    private void checkForEndPortal(Block placedBlock, Faction fac) {
        Set<Location> portalBlocks = new HashSet<>();
        World world = placedBlock.getWorld();
        int placedX = placedBlock.getX();
        int placedY = placedBlock.getY();
        int placedZ = placedBlock.getZ();

        // Search in a 6x6 area at the same Y level - I do not want anyone fucking around with how the portals are created
        // If radius is too small we have problems with bits of the portal not being recognised
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                Block block = world.getBlockAt(placedX + x, placedY, placedZ + z);
                if (block.getType() == Material.ENDER_PORTAL) {
                    portalBlocks.add(block.getLocation());
                }
            }
        }

        if (portalBlocks.size() == 9) {
            Location center = findPortalCenter(portalBlocks);
            handleEndPortalCreation(portalBlocks, center, fac);
        }
    }

    /**
     * @param portalLocations locations of the portal
     * @return Returns the center of a portal
     */
    private Location findPortalCenter(Set<Location> portalLocations) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (Location location : portalLocations) {
            minX = Math.min(minX, location.getBlockX());
            minY = Math.min(minY, location.getBlockY());
            minZ = Math.min(minZ, location.getBlockZ());
            maxX = Math.max(maxX, location.getBlockX());
            maxY = Math.max(maxY, location.getBlockY());
            maxZ = Math.max(maxZ, location.getBlockZ());
        }

        Iterator<Location> iterator = portalLocations.iterator();
        Location first = iterator.next();
        World world = first.getWorld();
        double centerX = (minX + maxX) / 2.0 + 0.5;
        double centerY = (minY + maxY) / 2.0;
        double centerZ = (minZ + maxZ) / 2.0 + 0.5;

        return new Location(world, centerX, centerY, centerZ);
    }

    private void handleEndPortalCreation(Set<Location> portalBlocks, Location center, Faction fac) {
        String holoName = createHologram(center);
        portalLocations.put(holoName, portalBlocks);

        try {
            Method method = fac.getClass().getMethod("getOnlinePlayers");
            @SuppressWarnings("unchecked")
            List<Player> onlinePlayers = (List<Player>) method.invoke(fac);
            notifyNearbyPlayers(center, onlinePlayers);
        } catch (NoSuchMethodException e) {
            Bukkit.getLogger().warning("getOnlinePlayers() method does not exist for this version of Factions. Skipping player notification.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @EventHandler
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (event.getCause() != PlayerPortalEvent.TeleportCause.END_PORTAL) return;

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (teleportCooldown.contains(playerId)) {
            player.sendMessage(ChatColor.RED + "You are on cooldown! Please wait.");
            return;
        }

        event.setCancelled(true);
        teleportPlayer(player);

        teleportCooldown.add(playerId);

        Bukkit.getScheduler().runTaskLater(this, () -> teleportCooldown.remove(playerId), 60L); // 60 ticks = 3 seconds
    }

    private void teleportPlayer(Player player) {
        String worldName = getConfig().getString("destination.world");
        World world = Bukkit.getWorld(worldName);

        if (world == null) {
            player.sendMessage(chatUtils.colour(getConfig().getString("messages.destination-not-found")));
            return;
        }

        double x = getConfig().getDouble("destination.x");
        double y = getConfig().getDouble("destination.y");
        double z = getConfig().getDouble("destination.z");
        Location destination = new Location(world, x, y, z);

        player.teleport(destination);
        player.sendMessage(chatUtils.colour(getConfig().getString("messages.teleported")));
    }

    private String createHologram(Location portalLoc) {
        return createHologram(portalLoc, "endportal_" + UUID.randomUUID());
    }

    public Map<String, Set<Location>> getPortalLocations() {
        return portalLocations;
    }

    private String createHologram(Location portalLoc, String holoName) {
        List<String> holoText = chatUtils.colourList(getConfig().getStringList("hologram.text"));
        double holoHeight = getConfig().getDouble("hologram.height");
        Location holoLoc = portalLoc.clone().add(0, holoHeight, 0);

        try {
            DHAPI.createHologram(holoName, holoLoc, holoText);
        } catch (Exception e) {
            getLogger().warning("Failed to create hologram: " + e.getMessage());
        }

        return holoName;
    }

    private void notifyNearbyPlayers(Location portalLocation, Collection<Player> players) {
        String createMessage = chatUtils.colour(getConfig().getString("messages.portal-created"));

        for (Player player : players) {
            if (player.getLocation().distance(portalLocation) <= 25) {
                player.sendMessage(createMessage);
            }
        }
    }
    public PortalDataManager getPortalDataManager() {
        return portalDataManager;
    }
}