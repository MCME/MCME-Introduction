package com.mcmiddleearth.introduction;

import com.mcmiddleearth.introduction.rooms.Room;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class IgnoreCommandHandler implements TabExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        IntroductionPlugin plugin = IntroductionPlugin.getInstance();
        Room secondRoom = plugin.getSecond();
        if(sender instanceof Player player) {
            Room playerRoom = plugin.getRoom(player);
            if(secondRoom.equals(playerRoom)) {
                secondRoom.teleport(player);
            } else {
                player.sendMessage(Component.text("There is no issue you can ignore.").color(NamedTextColor.RED));
            }
        } else {
            sender.sendMessage("Player only command!");
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return List.of();
    }
}
