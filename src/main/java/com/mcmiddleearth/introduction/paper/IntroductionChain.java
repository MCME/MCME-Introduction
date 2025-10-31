package com.mcmiddleearth.introduction.paper;

import com.mcmiddleearth.introduction.paper.rooms.Room;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.logging.Logger;

public class IntroductionChain {

    private static final Map<UUID, Integer> playerStages = new HashMap<>();
    private static final Map<UUID, BukkitTask> playerTasks = new HashMap<>();
    private static final List<Advancement> advancements = new ArrayList<>();

    private static final String advancementKey = "mcme:introchain";

    private static ConfigurationSection config;

    private static Component startMessage;

    public static void load() {
        config = IntroductionPlugin.getInstance().getConfig()
                .getConfigurationSection("introductionChain");
        if(config != null) {
            startMessage = Room.getMessage(config.getString("startMessage"));
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
        player.sendMessage(startMessage);
        playerStages.put(player.getUniqueId(), 0);
        continueChain(player);
    }

    public static void continueChain(Player player) {
        if(playerStages.containsKey(player.getUniqueId())) {
            int stage = playerStages.get(player.getUniqueId());
                playerTasks.put(player.getUniqueId(), new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (stage < advancements.size()) {
                            player.getAdvancementProgress(advancements.get(stage)).awardCriteria("manual");
                            Bukkit.getScheduler().runTaskLater(IntroductionPlugin.getInstance(), () -> {
                                player.getAdvancementProgress(advancements.get(stage)).revokeCriteria("manual");
                                playerStages.put(player.getUniqueId(), stage+1);
                            }, 200);
                        } else {
                            playerStages.remove(player.getUniqueId());
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
    }
}
