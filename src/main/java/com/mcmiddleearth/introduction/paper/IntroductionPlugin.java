package com.mcmiddleearth.introduction.paper;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.mcmiddleearth.introduction.paper.command.ConfirmCommand;
import com.mcmiddleearth.introduction.paper.command.IntroCommand;
import com.mcmiddleearth.introduction.paper.rooms.FirstRoom;
import com.mcmiddleearth.introduction.paper.rooms.Room;
import com.mcmiddleearth.introduction.paper.rooms.SecondRoom;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 *
 * @author Eriol_Eandur
 */
public final class IntroductionPlugin extends JavaPlugin {

    private static IntroductionPlugin instance;

    private Room first, second;

    public static final String CHANNEL = "mcme:intro";

    @SuppressWarnings("UnstableApiUsage")
    @Override
    public void onEnable() {
        saveDefaultConfig();
        instance = this;
        getServer().getPluginManager().registerEvents(new PlayerListener(),this);
        getServer().getMessenger()
                .registerOutgoingPluginChannel(this, CHANNEL);
        ProtocolManager manager = ProtocolLibrary.getProtocolManager();

        manager.addPacketListener(new ChatPacketListener());
        /*IgnoreCommandHandler ignoreHandler = new IgnoreCommandHandler();
        PluginCommand ignoreCommand = getServer().getPluginCommand("confirm");
        if(ignoreCommand != null) {
            ignoreCommand.setExecutor(ignoreHandler);
            ignoreCommand.setTabCompleter(ignoreHandler);
        } else {
            Logger.getLogger(this.getClass().getSimpleName()).warning("Ignore command not found.");
        }*/
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                commands -> {
                    commands.registrar().register(ConfirmCommand.createCommand("confirm"),
                            "Confirm compatibility issues");
                });
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                commands -> {
                    commands.registrar().register(IntroCommand.createCommand("intro"),
                            "Introduction plugin management.");
                });
        loadData();
    }

    @Override
    public void onDisable() {
        unloadData();
    }

    public void unloadData() {
        Bukkit.getOnlinePlayers().forEach(PlayerListener::exitAllRooms);
        first.unload();
        second.unload();
        ChatPacketListener.unSilenceAll();
    }

    public void loadData() {
        first = new FirstRoom(getConfig().getConfigurationSection("firstRoom"));
        second = new SecondRoom(getConfig().getConfigurationSection("secondRoom"));
        first.setNextRoom(second);
    }

    public static IntroductionPlugin getInstance(){return instance;}

    public Room getRoom(Player player) {
        if(first.isInside(player)) {
            return first;
        } else if(second.isInside(player)) {
            return second;
        }
        return null;
    }

    public Room getFirst() {
        return first;
    }
    public Room getSecond() {
        return second;
    }
}
