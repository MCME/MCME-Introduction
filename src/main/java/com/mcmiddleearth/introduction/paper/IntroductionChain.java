package com.mcmiddleearth.introduction.paper;

import com.mcmiddleearth.connect.util.ConnectUtil;
import com.mcmiddleearth.introduction.paper.rooms.Room;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.TitlePart;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.time.Duration;
import java.util.*;
import java.util.logging.Logger;

public class IntroductionChain {

    private static final Map<UUID, Integer> playerStages = new HashMap<>();
    private static final Map<UUID, BukkitTask> playerTasks = new HashMap<>();
    private static final List<Advancement> advancements = new ArrayList<>();
    private static final Set<UUID> finishedPlayers = new HashSet<>();

    private static final String advancementKey = "mcme:introchain";

    private static ConfigurationSection config;

    private static Component startMessage, startTitle, startSubtitle;
    private static String startBroadcast;
    private static final String finishedPlayerFilename = "finishedPlayerList.uid";
    private static long fadeIn, stay, fadeOut;

    public static void load() {
        try(Scanner scanner = new Scanner(new File(IntroductionPlugin.getInstance().getDataFolder(),finishedPlayerFilename))) {
            while(scanner.hasNext()) {
                finishedPlayers.add(UUID.fromString(scanner.nextLine()));
            }
        } catch (FileNotFoundException ignore) {
            //throw new RuntimeException(e);
        }
        config = IntroductionPlugin.getInstance().getConfig()
                .getConfigurationSection("introductionChain");
        if(config != null) {
            startMessage = Room.getMessage(config.getString("startMessage"));
            ConfigurationSection titleConfig = config.getConfigurationSection("title");
            if(titleConfig!=null) {
                startTitle = Room.getMessage(titleConfig.getString("title"));
                startSubtitle = Room.getMessage(titleConfig.getString("subtitle"));
                fadeIn = titleConfig.getLong("fadeIn");
                fadeOut = titleConfig.getLong("fadeOut");
                stay = titleConfig.getLong("stay");
            }
            startBroadcast = config.getString("broadcastMessage"," ");
            List<String> chainDisplays = config.getStringList("advancementDisplays");
            for (int i = 0; i < chainDisplays.size(); i++) {
Logger.getGlobal().info("Load: " + advancementKey);
                NamespacedKey key = NamespacedKey.fromString(advancementKey + i);
Logger.getGlobal().info("Key: "+key);
                //NamespacedKey key2 = NamespacedKey.fromString(advancementKey + (char)(64+i));
//Logger.getGlobal().info("Key2: "+key2);
                advancements.add(Bukkit.getUnsafe().loadAdvancement(NamespacedKey.fromString(advancementKey + i),
                        "{\"display\":" + chainDisplays.get(i) + ", \"criteria\":{\"manual\":{\"trigger\":\"minecraft:impossible\"}}}"));
            }
        }
    }

    public static void startChain(Player player) {
        player.sendTitlePart(TitlePart.TITLE, (startTitle != null ? startTitle : Component.text("Welcome")));
        player.sendTitlePart(TitlePart.SUBTITLE, (startSubtitle != null ? startSubtitle : Component.text("to")
                .append(Component.text("Minecraft Middle-earth").color(TextColor.fromCSSHexString("ffc13b")))));
        player.sendTitlePart(TitlePart.TIMES, Title.Times.times(
                Duration.ofSeconds(fadeIn),
                Duration.ofSeconds((stay != 0 ? stay : 5)),
                Duration.ofSeconds(fadeOut)));
        Bukkit.getScheduler().runTaskLater(IntroductionPlugin.getInstance(), () -> player.sendMessage(startMessage), 20 * (fadeIn + stay + fadeOut));
        if (!finishedPlayers.contains(player.getUniqueId())) {
            Bukkit.getScheduler().runTaskLater(IntroductionPlugin.getInstance(), () -> {
                int playerIndex = startBroadcast.indexOf("_@p_");
                String send = startBroadcast.substring(0, playerIndex)
                        + player.getName()
                        + startBroadcast.substring(playerIndex + 4);
                //Component broadcastMessage = LegacyComponentSerializer.legacy('§').deserialize(send);
                ConnectUtil.sendMessage(player, "", "", send, 0);
                //Bukkit.broadcast(broadcastMessage);
                finishedPlayers.add(player.getUniqueId());
                saveFinishedPlayers();
            }, 20 * (fadeIn + stay + fadeOut) + 200);
        }
        playerStages.put(player.getUniqueId(), 0);
        continueChain(player);
    }

    private static void saveFinishedPlayers() {
        try(PrintWriter writer = new PrintWriter(new FileOutputStream(new File(IntroductionPlugin.getInstance()
                                                                        .getDataFolder(),finishedPlayerFilename)))) {
            finishedPlayers.forEach(writer::println);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static void continueChain(Player player) {
        if(playerStages.containsKey(player.getUniqueId())) {
                playerTasks.put(player.getUniqueId(), new BukkitRunnable() {
                    @Override
                    public void run() {
                        if(playerStages.containsKey(player.getUniqueId())) {
                            int stage = playerStages.get(player.getUniqueId());
Logger.getGlobal().info("Stage: " + stage + " for " + player.getName());
                            if (stage < advancements.size()) {
                                player.getAdvancementProgress(advancements.get(stage)).awardCriteria("manual");
                                Bukkit.getScheduler().runTaskLater(IntroductionPlugin.getInstance(), () -> {
                                    player.getAdvancementProgress(advancements.get(stage)).revokeCriteria("manual");
                                    playerStages.put(player.getUniqueId(), stage + 1);
                                }, 200);
                            } else {
                                playerStages.remove(player.getUniqueId());
                                cancel();
                            }
                        } else {
                            cancel();
                        }
                    }
                }.runTaskTimer(IntroductionPlugin.getInstance(),
                        config.getLong("initialDelay", 100), config.getLong("period", 300)));
        }
    }

    public static void interruptChain(Player player) {
        BukkitTask task = playerTasks.get(player.getUniqueId());
        if(task!=null) task.cancel();
    }

    public static void unload() {
        advancements.forEach(advancement ->
                Bukkit.getUnsafe().removeAdvancement(advancement.getKey()));
        for(int i = 0; i < advancements.size(); i++) {
            Bukkit.getUnsafe().removeAdvancement(NamespacedKey.fromString(advancementKey+i));
        }
        advancements.clear();
    }
}
