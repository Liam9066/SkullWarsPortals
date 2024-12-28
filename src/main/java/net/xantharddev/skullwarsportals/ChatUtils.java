package net.xantharddev.skullwarsportals;

import org.bukkit.ChatColor;

import java.util.List;
import java.util.stream.Collectors;

public class ChatUtils {
    public String colour(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public List<String> colourList(List<String> stringList) {
        return stringList.stream()
                .map(this::colour)
                .collect(Collectors.toList());
    }
}
