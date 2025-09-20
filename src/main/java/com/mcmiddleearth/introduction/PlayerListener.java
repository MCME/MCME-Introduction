package com.mcmiddleearth.introduction;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

/**
 *
 * @author Eriol_Eandur
 */
public class PlayerListener implements Listener {

    @EventHandler(priority = EventPriority.NORMAL)
    public void onClick(PlayerInteractEvent event){
        //if in room cancel
        Room room = IntroductionPlugin.getInstance().getRoom(event.getPlayer());
        if(room!=null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void nextRoom(PlayerSwapHandItemsEvent event){
        //if in room switch to next one
        Room room = IntroductionPlugin.getInstance().getRoom(event.getPlayer());
        if(room!=null) {
            event.getPlayer().teleport(room.getTpTarget());
            Room next = room.getNext();
            if(next != null) {
                next.setCameraOverlay(event.getPlayer());
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void playerMove(PlayerMoveEvent event){
        //if in room cancel movement
        Room room = IntroductionPlugin.getInstance().getRoom(event.getPlayer());
        if(room!=null) {
            room.setCameraOverlay(event.getPlayer());
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void joinServer(PlayerJoinEvent event){
        //if in room give matching camera overlay.
        Room room = IntroductionPlugin.getInstance().getRoom(event.getPlayer());
        if(room!=null) {
            room.setCameraOverlay(event.getPlayer());
        }
    }
}
