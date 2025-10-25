package com.mcmiddleearth.introduction.paper.listener;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.mcmiddleearth.introduction.paper.IntroductionPlugin;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

public class ChatPacketListener extends PacketAdapter {

    private static final Set<UUID> silenced = new HashSet<>();

    public ChatPacketListener() {
        super(IntroductionPlugin.getInstance(),
                ListenerPriority.NORMAL,
                PacketType.Play.Server.SYSTEM_CHAT,
                PacketType.Play.Server.CHAT);
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        if (silenced.contains(uuid)) {
            WrappedChatComponent comp = event.getPacket().getChatComponents().read(0);
            //if (comp != null) {
            String message = comp.getJson();
            //String message = event.getPacket().getStrings().readSafely(0);
            Logger.getGlobal().info("Message to silenced: "+message);
            Logger.getGlobal().info("Contains marker: "+(message!=null?message.contains(IntroductionPlugin.CHANNEL):"null"));
            if(!(message != null && message.contains(IntroductionPlugin.CHANNEL))) {
                event.setCancelled(true);
                Logger.getGlobal().info("Blocked chat packet to " + event.getPlayer().getName()
                        + " (" + event.getPacketType().name() + ")");
            }
        }
    }

    public static void silence(Player player) {
        silenced.add(player.getUniqueId());
    }

    public static void unSilence(Player player) {
        silenced.remove(player.getUniqueId());
    }

    public static void unSilenceAll() {
        silenced.clear();
    }

}
