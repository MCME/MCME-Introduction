package com.mcmiddleearth.introduction.paper.listener;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.mcmiddleearth.introduction.paper.IntroductionPlugin;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class ChatPacketListener extends PacketAdapter {

    // Written on the main thread, read on Netty's threads for every packet sent.
    private static final Set<UUID> silenced = ConcurrentHashMap.newKeySet();

    // Only system chat. Since 1.21.5 the client numbers the player chat messages (Play.Server.CHAT)
    // it receives and disconnects when one is missing, so dropping one disconnects the player at
    // the next. Chat plugins such as VentureChat send their messages as system chat.
    public ChatPacketListener() {
        super(IntroductionPlugin.getInstance(),
                ListenerPriority.NORMAL,
                PacketType.Play.Server.SYSTEM_CHAT);
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        if (silenced.contains(uuid)) {
            // readSafely returns null both when the packet has no chat component field and when
            // the field is empty, where read(0) would throw or return null.
            WrappedChatComponent comp = event.getPacket().getChatComponents().readSafely(0);
            String message = (comp != null ? comp.getJson() : null);
            if (isBlocked(uuid, message)) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Whether a chat message must be hidden from a player. A silenced player sees only this
     * plugin's own messages, which carry the channel name as their insertion.
     *
     * @param messageJson the message as JSON, or null when the packet holds no readable message
     */
    public static boolean isBlocked(UUID uuid, String messageJson) {
        return silenced.contains(uuid)
                && !(messageJson != null && messageJson.contains(IntroductionPlugin.CHANNEL));
    }

    public static void silence(Player player) {
        silenced.add(player.getUniqueId());
Logger.getGlobal().info("Silenced player: " + player.getName());
    }

    public static void unSilence(Player player) {
        silenced.remove(player.getUniqueId());
Logger.getGlobal().info("Unsilenced player: " + player.getName());
    }

    public static void unSilenceAll() {
        silenced.clear();
    }

}
