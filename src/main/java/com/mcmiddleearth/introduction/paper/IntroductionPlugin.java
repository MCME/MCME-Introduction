package com.mcmiddleearth.introduction.paper;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.mcmiddleearth.introduction.paper.command.ConfirmCommand;
import com.mcmiddleearth.introduction.paper.command.IntroCommand;
import com.mcmiddleearth.introduction.paper.listener.ChatPacketListener;
import com.mcmiddleearth.introduction.paper.listener.GlowListener;
import com.mcmiddleearth.introduction.paper.listener.RoomListener;
import com.mcmiddleearth.introduction.paper.rooms.FirstRoom;
import com.mcmiddleearth.introduction.paper.rooms.Room;
import com.mcmiddleearth.introduction.paper.rooms.SecondRoom;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

/**
 *
 * @author Eriol_Eandur
 */
public final class IntroductionPlugin extends JavaPlugin {

    private static IntroductionPlugin instance;

    private Room first, second;

    private GlowListener glowListener;

    public static final String CHANNEL = "mcme:intro";

    @SuppressWarnings("UnstableApiUsage")
    @Override
    public void onEnable() {
        saveDefaultConfig();
        instance = this;
        getServer().getPluginManager().registerEvents(new RoomListener(),this);
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
        Bukkit.getOnlinePlayers().forEach(RoomListener::exitAllRooms);
        first.unload();
        second.unload();
        ChatPacketListener.unSilenceAll();
        //IntroductionChain.unload();
        if(glowListener!= null) {
//Logger.getGlobal().info("disable: "+glowListener);
            glowListener.disable();
        }
        HandlerList.unregisterAll(this);
    }

    public void loadData() {
        first = new FirstRoom(getConfig().getConfigurationSection("firstRoom"));
        second = new SecondRoom(getConfig().getConfigurationSection("secondRoom"));
        first.setNextRoom(second);
        getServer().getPluginManager().registerEvents(new RoomListener(),this);
        glowListener = new GlowListener(getConfig().getConfigurationSection("itemGlow"));
//Logger.getGlobal().info("Enable "+glowListener);
        Bukkit.getPluginManager().registerEvents(glowListener,this);
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
