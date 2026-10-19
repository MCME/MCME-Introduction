package com.mcmiddleearth.introduction.velocity;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
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

import com.google.inject.Inject;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


@Plugin(
        id = "${plugin.id}",
        name = "${plugin.name}",
        version = "${plugin.version}",
        authors = { "${plugin.author}" }
)
public final class IntroductionPlugin {

    private final ProxyServer proxyServer;
    private final Logger logger;
    // Written on Velocity's event threads, read on Netty's threads for every packet sent.
    private final Set<UUID> silenced = ConcurrentHashMap.newKeySet();

    /**
     * Velocity constructs this class through Guice, so the constructor must carry an
     * annotation the injector recognises.
     * <p>
     * Guice's own {@code com.google.inject.Inject} is used rather than a JSR-330
     * {@code @Inject}. Velocity 3.x shipped Guice 6, which understood both
     * {@code javax.inject} and {@code jakarta.inject}; Velocity 4 ships Guice 7, which
     * dropped {@code javax.inject} support entirely. A {@code @javax.inject.Inject} here is
     * therefore invisible to Velocity 4's injector, which then reports the class as having
     * neither an injectable nor a no-arg constructor. Guice's own annotation is recognised
     * by every Guice version, so it stays correct on both proxies.
     */
    @Inject
    public IntroductionPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxyServer = server;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        proxyServer.getChannelRegistrar().register(MinecraftChannelIdentifier.from(com.mcmiddleearth.introduction.paper.IntroductionPlugin.CHANNEL));
        logger.info("Listening on " + com.mcmiddleearth.introduction.paper.IntroductionPlugin.CHANNEL
                + " for players in the introduction rooms, whose proxy chat is hidden.");
    }

    @Subscribe
    public void onPacketSend(PacketSendEvent event) {
        final MinecraftPacket packet = event.getPacket();
        //logger.info(packet.getClass().getSimpleName());
        if(silenced.contains(event.getPlayer().getUniqueId())) {
            if (packet instanceof SystemChatPacket) {
                event.setResult(ResultedEvent.GenericResult.denied());
                logger.debug("Chat blocked!");
            }
        }
    }

    /**
     * The backend's "exit" is sent while the player quits it, so it may never arrive: a player who
     * left the rooms by leaving the server would stay silenced. A player is in no room on the
     * proxy's word, so leaving the proxy or arriving on a server ends the silence. A backend with
     * rooms sends "enter" again when the player is put in one there.
     */
    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        silenced.remove(event.getPlayer().getUniqueId());
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        silenced.remove(event.getPlayer().getUniqueId());
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
        String subchannel;
        UUID uuid;
        try {
            ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());
            subchannel = in.readUTF();
            uuid = UUID.fromString(in.readUTF());
        } catch (IllegalStateException | IllegalArgumentException e) {
            logger.warn("Ignored a malformed " + com.mcmiddleearth.introduction.paper.IntroductionPlugin.CHANNEL
                    + " message from " + backend.getServerInfo().getName() + ": " + e.getMessage());
            return;
        }
        // A backend speaks only for the player whose connection carries the message.
        Player player = backend.getPlayer();
        if(!uuid.equals(player.getUniqueId())) {
            return;
        }
        if(subchannel.equals("enter")) {
            silenced.add(uuid);
            logger.info("silenced: "+player.getUsername());
        } else if(subchannel.equals("exit")) {
            silenced.remove(uuid);
            logger.info("un-silenced: "+player.getUsername());
        }
    }

}

