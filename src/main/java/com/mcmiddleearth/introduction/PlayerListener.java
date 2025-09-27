package com.mcmiddleearth.introduction;

import com.mcmiddleearth.introduction.rooms.Room;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

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
//Logger.getGlobal().info("In Room: "+room);
        if(room!=null) {
            event.setCancelled(true);
        }
    }

    //@EventHandler(priority = EventPriority.LOWEST)
    //public void nextRoom(PlayerSwapHandItemsEvent event){
    private void nextRoom(Player player) {
        //if in room switch to next one
        Room room = IntroductionPlugin.getInstance().getRoom(player);
//Logger.getGlobal().info("In Room: "+room);
        if(room!=null) {
            if(room.canIgnore()) {
                room.handleOverride(player, true);
            }
            //event.setCancelled(true);
            teleportToNextRoom(room, player);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void playerMove(PlayerMoveEvent event){
        //if in room cancel movement
        Room room = IntroductionPlugin.getInstance().getRoom(event.getPlayer());
//Logger.getGlobal().info("In Room: "+room);
        if(room!=null) {
            event.setCancelled(true);
             if(!room.isAwaitingTeleport(event.getPlayer())) {
                //Logger.getGlobal().info("Is AWaiting teleport: "+room.isAwaitingTeleport(event.getPlayer()));
                if (room.isSkipped(event.getPlayer())) {
                    teleportToNextRoom(room, event.getPlayer());
                    //Logger.getGlobal().info("TLEPOT TO NEXT ROOM");
                } else {
                    room.handleEnter(event.getPlayer());
                    Location loc = event.getPlayer().getLocation().clone();
                    loc.setPitch(0);
                    Vector movement = event.getTo().toVector().subtract(event.getFrom().toVector()).normalize();
                    Vector direction = loc.getDirection();
                    Vector crossProduct = movement.clone();
                    crossProduct.crossProduct(direction);
                    //Logger.getGlobal().info(""+movement);
                    //Logger.getGlobal().info(""+direction);
                    //Logger.getGlobal().info(""+crossProduct);
                    if (crossProduct.length() > 0.7 && crossProduct.getY() < 0) {
                        nextRoom(event.getPlayer());
                        //Logger.getGlobal().info("NEXT ROOM");
                    }
                }
            }
        } else {
            exitAllRooms(event.getPlayer());
        }

    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void joinServer(PlayerJoinEvent event) {
        //if in room give matching camera overlay.
        Room room = IntroductionPlugin.getInstance().getRoom(event.getPlayer());
//Logger.getGlobal().info("In Room: " + room);
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
                    second.handleOverride(event.getPlayer(), true);
                    Bukkit.getScheduler().runTaskLater(IntroductionPlugin.getInstance(), () -> {
                                second.handleOverride(event.getPlayer(), false);
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
        Room previous = room;
        while(next != null && next.isSkipped(player)) {
            room = next;
            next = next.getNext();
        }
        room.teleport(player, previous, next);
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
