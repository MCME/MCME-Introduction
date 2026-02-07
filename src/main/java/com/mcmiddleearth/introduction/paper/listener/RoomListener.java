package com.mcmiddleearth.introduction.paper.listener;

import com.mcmiddleearth.connect.events.PlayerConnectEvent;
import com.mcmiddleearth.introduction.paper.IntroductionChain;
import com.mcmiddleearth.introduction.paper.IntroductionPlugin;
import com.mcmiddleearth.introduction.paper.rooms.Room;
import com.mcmiddleearth.introduction.paper.rooms.SecondRoom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;

import java.util.logging.Logger;

/**
 *
 * @author Eriol_Eandur
 */
public class RoomListener implements Listener {

    // Set of players that are allowed to teleport out of a room once (single-use)
    //private static final Set<UUID> allowedTeleportOut = ConcurrentHashMap.newKeySet();

    /**
     * Mark a player so that the next teleport event will be allowed (one-time).
     */
    /*public static void allowTeleportOut(Player player) {
        if(player == null) return;
        allowedTeleportOut.add(player.getUniqueId());
    }*/

    /**
     * Check and consume the one-time allow flag for the given player.
     * Returns true if the player was allowed (and consumes the flag), false otherwise.
     */
    /*public static boolean isAllowTeleportOut(Player player) {
        if(player == null) return false;
Logger.getGlobal().info("Allow teleport: "+allowedTeleportOut.contains(player.getUniqueId()));
        return allowedTeleportOut.remove(player.getUniqueId());
    }*/

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(PlayerInteractEvent event){
        //if in room cancel
        Room room = IntroductionPlugin.getInstance().getRoom(event.getPlayer().getLocation());
//Logger.getGlobal().info("In Room: "+room);
        if(room!=null) {
            event.setCancelled(true);
        }
    }

