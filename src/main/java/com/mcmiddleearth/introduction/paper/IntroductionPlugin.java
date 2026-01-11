package com.mcmiddleearth.introduction.paper;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.mcmiddleearth.introduction.paper.command.ConfirmCommand;
import com.mcmiddleearth.introduction.paper.command.IntroCommand;
import com.mcmiddleearth.introduction.paper.confirmData.ConfirmDataManager;
import com.mcmiddleearth.introduction.paper.listener.ChatPacketListener;
import com.mcmiddleearth.introduction.paper.listener.ConfirmPreLoginListener;
import com.mcmiddleearth.introduction.paper.listener.GlowListener;
import com.mcmiddleearth.introduction.paper.listener.RoomListener;
import com.mcmiddleearth.introduction.paper.rooms.FirstRoom;
import com.mcmiddleearth.introduction.paper.rooms.Room;
import com.mcmiddleearth.introduction.paper.rooms.SecondRoom;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 *
 * @author Eriol_Eandur
 */
public final class IntroductionPlugin extends JavaPlugin {

    private static IntroductionPlugin instance;

    private Room first, second;

    private GlowListener glowListener;

    private ConfirmDataManager confirmDataManager;

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

        // Initialize confirm data manager (may use DB if configured)
        confirmDataManager = new ConfirmDataManager();

        // Register pre-login listener which will load data on demand (AsyncPlayerPreLoginEvent)
        Bukkit.getPluginManager().registerEvents(new ConfirmPreLoginListener(), this);

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
        IntroductionChain.unload();
    }

    public void loadData() {
        IntroductionChain.load();
        first = new FirstRoom(getConfig().getConfigurationSection("firstRoom"));
        second = new SecondRoom(getConfig().getConfigurationSection("secondRoom"));
        first.setNextRoom(second);
        getServer().getPluginManager().registerEvents(new RoomListener(),this);
        glowListener = new GlowListener(getConfig().getConfigurationSection("itemGlow"));
//Logger.getGlobal().info("Enable "+glowListener);
        Bukkit.getPluginManager().registerEvents(glowListener,this);
    }

    public static IntroductionPlugin getInstance(){return instance;}

    public Room getRoom(Location location) {
        if(first.isInside(location)) {
            return first;
        } else if(second.isInside(location)) {
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

    public GlowListener getGlowListener() {
        return glowListener;
    }

    public ConfirmDataManager getConfirmDataManager() {
        return confirmDataManager;
    }

    private static String prefix = "[MCME-Intro] ";

    public static void sendInfoMessage(Player player, String message) {
        player.sendMessage(Component.text(prefix+message).color(NamedTextColor.AQUA).insertion(IntroductionPlugin.CHANNEL));
    }
    public static void sendErrorMessage(Player player, String message) {
        player.sendMessage(Component.text(prefix+message).color(NamedTextColor.RED).insertion(IntroductionPlugin.CHANNEL));
    }

    // Overloads to send formatted Components loaded from config (JSON)
    public static void sendInfoMessage(Player player, Component message) {
        Component pref = Component.text(prefix).color(NamedTextColor.AQUA).insertion(IntroductionPlugin.CHANNEL);
        player.sendMessage(pref.append(message));
    }

    public static void sendErrorMessage(Player player, Component message) {
        Component pref = Component.text(prefix).color(NamedTextColor.RED).insertion(IntroductionPlugin.CHANNEL);
        player.sendMessage(pref.append(message));
    }

}
