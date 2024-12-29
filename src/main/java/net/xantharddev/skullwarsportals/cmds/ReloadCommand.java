package net.xantharddev.skullwarsportals.cmds;

import net.xantharddev.skullwarsportals.Utils.ChatUtils;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class ReloadCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final ChatUtils chatUtils = new ChatUtils();
    public ReloadCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("skportals.reload")) {
                sender.sendMessage(chatUtils.colour(plugin.getConfig().getString("messages.no-permission")));
                return true;
            }

            try {
                plugin.reloadConfig();
                sender.sendMessage(chatUtils.colour(plugin.getConfig().getString("messages.reload")));
            } catch (Exception e) {
                sender.sendMessage(ChatColor.RED + "messages.reload-fail");
                e.printStackTrace();
            }
            return true;
        }

        sender.sendMessage(chatUtils.colour(plugin.getConfig().getString("messages.reload-usage")));
        return true;
    }
}