    //@EventHandler(priority = EventPriority.LOWEST)
    //public void nextRoom(PlayerSwapHandItemsEvent event){
    private void nextRoom(Player player) {
        //if in room switch to next one
        Room room = IntroductionPlugin.getInstance().getRoom(player.getLocation());
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
    public void playerTeleport(PlayerTeleportEvent event){
//Logger.getGlobal().info("Player teleport event for "+event.getPlayer().getName()+" cancelled: "+event.isCancelled());
        handlePlayerMoveIntoRoom(event);
        //if(!isAllowTeleportOut(event.getPlayer())) handlePlayerMove(event);
        //Logger.getGlobal().info("Teleport event for "+event.getPlayer().getName()+" cancelled: "+event.isCancelled());
        //Bukkit.getScheduler().runTaskLater(IntroductionPlugin.getInstance(),
        //        () -> handlePlayerMove(event),1);
        //handlePlayerMove(event);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void playerMove(PlayerMoveEvent event) {
        //Logger.getGlobal().info("Playermove event for " + event.getPlayer().getName() + " cancelled: " + event.isCancelled());
        if(!handlePlayerMoveIntoRoom(event)) {
            handlePlayerMove(event);
        }
    }

    private boolean handlePlayerMoveIntoRoom(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();
        Room fromRoom = IntroductionPlugin.getInstance().getRoom(from);
        Room toRoom = IntroductionPlugin.getInstance().getRoom(to);
        //enter a room or move between rooms
        if (fromRoom != toRoom && toRoom != null) {
            toRoom.setPlayerEnterTime(player.getUniqueId());
            Bukkit.getScheduler().runTaskLater(IntroductionPlugin.getInstance(),
                () -> {
                    player.teleport(toRoom.getPlayerLocation());
                    Bukkit.getScheduler().runTaskLater(IntroductionPlugin.getInstance(),
                        () -> {
                            handlePlayerMove(event);
                        }, 1);
                },1);
            return true;
        }
        return false;
    }

    private void handlePlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Room room = IntroductionPlugin.getInstance().getRoom(player.getLocation());
//Logger.getGlobal().info("In Room: "+room);
        //already in a room
        if(room != null) {
            /*if(!isAllowTeleportOut(player))*/
             event.setCancelled(true);
             if(!room.isAwaitingTeleport(player)) {
                //Logger.getGlobal().info("Is AWaiting teleport: "+room.isAwaitingTeleport(player));
                if (room.isSkipped(player)) {
                    teleportToNextRoom(room, player);
                    //Logger.getGlobal().info("TELEPORT TO NEXT ROOM");
                } else {
                    if(!room.handleEnter(player)
                            && room.getPlayerEnterTime(player.getUniqueId())
                                 + IntroductionPlugin.getInstance().getConfig().getInt("roomEnterMoveDelay",10)
                                < Bukkit.getCurrentTick()) {
                        Location loc = player.getLocation().clone();
                        loc.setPitch(0);
                        Vector movement = event.getTo().toVector().subtract(event.getFrom().toVector()).normalize();
                        Vector direction = event.getFrom().getDirection();
                        Vector crossProduct = movement.clone();
                        crossProduct.crossProduct(direction);
/*if (!((Double) movement.getX()).isNaN()) {
    Logger.getGlobal().info("mov " + movement);
    Logger.getGlobal().info("dir " + direction + " pitch " + loc.getPitch() + " yaw " + loc.getYaw());
    Logger.getGlobal().info("cro " + crossProduct);
}*/
                        if (crossProduct.length() > 0.7 && crossProduct.getY() < 0) {
                            nextRoom(player);
                            //Logger.getGlobal().info("NEXT ROOM");
                        }
                    }
                }
            }
        } else {
            exitAllRooms(player);
        }

    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void joinServer(PlayerConnectEvent event) {
        //if in room give matching camera overlay.
        Room room = IntroductionPlugin.getInstance().getRoom(event.getPlayer().getLocation());
//Logger.getGlobal().info("In Room: " + room);
        if (room != null) {
            room.silence(event.getPlayer());
            Bukkit.getScheduler().runTaskLater(IntroductionPlugin.getInstance(), () -> {
                if (room.isSkipped(event.getPlayer())) {
                    teleportToNextRoom(room, event.getPlayer());
                } else {
                    room.handleEnter(event.getPlayer());
                }
            }, IntroductionPlugin.getInstance().getConfig().getLong("joinDelay",10));
         } else {
            Bukkit.getScheduler().runTaskLater(IntroductionPlugin.getInstance(), () -> {
                Room second = IntroductionPlugin.getInstance().getSecond();
                Player player = event.getPlayer();
                if(!second.isSkipped(player)) {
                    ((SecondRoom)second).startReminderTask(player);
                }
                IntroductionChain.continueChain(player);
            }, IntroductionPlugin.getInstance().getConfig().getLong("joinDelay",10));
        }
    }

    @EventHandler
    public void quitServer(PlayerQuitEvent event) {
        IntroductionPlugin.getInstance().getFirst().handleOverride(event.getPlayer(), false);
        IntroductionPlugin.getInstance().getSecond().handleOverride(event.getPlayer(), false);
        exitAllRooms(event.getPlayer());
        IntroductionChain.interruptChain(event.getPlayer());
        ((SecondRoom)IntroductionPlugin.getInstance().getSecond()).cancelReminderTask(event.getPlayer());
    }

    /*@EventHandler
    public void rpLoaded(PlayerResourcePackStatusEvent event) {
        Room room = IntroductionPlugin.getInstance().getRoom(event.getPlayer());
        if(room instanceof FirstRoom first) {
            switch (event.getStatus()) {
                case PlayerResourcePackStatusEvent.Status.DECLINED:
                case PlayerResourcePackStatusEvent.Status.DISCARDED:
                case PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD:
                case PlayerResourcePackStatusEvent.Status.FAILED_RELOAD:
                case PlayerResourcePackStatusEvent.Status.INVALID_URL:
                    first.sendRpWarning(event.getPlayer());
                    break;
                case PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED:
                    //todo: handleEnter set overlay
            }
        }
    }*/

    @EventHandler
    public void blockInventoryOpen(InventoryOpenEvent event) {
//Logger.getGlobal().info("open inv");
        if (event.getPlayer() instanceof Player player) {
//Logger.getGlobal().info("is player");
            if (IntroductionPlugin.getInstance().getRoom(player.getLocation()) != null) {
                event.setCancelled(true);
//Logger.getGlobal().info("cancelled");
            }
        }
    }

    public static void teleportToNextRoom(Room room, Player player) {
        Room next = room.getNext();
        Room previous = room;
//Logger.getGlobal().info("1 skipped: "+next+" "+next.isSkipped(player));
        while(next != null && next.isSkipped(player)) {
            room = next;
            next = next.getNext();
//Logger.getGlobal().info("2 skipped: "+next+" "+next.isSkipped(player));
        }
//Logger.getGlobal().info(room+" Teleporting "+player.getName()+" from "+previous+" to "+next);
        room.teleport(player, previous, next);
    }

    public static void exitAllRooms(Player player) {
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
