package net.xantharddev.skullwarsportals.cmds;

import net.xantharddev.skullwarsportals.SkullWarsPortals;
import net.xantharddev.skullwarsportals.Utils.ChatUtils;
import eu.decentsoftware.holograms.api.DHAPI;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;

public class RemovePortalsCmd implements CommandExecutor {

    private final SkullWarsPortals plugin;
    private final ChatUtils chatUtils = new ChatUtils();

    public RemovePortalsCmd(SkullWarsPortals plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("skullwarsportals.deleteallportals")) {
            sender.sendMessage(chatUtils.colour(plugin.getConfig().getString("messages.no-permission")));
            return true;
        }

        int numberOfPortalsRemoved = plugin.getPortalLocations().size();

        if (numberOfPortalsRemoved == 0) {
            sender.sendMessage(chatUtils.colour(plugin.getConfig().getString("messages.no-portals-to-remove")));
            return true;
        }

        // Collect portal keys to remove after iteration
        List<String> portalsToRemove = new ArrayList<>();

        for (Map.Entry<String, Set<Location>> entry : plugin.getPortalLocations().entrySet()) {
            Set<Location> allPortalLocations = getAllPortalBlocks(entry.getValue());

            new BukkitRunnable() {
                @Override
                public void run() {
                    allPortalLocations.forEach(loc -> loc.getBlock().setType(Material.AIR)); // Remove portal blocks
                    DHAPI.removeHologram(entry.getKey()); // Remove hologram
                }
            }.runTask(plugin);

            portalsToRemove.add(entry.getKey());
        }

        portalsToRemove.forEach(key -> plugin.getPortalLocations().remove(key));

        plugin.getPortalDataManager().savePortalLocations(plugin.getPortalLocations());

        String successMessage = plugin.getConfig()
                .getString("messages.portal-delete-success")
                .replace("%NumberOfPortalsRemoved%", String.valueOf(numberOfPortalsRemoved));

        sender.sendMessage(chatUtils.colour(successMessage));

        return true;
    }

    /**
     * This method returns all the blocks of a portal, including the frames.
     * @param portalBlocks Set of portal locations (the 3x3 inside blocks).
     * @return Set of all portal blocks, including frames.
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
}
