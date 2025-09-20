package com.mcmiddleearth.introduction;

import org.bukkit.configuration.file.FileConfiguration;

/**
 *
 * @author Eriol_Eandur
 */
public class PluginConfig {

    private FileConfiguration config;

    public void loadConfig(FileConfiguration config){
        this.config = config;
    }

    public void getRoom(String key) {

    }
}
