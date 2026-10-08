package com.mcmiddleearth.introduction.paper.listener;

import com.mcmiddleearth.introduction.paper.IntroductionPlugin;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.*;
import java.util.*;

public class GlowListener implements Listener {

    private World world;
    private BoundingBox box;

    private float size = 0.5f;

    private double rayRange = 6;
    private double rayWidth = 1.5;
    private double repetitions = 2;
    private Team team;
    private final Map<UUID, ItemDisplay> glowEntities = new HashMap<>();
    private final Map<Player, Map<ItemDisplay, Integer>> lookEvents = new HashMap<>();

    private final Set<UUID> enabledGloItems = new HashSet<>();
    private final File enabledGlowItemsFile = new File(IntroductionPlugin.getInstance().getDataFolder(),"EnabledGlowItems.dat");

    private final BukkitTask task;

    private final Set<Material> introductionItems = new HashSet<>();

    public GlowListener(ConfigurationSection config) {
        world = Bukkit.getWorld("world");
        box = new BoundingBox(-4100,-50,-4400,-4000,-30,-4300);
        if(!enabledGlowItemsFile.exists()) {
            try {
                enabledGlowItemsFile.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        try(Scanner scanner = new Scanner(enabledGlowItemsFile)) {
            while(scanner.hasNext()) {
                enabledGloItems.add(UUID.fromString(scanner.nextLine()));
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        if(config != null) {
            Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
            String teamName = "mcme_intro";
            team = scoreboard.getTeam(teamName);
            if (team == null) {
                team = scoreboard.registerNewTeam(teamName);
            }
            String colorName = config.getString("glowColor","WHITE");
            NamedTextColor glowColor = NamedTextColor.NAMES.value(colorName.toLowerCase());
            team.color(glowColor);
            size = (float) (size * (1+config.getDouble("zoom", 0.01)));
            world = Bukkit.getWorld(config.getString("world","hub"));
            rayRange = config.getDouble("rayRange",6);
            rayWidth = config.getDouble("rayWidth", 1.5);
            repetitions = config.getInt("rayRepetitions",2);
            ConfigurationSection pos1 = config.getConfigurationSection("pos1");
            ConfigurationSection pos2 = config.getConfigurationSection("pos2");
            if(pos1 != null && pos2 != null){
                double xMin = pos1.getDouble("x");
                double xMax = pos2.getDouble("x");
                double yMin = pos1.getDouble("y");
                double yMax = pos2.getDouble("y");
                double zMin = pos1.getDouble("z");
                double zMax = pos2.getDouble("z");
                box = new BoundingBox(xMin, yMin, zMin, xMax, yMax, zMax);
            }
            List<String> itemNames = config.getStringList("items");
            for(String name: itemNames) {
                Material itemMaterial = null;
                try {
                    itemMaterial = Material.valueOf(name.toUpperCase());
                } catch(IllegalArgumentException ignore) {}
                if(itemMaterial != null) {
                    introductionItems.add(itemMaterial);
                }
            }
        }
        task = new BukkitRunnable() {
            @Override
            public void run() {
                Bukkit.getOnlinePlayers().forEach(player -> {
                //lookEvents.forEach((player,playerEvents) -> {
//Logger.getGlobal().info("Runnable: "+task.getTaskId()+" width: "+rayWidth);
                    updateGlow(player);
                    Map<ItemDisplay, Integer> playerEvents = lookEvents.get(player);
                    if(playerEvents!=null) {
                        Set<ItemDisplay> removals = new HashSet<>();
                        playerEvents.forEach((entity, timestamp) -> {
                            if (Bukkit.getServer().getCurrentTick() > timestamp + 20) {
                                removals.add(entity);
                                player.hideEntity(IntroductionPlugin.getInstance(), entity);
                                //Logger.getGlobal().info("Hide entity: "+entity+" from "+player);
                            }
                        });
                        removals.forEach(playerEvents::remove);
                    }
                });
            }
        }.runTaskTimer(IntroductionPlugin.getInstance(), 20, 5);
    }

    //@EventHandler
    //public void onPlayerMove(PlayerMoveEvent event) {
    public void updateGlow(Player player) {
        //Player player = event.getPlayer();
        if (player.getWorld().equals(world)
                && enabledGloItems.contains(player.getUniqueId())
                && box.contains(player.getLocation().toVector())) {
            rayTraceEntities(player).forEach(hitEntity -> {
                ItemDisplay glowEntity = glowEntities.get(hitEntity.getUniqueId());
                if(glowEntity == null) {
                    glowEntity = (ItemDisplay) world.spawnEntity(hitEntity.getLocation(), EntityType.ITEM_DISPLAY);
                    glowEntity.setItemStack(((ItemFrame)hitEntity).getItem());
                    glowEntity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                    glowEntity.setTransformation(new Transformation(new Vector3f(0,-1f/32,0),
                                                 new Quaternionf(),
                                                 new Vector3f(size,size,size),
                                                 new Quaternionf().rotationX((float)Math.toRadians(-90))));
//Logger.getGlobal().info("Facing: "+hitEntity.getFacing());
//Logger.getGlobal().info("Rotation: "+hitEntity.getRotation());
                    float rot = getRotation(hitEntity);
                    glowEntity.setRotation(rot,0);
//Logger.getGlobal().info("Glow Rotation: "+rot);
                    glowEntity.setVisibleByDefault(false);
                    glowEntity.setPersistent(false);
                    glowEntity.setGlowing(true);
                    team.addEntity(glowEntity);
                    glowEntities.put(hitEntity.getUniqueId(),glowEntity);
//Logger.getGlobal().info("Create entity "+glowEntity);
                }
                player.showEntity(IntroductionPlugin.getInstance(), glowEntity);
//Logger.getGlobal().info("Show entity: "+glowEntity+" to "+player);
                Map<ItemDisplay, Integer> playerEvents = lookEvents.computeIfAbsent(player, k -> new HashMap<>());
                playerEvents.put(glowEntity, Bukkit.getServer().getCurrentTick());
            });
        } else {
            hideAllGlowEntities(player);
        }

    }

    @EventHandler
    public void onEntityUnload(EntitiesUnloadEvent event) {
        event.getEntities().forEach(entity -> {
            ItemDisplay glowEntity = glowEntities.get(entity.getUniqueId());
            if(glowEntity != null) {
                glowEntity.remove();
                glowEntities.remove(entity.getUniqueId());
            }
        });
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event) {
        hideAllGlowEntities(event.getPlayer());
    }

    private Set<ItemFrame> rayTraceEntities(Player player) {
        Set<ItemFrame> hitEntities = new HashSet<>();
//Logger.getGlobal().info("width: "+rayWidth);
        for(int i = 0; i < repetitions; i++) {
            RayTraceResult result = world.rayTraceEntities(player.getEyeLocation(), player.getEyeLocation().getDirection(),
                    rayRange, rayWidth, entity -> isIntroductionItem(entity) && !hitEntities.contains((ItemFrame)entity));
            if(result!=null && result.getHitEntity()!=null) {
                hitEntities.add((ItemFrame)result.getHitEntity());
            } else {
                return hitEntities;
            }
        }
        return hitEntities;
    }

    private void hideAllGlowEntities(Player player) {
        Map<ItemDisplay,Integer> playerEvents = lookEvents.get(player);
        if(playerEvents!=null) {
            playerEvents.forEach((entity, timestamp) -> {
                player.hideEntity(IntroductionPlugin.getInstance(), entity);
            });
        }
        lookEvents.remove(player);
    }

    private boolean isIntroductionItem(Entity entity) {
        if(entity instanceof ItemFrame itemFrame) {
//Logger.getGlobal().info("Check: "+itemFrame.getItem().getType());
            return introductionItems.contains(itemFrame.getItem().getType());
        }
        return false;
    }

    private float getRotation(ItemFrame itemFrame) {
        return switch(itemFrame.getRotation()) {
            case CLOCKWISE_45 -> 45;
            case CLOCKWISE -> 90;
            case CLOCKWISE_135 -> 135;
            case FLIPPED -> 180;
            case FLIPPED_45 -> 225;
            case COUNTER_CLOCKWISE -> 270;
            case COUNTER_CLOCKWISE_45 -> 315;
            default -> 0;
        };
    }

    public void disable() {
//Logger.getGlobal().info("Task: "+task.getTaskId());
        task.cancel();
        Bukkit.getOnlinePlayers().forEach(this::hideAllGlowEntities);
    }

    public void setItemGlow(Player player, boolean glowing) {
        if(!glowing) {
            hideAllGlowEntities(player);
            enabledGloItems.remove(player.getUniqueId());
            saveEnabledGlowItems();
        } else {
            enabledGloItems.add(player.getUniqueId());
            saveEnabledGlowItems();
        }
    }

    private void saveEnabledGlowItems() {
        try(PrintWriter writer = new PrintWriter(new FileOutputStream(enabledGlowItemsFile))) {
            for(UUID uuid: enabledGloItems) {
                writer.println(uuid.toString());
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private record LookEvent(ItemDisplay entity, int timestamp) { }
}
