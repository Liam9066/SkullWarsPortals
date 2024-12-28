package net.xantharddev.skullwarsportals;

import de.tr7zw.nbtapi.NBT;
import net.minecraft.server.v1_8_R3.NBTTagCompound;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class PortalCommand implements CommandExecutor {
    private final SkullWarsPortals plugin;

    public PortalCommand(SkullWarsPortals plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 2 || !args[0].equalsIgnoreCase("giveremover")) {
            return false;
        }

        if (!sender.hasPermission("skullwarsportals.giveremover")) {
            sender.sendMessage(plugin.colour(plugin.getConfig().getString("messages.no-permission")));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.colour(plugin.getConfig().getString("messages.player-not-found")));
            return true;
        }

        givePortalRemover(target);
        target.sendMessage(plugin.colour(plugin.getConfig().getString("messages.remover-received")));
        return true;
    }

    private void givePortalRemover(Player player) {
        // Retrieve item type, durability, and name from config
        String itemTypeString = plugin.getConfig().getString("items.portal-remover.type");
        int itemDamage = plugin.getConfig().getInt("items.portal-remover.damage");
        String itemName = plugin.getConfig().getString("items.portal-remover.name");
        List<String> loreList = plugin.getConfig().getStringList("items.portal-remover.lore");

        // Log for debugging
        Bukkit.getLogger().info("Portal Remover Item Type: " + itemTypeString);
        Bukkit.getLogger().info("Portal Remover Item Damage: " + itemDamage);
        Bukkit.getLogger().info("Portal Remover Name: " + itemName);
        Bukkit.getLogger().info("Portal Remover Lore: " + loreList);

        // Ensure valid item type
        Material itemType = Material.getMaterial(itemTypeString);
        if (itemType == null) {
            player.sendMessage(plugin.colour(plugin.getConfig().getString("messages.invalid-item-type")));
            return;
        }

        // Create the ItemStack for the portal remover
        ItemStack itemStack = new ItemStack(itemType);
        itemStack.setDurability((short) itemDamage);

        // Set the name of the item if provided in the config
        ItemMeta meta = itemStack.getItemMeta();
        meta.setDisplayName(plugin.colour(itemName));
        meta.setLore(plugin.colourList(loreList));
        itemStack.setItemMeta(meta);
        NBT.modify(itemStack, nbt -> {nbt.setBoolean("isPortalBreaker", true);});
        player.getInventory().addItem(itemStack);
    }
}
