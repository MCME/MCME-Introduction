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
            // readSafely returns null both when the packet has no chat component field and when
            // the field is empty, as it usually is for player chat. read(0) threw or returned null.
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
    static boolean isBlocked(UUID uuid, String messageJson) {
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
