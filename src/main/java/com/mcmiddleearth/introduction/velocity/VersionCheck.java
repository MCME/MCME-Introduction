package com.mcmiddleearth.introduction.velocity;

import com.google.gson.JsonArray;
import com.mcmiddleearth.introduction.flow.FlowLog;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.server.ServerPing;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.List;
import java.util.OptionalInt;

/**
 * Lets only the backends' supported client versions onto the network, before Mojang's login, and
 * shows the supported versions in the server list. Clients on other versions see them in red there
 * and get {@code proxy.messageUnsupportedVersion} when they try to join.
 * <p>
 * A refused client never reaches the hub, so its rooms can't record it: the proxy writes its own
 * flow log, with the enforced versions and each refusal, without names (docs/flow-log.md).
 */
public final class VersionCheck {

    private final Logger logger;
    private final FlowLog flowLog;
    private final VersionPolicyFile policyFile;

    public VersionCheck(Logger logger, Path configFile, FlowLog flowLog) {
        this.logger = logger;
        this.flowLog = flowLog;
        this.policyFile = new VersionPolicyFile(configFile, this::policyRead);
    }

    /** Starts the flow log and reads the policy once, which logs it. */
    public void start(String pluginVersion) {
        flowLog.log("start", null, event -> event.addProperty("plugin", pluginVersion));
        policyFile.current();
    }

    public void stop() {
        flowLog.log("stop", null, event -> { });
    }

    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        if (!event.getResult().isAllowed()) {
            return;
        }
        VersionPolicy policy = policyFile.current();
        ProtocolVersion client = event.getConnection().getProtocolVersion();
        if (policy.accepts(client.getVersionsSupportedBy())) {
            return;
        }
        event.setResult(PreLoginEvent.PreLoginComponentResult.denied(text(policy.message())));
        logger.info("Refused {}: client version {} is not in supportedVersions ({}).",
                event.getUsername(), versionName(client), String.join(", ", policy.versions()));
        flowLog.log("refuse", null, entry -> {
            entry.addProperty("version", versionName(client));
            entry.addProperty("protocol", client.getProtocol());
        });
    }

    /** Runs last, so the version survives plugins that build the rest of the server list entry. */
    @Subscribe(order = PostOrder.LAST)
    public void onProxyPing(ProxyPingEvent event) {
        VersionPolicy policy = policyFile.current();
        if (!policy.isActive()) {
            return;
        }
        ProtocolVersion client = event.getConnection().getProtocolVersion();
        int protocol;
        if (policy.accepts(client.getVersionsSupportedBy())) {
            protocol = client.getProtocol();
        } else {
            // Any protocol but the client's makes it show the version name in red.
            OptionalInt newest = newestKnownProtocol(policy);
            if (newest.isEmpty()) {
                return;
            }
            protocol = newest.getAsInt();
        }
        event.setPing(event.getPing().asBuilder()
                .version(new ServerPing.Version(protocol, policy.serverListVersion()))
                .build());
    }

    private static OptionalInt newestKnownProtocol(VersionPolicy policy) {
        return ProtocolVersion.SUPPORTED_VERSIONS.stream()
                .filter(version -> policy.accepts(version.getVersionsSupportedBy()))
                .mapToInt(ProtocolVersion::getProtocol)
                .max();
    }

    private static boolean knownToVelocity(String release) {
        return ProtocolVersion.SUPPORTED_VERSIONS.stream()
                .anyMatch(version -> version.getVersionsSupportedBy().contains(release));
    }

    /**
     * A protocol's name as the backends' flow log writes it (ViaVersion's naming): "26.2", or
     * "26.1-26.1.2" for releases that share a protocol.
     */
    static String versionName(ProtocolVersion version) {
        List<String> releases = version.getVersionsSupportedBy();
        return releases.size() == 1 ? releases.getFirst() : releases.getFirst() + "-" + releases.getLast();
    }

    private void policyRead(VersionPolicy policy) {
        flowLog.log("versions", null, entry -> {
            entry.addProperty("check", policy.isActive());
            JsonArray versions = new JsonArray();
            policy.versions().forEach(versions::add);
            entry.add("supportedVersions", versions);
            if (!policy.isActive()) {
                entry.addProperty("problem", policy.problem());
            }
        });
        if (!policy.isActive()) {
            logger.warn("Version check OFF, every client version gets in: {}", policy.problem());
            return;
        }
        logger.info("Version check on: letting in Minecraft {} (supportedVersions in {}).",
                String.join(", ", policy.versions()), policyFile.file());
        for (String release : policy.versions()) {
            if (!knownToVelocity(release)) {
                logger.warn("supportedVersions lists {}, which this Velocity does not know: no {} client can"
                        + " connect until Velocity is updated.", release, release);
            }
        }
    }

    /** A JSON text component, or plain text, as the backends' texts. */
    private Component text(String raw) {
        String trimmed = raw.strip();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                return GsonComponentSerializer.gson().deserialize(trimmed);
            } catch (RuntimeException e) {
                logger.error("proxy.messageUnsupportedVersion is not valid JSON, shown as plain text: {}", e.getMessage());
            }
        }
        return Component.text(raw);
    }
}
