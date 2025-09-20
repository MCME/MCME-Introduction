package com.mcmiddleearth.introduction;

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

    private final PluginConfig config = new PluginConfig();
    private Room first, second;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config.loadConfig(this.getConfig());
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
        config.getRoom("firstRoom");
        config.getRoom("secondRoom");
    }

    public static IntroductionPlugin getInstance(){return instance;}

    public Room getFirstRoom() {
        return first;
    }

    public Room getSecondRoom() {
        return second;
    }

    public Room getRoom(Player player) {

    }
}
