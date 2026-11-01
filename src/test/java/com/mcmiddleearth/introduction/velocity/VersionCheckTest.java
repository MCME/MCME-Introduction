package com.mcmiddleearth.introduction.velocity;

import com.google.gson.JsonObject;
import com.mcmiddleearth.introduction.flow.FlowLog;
import com.mcmiddleearth.introduction.flow.FlowLogFiles;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.proxy.server.ServerPing;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The proxy refuses client versions the backends don't support, before Mojang's login, shows the
 * supported versions in the server list, and notes each refusal in its flow log.
 */
class VersionCheckTest {

    @TempDir
    Path dataDirectory;

    private Path config;
    private FlowLog flowLog;
    private VersionCheck check;

    @BeforeEach
    void setUp() throws IOException {
        config = dataDirectory.resolve("config.yml");
        Files.writeString(config, "supportedVersions: ['26.2', '26.3']\n");
        flowLog = FlowLog.open(dataDirectory.resolve("flow"), true, 90, message -> { });
        check = new VersionCheck(NOPLogger.NOP_LOGGER, config, flowLog);
        check.start("1.5.0");
    }

    @Test
    void aSupportedClientGetsIn() {
        PreLoginEvent login = login(ProtocolVersion.MINECRAFT_26_2);

        check.onPreLogin(login);

        assertTrue(login.getResult().isAllowed());
    }

    @Test
    void anUnsupportedClientIsRefusedWithTheSupportedVersions() {
        PreLoginEvent login = login(ProtocolVersion.MINECRAFT_26_1);

        check.onPreLogin(login);

        assertFalse(login.getResult().isAllowed());
        String reason = plain(login.getResult().getReasonComponent().orElseThrow());
        assertTrue(reason.contains("Minecraft 26.2, 26.3"), reason);
    }

    @Test
    void aJsonMessageIsShownAsFormattedText() throws IOException {
        Files.writeString(config, """
                supportedVersions: ['26.2']
                proxy:
                  messageUnsupportedVersion: '{"text":"Use <supported_versions>","color":"red"}'
                """);
        PreLoginEvent login = login(ProtocolVersion.MINECRAFT_26_1);

        check.onPreLogin(login);

        assertEquals("Use 26.2", plain(login.getResult().getReasonComponent().orElseThrow()));
    }

    @Test
    void aLoginAnotherPluginRefusedIsLeftAlone() {
        PreLoginEvent login = login(ProtocolVersion.MINECRAFT_26_1);
        login.setResult(PreLoginEvent.PreLoginComponentResult.denied(Component.text("banned")));

        check.onPreLogin(login);

        assertEquals("banned", plain(login.getResult().getReasonComponent().orElseThrow()));
        assertTrue(events("refuse").isEmpty(), "not refused by the version check");
    }

    @Test
    void aRefusalGoesInTheFlowLogWithoutTheName() {
        check.onPreLogin(login(ProtocolVersion.MINECRAFT_26_1));

        JsonObject refuse = single(events("refuse"));
        assertEquals("26.1-26.1.2", refuse.get("version").getAsString());
        assertEquals(ProtocolVersion.MINECRAFT_26_1.getProtocol(), refuse.get("protocol").getAsInt());
        assertEquals(List.of("time", "event", "version", "protocol"), List.copyOf(refuse.keySet()),
                "no name and no UUID: " + refuse);
    }

    @Test
    void theLogStartsWithThePluginAndTheEnforcedVersions() {
        assertEquals("1.5.0", single(events("start")).get("plugin").getAsString());
        JsonObject versions = single(events("versions"));
        assertTrue(versions.get("check").getAsBoolean());
        assertEquals(List.of("26.2", "26.3"),
                versions.getAsJsonArray("supportedVersions").asList().stream().map(v -> v.getAsString()).toList());
    }

    @Test
    void aChangedListIsReadAtTheNextLoginAndLogged() throws IOException {
        Files.writeString(config, "supportedVersions: ['26.3']\n");
        PreLoginEvent login = login(ProtocolVersion.MINECRAFT_26_2);

        check.onPreLogin(login);

        assertFalse(login.getResult().isAllowed(), "26.2 is no longer listed");
        List<JsonObject> versions = events("versions");
        assertEquals(2, versions.size());
        assertEquals("26.3", versions.getLast().getAsJsonArray("supportedVersions").get(0).getAsString());
    }

    @Test
    void anUnreadableListLetsEveryoneInAndSaysWhy() throws IOException {
        Files.writeString(config, "supportedVersions: ['26.2'\n");
        PreLoginEvent login = login(ProtocolVersion.MINECRAFT_1_21_11);

        check.onPreLogin(login);

        assertTrue(login.getResult().isAllowed());
        JsonObject versions = events("versions").getLast();
        assertFalse(versions.get("check").getAsBoolean());
        assertTrue(versions.get("problem").getAsString().contains("not valid YAML"), versions.toString());
    }

    @Test
    void theServerListShowsTheSupportedVersions() {
        ProxyPingEvent ping = ping(ProtocolVersion.MINECRAFT_26_2);

        check.onProxyPing(ping);

        assertEquals("Minecraft 26.2, 26.3", ping.getPing().getVersion().getName());
        assertEquals(ProtocolVersion.MINECRAFT_26_2.getProtocol(), ping.getPing().getVersion().getProtocol());
    }

    @Test
    void anUnsupportedClientSeesTheVersionsInRed() {
        // Any protocol but the client's makes it show the version name in red: the newest supported one.
        ProxyPingEvent ping = ping(ProtocolVersion.MINECRAFT_26_1);

        check.onProxyPing(ping);

        assertEquals("Minecraft 26.2, 26.3", ping.getPing().getVersion().getName());
        assertEquals(ProtocolVersion.MINECRAFT_26_3.getProtocol(), ping.getPing().getVersion().getProtocol());
    }

    @Test
    void withTheCheckOffTheServerListIsLeftAlone() throws IOException {
        Files.writeString(config, """
                supportedVersions: ['26.2']
                proxy:
                  versionCheck: false
                """);
        ProxyPingEvent ping = ping(ProtocolVersion.MINECRAFT_26_1);

        check.onProxyPing(ping);

        assertEquals("from the proxy", ping.getPing().getVersion().getName());
    }

    @Test
    void theLogEndsWithStop() {
        check.stop();

        List<JsonObject> all = readAll();
        assertEquals("stop", all.getLast().get("event").getAsString());
    }

    private static PreLoginEvent login(ProtocolVersion version) {
        return new PreLoginEvent(connection(version), "Newcomer");
    }

    private static ProxyPingEvent ping(ProtocolVersion version) {
        ServerPing original = new ServerPing(new ServerPing.Version(version.getProtocol(), "from the proxy"),
                null, Component.text("MCME"), null);
        return new ProxyPingEvent(connection(version), original);
    }

    private static InboundConnection connection(ProtocolVersion version) {
        InboundConnection connection = mock(InboundConnection.class);
        when(connection.getProtocolVersion()).thenReturn(version);
        return connection;
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /** All events so far; writes the waiting ones first, which ends the log. */
    private List<JsonObject> readAll() {
        flowLog.close();
        return FlowLogFiles.read(dataDirectory.resolve("flow"));
    }

    private List<JsonObject> events(String event) {
        return readAll().stream().filter(e -> event.equals(e.get("event").getAsString())).toList();
    }

    private static JsonObject single(List<JsonObject> events) {
        assertEquals(1, events.size(), "events: " + events);
        return events.getFirst();
    }
}
