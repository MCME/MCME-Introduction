package com.mcmiddleearth.introduction;

import com.mcmiddleearth.introduction.rooms.Room;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 *
 * @author Eriol_Eandur
 */
public class PlayerListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(PlayerInteractEvent event){
        //if in room cancel
        Room room = IntroductionPlugin.getInstance().getRoom(event.getPlayer());
Logger.getGlobal().info("In Room: "+room);
        if(room!=null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void nextRoom(PlayerSwapHandItemsEvent event){
        //if in room switch to next one
        Room room = IntroductionPlugin.getInstance().getRoom(event.getPlayer());
Logger.getGlobal().info("In Room: "+room);
        if(room!=null && !room.canIgnore()) {
            event.setCancelled(true);
            teleportToNextRoom(room, event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void playerMove(PlayerMoveEvent event){
        //if in room cancel movement
        Room room = IntroductionPlugin.getInstance().getRoom(event.getPlayer());
Logger.getGlobal().info("In Room: "+room);
        if(room!=null) {
            if(room.isSkipped(event.getPlayer())) {
                teleportToNextRoom(room, event.getPlayer());
            } else {
                room.handleEnter(event.getPlayer());
                event.setCancelled(true);
            }
        } else {
            exitAllRooms(event.getPlayer());
        }

    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void joinServer(PlayerJoinEvent event) {
        //if in room give matching camera overlay.
        Room room = IntroductionPlugin.getInstance().getRoom(event.getPlayer());
        Logger.getGlobal().info("In Room: " + room);
        if (room != null) {
            Bukkit.getScheduler().runTaskLater(IntroductionPlugin.getInstance(), () -> {
                if (room.isSkipped(event.getPlayer())) {
                    teleportToNextRoom(room, event.getPlayer());
                } else {
                    room.handleEnter(event.getPlayer());
                }
            }, IntroductionPlugin.getInstance().getConfig().getLong("joinDelay",10));
        } else {
            Room second = IntroductionPlugin.getInstance().getSecond();
            if(!second.isSkipped(event.getPlayer())) {
                Bukkit.getScheduler().runTaskLater(IntroductionPlugin.getInstance(), () -> {
                    second.handleEnter(event.getPlayer());
                    second.setFixed(event.getPlayer(), true);
                    Bukkit.getScheduler().runTaskLater(IntroductionPlugin.getInstance(), () -> {
                                second.setFixed(event.getPlayer(), false);
                                second.handleExit(event.getPlayer());
                    },
                    IntroductionPlugin.getInstance().getConfig().getLong("reminderDuration", 100));
                }, IntroductionPlugin.getInstance().getConfig().getLong("joinDelay",10));
            }
        }
    }

    @EventHandler
    public void quitServer(PlayerQuitEvent event) {
        exitAllRooms(event.getPlayer());
    }

    public static void teleportToNextRoom(Room room, Player player) {
        Room next = room.getNext();
        while(next != null && next.isSkipped(player)) {
            room = next;
            next = next.getNext();
        }
        room.teleport(player, next);
    }

    private void exitAllRooms(Player player) {
        IntroductionPlugin.getInstance().getFirst().handleExit(player);
        IntroductionPlugin.getInstance().getSecond().handleExit(player);
    }

    public static void showStatus() {
        Logger.getGlobal().info("First");
        IntroductionPlugin.getInstance().getFirst().showPlayers();
        Logger.getGlobal().info("Second");
        IntroductionPlugin.getInstance().getSecond().showPlayers();
    }
}
