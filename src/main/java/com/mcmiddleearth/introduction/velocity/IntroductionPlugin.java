package com.mcmiddleearth.introduction.velocity;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.packet.chat.SystemChatPacket;
import io.github._4drian3d.vpacketevents.api.event.PacketSendEvent;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;


@Plugin(
        id = "mcme-introduction",
        name = "MCME-Introduction",
        version = "1.0.0",
        authors = {"Eriol_Eandur"}
)
public final class IntroductionPlugin {

    private final ProxyServer proxyServer;
    private final Logger logger;
    private final Set<UUID> silenced = new HashSet<>();

    @Inject
    public IntroductionPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxyServer = server;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        proxyServer.getChannelRegistrar().register(MinecraftChannelIdentifier.from(com.mcmiddleearth.introduction.paper.IntroductionPlugin.CHANNEL));
        logger.info("Registering VPacketEvents listener…");
    }

    @Subscribe
    public void onPacketSend(PacketSendEvent event) {
        final MinecraftPacket packet = event.getPacket();
        //logger.info(packet.getClass().getSimpleName());
        if(silenced.contains(event.getPlayer().getUniqueId())) {
            if (packet instanceof SystemChatPacket) {
                event.setResult(ResultedEvent.GenericResult.denied());
                logger.info("Chat blocked!");
            }
        }
    }

    @Subscribe
    public void onPluginMessageFromBackend(PluginMessageEvent event) {
        if (!com.mcmiddleearth.introduction.paper.IntroductionPlugin.CHANNEL.equals(event.getIdentifier().getId())) {
            return;
        }
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if (!(event.getSource() instanceof ServerConnection backend)) {
            return;
        }
        ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());
        String subchannel = in.readUTF();
        UUID uuid = UUID.fromString(in.readUTF());
        Player player = proxyServer.getPlayer(uuid).orElse(null);
        if(subchannel.equals("enter") && player != null) {
            silenced.add(uuid);
            logger.info("silenced: "+player.getUsername());
        } else if(subchannel.equals("exit")) {
            silenced.remove(uuid);
            logger.info("un-silenced: "+(player!=null?player.getUsername():"null"));
        }
    }

    /*@Subscribe
    public void onPacketSend(PacketSendEvent event) {
        PacketType type = event.getPacketType();

        // Chat & system chat packets (clientbound)
        if (type == PacketTypes.Play.Client.CHAT_MESSAGE
                || type == PacketTypes.Play.Client.SYSTEM_CHAT_MESSAGE
                || type == PacketTypes.Play.Client.PLAYER_CHAT_MESSAGE) {

            event.setResult(Result.DENY); // cancels sending
        }
    }*/
}

