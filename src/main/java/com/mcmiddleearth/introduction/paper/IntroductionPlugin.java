package com.mcmiddleearth.introduction.paper;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.mcmiddleearth.introduction.paper.command.ConfirmCommand;
import com.mcmiddleearth.introduction.paper.command.IntroCommand;
import com.mcmiddleearth.introduction.paper.confirmData.ConfirmDataManager;
import com.mcmiddleearth.introduction.paper.flow.FlowEvents;
import com.mcmiddleearth.introduction.paper.flow.FlowEvents.Exit;
import com.mcmiddleearth.introduction.paper.flow.FlowLog;
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
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;

/**
 *
 * @author Eriol_Eandur
 */
// Not final: MockBukkit loads the plugin in tests by subclassing it.
public class IntroductionPlugin extends JavaPlugin {

    private static IntroductionPlugin instance;

    private Room first, second;

    private GlowListener glowListener;

    private ConfirmDataManager confirmDataManager;

    private FlowLog flowLog = FlowLog.disabled();

    // From /intro reload until its delayed load has run.
    private boolean reloadPending = false;

    public static final String CHANNEL = "mcme:intro";

    @SuppressWarnings("UnstableApiUsage")
    @Override
    public void onEnable() {
        saveDefaultConfig();
        instance = this;
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

        loadData();
    }

    @Override
    public void onDisable() {
        // Delete the advancement files, so the next start registers the advancements anew from config.yml.
        first.removeAdvancement();
        second.removeAdvancement();
        IntroductionChain.removeAdvancements();
        unloadData();
    }

    public void unloadData() {
        Bukkit.getOnlinePlayers().forEach(player -> RoomListener.exitAllRooms(player, Exit.STOPPED));
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
        FlowEvents.stop();
        flowLog.close();
    }

    /**
     * /intro reload: takes the rooms, the tips and the listeners down, and builds them again from
     * config.yml and locations.yml a second later. Tells the sender how it went; a reload while one
     * is pending is ignored, as it would load everything twice.
     */
    public void reload(CommandSender sender) {
        if(reloadPending) {
            sendErrorMessage(sender, "A reload is running already.");
            return;
        }
        sendInfoMessage(sender, "Reloading config.yml and locations.yml...");
        unloadData();
        reloadPending = true;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                reloadConfig();
                loadData();
                sendInfoMessage(sender, "Reload done.");
            } catch(RuntimeException e) {
                getLogger().log(Level.SEVERE, "/intro reload failed: the rooms are off until a reload works.", e);
                sendErrorMessage(sender, "Reload failed: see the console.");
            } finally {
                reloadPending = false;
            }
        }, 20);
    }

    public void loadData() {
        flowLog = FlowLog.open(getDataFolder(), getConfig(), getLogger());
        IntroductionChain.load();
        YamlConfiguration locationConfig = new YamlConfiguration();
        try {
            locationConfig.load(new File(getDataFolder(), "locations.yml"));
        } catch (FileNotFoundException ignore) {
            // A server without rooms.
        } catch (IOException | InvalidConfigurationException e) {
            getLogger().severe("locations.yml can't be read, so this server has no rooms: " + e.getMessage());
        }
        first = new FirstRoom(getConfig().getConfigurationSection("firstRoom"),
                              locationConfig.getConfigurationSection("firstRoom"));
        second = new SecondRoom(getConfig().getConfigurationSection("secondRoom"),
                              locationConfig.getConfigurationSection("secondRoom"));
        first.setNextRoom(second);
        getServer().getPluginManager().registerEvents(new RoomListener(),this);
        glowListener = new GlowListener(locationConfig.getConfigurationSection("itemGlow"));
//Logger.getGlobal().info("Enable "+glowListener);
        Bukkit.getPluginManager().registerEvents(glowListener,this);
        // Register pre-login listener which will load data on demand (AsyncPlayerPreLoginEvent).
        // It is registered here rather than in onEnable because unloadData() unregisters all listeners.
        Bukkit.getPluginManager().registerEvents(new ConfirmPreLoginListener(), this);
        FlowEvents.start(getPluginMeta().getVersion(), ((SecondRoom) second).getSupportedVersions());
    }

    public static IntroductionPlugin getInstance(){return instance;}

    /**
     * Registers an advancement, or returns the one already registered under its key. Paper keeps a
     * registered advancement until the server stops, and saves it as a file that the next start loads
     * again unless onDisable deleted it. So after /intro reload, or at a start after a crash, the key
     * can be taken already, and registering it again would fail.
     *
     * @return null when the advancement is not valid: Paper then skips it without a word, so the
     *         warning here names it
     */
    @Nullable
    public static Advancement loadAdvancement(NamespacedKey key, String advancement) {
        Advancement registered = Bukkit.getAdvancement(key);
        if(registered != null) {
            return registered;
        }
        Advancement loaded = Bukkit.getUnsafe().loadAdvancement(key, advancement);
        if(loaded == null) {
            getInstance().getLogger().warning("Advancement " + key + " is not valid, so it is left out."
                    + " Check its display in config.yml.");
        }
        return loaded;
    }

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

    public FlowLog getFlowLog() {
        return flowLog;
    }

    private static String prefix = "[MCME-Intro] ";

    public static void sendInfoMessage(CommandSender sender, String message) {
        sender.sendMessage(Component.text(prefix+message).color(NamedTextColor.AQUA).insertion(IntroductionPlugin.CHANNEL));
    }
    public static void sendErrorMessage(CommandSender sender, String message) {
        sender.sendMessage(Component.text(prefix+message).color(NamedTextColor.RED).insertion(IntroductionPlugin.CHANNEL));
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

    public boolean isInsideRoom(Player player) {
        return first.isInside(player.getLocation()) || second.isInside(player.getLocation());
    }
}
