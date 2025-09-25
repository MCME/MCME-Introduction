package com.mcmiddleearth.introduction;

import com.mcmiddleearth.introduction.rooms.FirstRoom;
import com.mcmiddleearth.introduction.rooms.Room;
import com.mcmiddleearth.introduction.rooms.SecondRoom;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

/**
 *
 * @author Eriol_Eandur
 */
public final class IntroductionPlugin extends JavaPlugin {

    private static IntroductionPlugin instance;

    private Room first, second;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        instance = this;
        getServer().getPluginManager().registerEvents(new PlayerListener(),this);
        IgnoreCommandHandler ignoreHandler = new IgnoreCommandHandler();
        PluginCommand ignoreCommand = getServer().getPluginCommand("ignore");
        if(ignoreCommand != null) {
            ignoreCommand.setExecutor(ignoreHandler);
            ignoreCommand.setTabCompleter(ignoreHandler);
        } else {
            Logger.getLogger(this.getClass().getSimpleName()).warning("Ignore command not found.");
        }
        first = new FirstRoom(getConfig().getConfigurationSection("firstRoom"));
        second = new SecondRoom(getConfig().getConfigurationSection("secondRoom"));
        first.setNextRoom(second);
    }

    public static IntroductionPlugin getInstance(){return instance;}

    public Room getRoom(Player player) {
        if(first.isInside(player.getLocation())) {
            return first;
        } else if(second.isInside(player.getLocation())) {
            return second;
        }
        return null;
    }

    public Room getSecond() {
        return second;
    }
}
