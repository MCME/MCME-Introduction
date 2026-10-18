package com.mcmiddleearth.introduction.paper.rooms;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mcmiddleearth.architect.serverResoucePack.RpPlayerData;
import com.mcmiddleearth.architect.serverResoucePack.RpPlayerStatus;
import com.mcmiddleearth.connect.events.PlayerConnectEvent;
import com.mcmiddleearth.introduction.paper.IntroductionChain;
import com.mcmiddleearth.introduction.paper.flow.FlowEvents.Exit;
import com.mcmiddleearth.introduction.paper.flow.FlowLogFiles;
import com.mcmiddleearth.introduction.paper.listener.RoomListener;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every step a player takes through the rooms goes in the flow log, with what a reader needs to
 * tell why: the client on entering, the pack's result, how and after how long the player left.
 */
class RoomFlowLogTest extends RoomTestBase {

    private Function<Player, RpPlayerData> realPackStatus;
    private final RpPlayerData packStatus = new RpPlayerData();
    private World world;
    private String brand = "vanilla";
    private PlayerMock player;

    @BeforeEach
    void setUp() throws IOException {
        realPackStatus = Room.rpPlayerData;
        Room.rpPlayerData = p -> packStatus;
        // Without its advancement, the first room awards none (MockBukkit cannot show it).
        plugin.getConfig().set("firstRoom.advancementDisplay", null);
        world = addRooms();
        player = new PlayerMock(server, "Newcomer") {
            @Override
            public String getClientBrandName() {
                return brand;
            }
        };
        server.addPlayer(player);
        player.teleport(new Location(world, 40, 61, 2));
    }

    @AfterEach
    void restorePackStatus() {
        Room.rpPlayerData = realPackStatus;
    }

    @Test
    void theLogStartsWithThePluginVersionAndTheSupportedVersions() {
        JsonObject start = events("start").getLast();

        assertEquals(plugin.getPluginMeta().getVersion(), start.get("plugin").getAsString());
        JsonArray versions = start.getAsJsonArray("supportedVersions");
        assertEquals(List.of("26.2", "26.3"), versions.asList().stream().map(v -> v.getAsString()).toList());
    }

    @Test
    void enteringTheWelcomeRoomRecordsTheClient() {
        plugin.getFirst().handleEnter(player);

        JsonObject enter = single(events("enter"));
        assertEquals(player.getUniqueId().toString(), enter.get("uuid").getAsString());
        assertEquals("welcome", enter.get("room").getAsString());
        assertEquals(ProtocolVersion.v26_2.getName(), enter.get("version").getAsString());
        assertEquals(ProtocolVersion.v26_2.getVersion(), enter.get("protocol").getAsInt());
        assertEquals("vanilla", enter.get("brand").getAsString());
        assertFalse(enter.get("marker").getAsBoolean());
        assertTrue(enter.get("supported").getAsBoolean());
        assertFalse(enter.has("warning"), "the welcome room shows no warning");
    }

    @ParameterizedTest(name = "{0} client, marker mod {1}, {2}: {3}")
    @CsvSource({
            "vanilla, false, 1.21.4, version",
            "forge,   false, 26.2,   modded",
            "fabric,  true,  1.21.4, mcme",
            "fabric,  false, 26.2,   missing_mod",
    })
    void enteringTheCompatibilityRoomRecordsTheWarningShown(String clientBrand, boolean marker, String version,
                                                           String warning) {
        brand = clientBrand;
        SecondRoom.mcmeMarker = p -> marker;
        SecondRoom.clientVersion = p -> ProtocolVersion.getClosest(version);

        plugin.getSecond().handleEnter(player);

        JsonObject enter = single(events("enter"));
        assertEquals("compatibility", enter.get("room").getAsString());
        assertEquals(warning, enter.get("warning").getAsString());
    }

    @Test
    void enteringARoomThePlayerIsInAlreadyIsNotRecordedAgain() {
        player.setLocation(new Location(world, 2, 61, 2));
        plugin.getFirst().handleEnter(player);
        plugin.getFirst().handleEnter(player);

        assertEquals(1, events("enter").size());
    }

    @Test
    void leavingARoomRecordsHowAndHowLongThePlayerStayed() {
        plugin.getFirst().handleEnter(player);

        plugin.getFirst().handleExit(player, false, Exit.WALKED);

        JsonObject exit = single(events("exit"));
        assertEquals("welcome", exit.get("room").getAsString());
        assertEquals("walked", exit.get("how").getAsString());
        assertTrue(exit.get("stayedMs").getAsLong() >= 0);
    }

    @Test
    void leavingARoomThePlayerWasNotInIsNotRecorded() {
        plugin.getFirst().handleExit(player, false, Exit.WALKED);

        assertTrue(events("exit").isEmpty());
    }

    @Test
    void walkingOnFromTheWelcomeRoomIntoTheCompatibilityRoom() {
        SecondRoom.clientVersion = p -> ProtocolVersion.v1_21_4;
        plugin.getFirst().handleEnter(player);

        RoomListener.teleportToNextRoom(plugin.getFirst(), player, Exit.WALKED);
        server.getScheduler().performTicks(TELEPORT_DELAY + 1);

        assertEquals("walked", single(events("exit")).get("how").getAsString());
        List<JsonObject> enters = events("enter");
        assertEquals(List.of("welcome", "compatibility"), enters.stream().map(e -> e.get("room").getAsString()).toList());
    }

    @Test
    void aCompatibleClientPassesOverTheCompatibilityRoomAndFinishes() {
        plugin.getFirst().handleEnter(player);

        RoomListener.teleportToNextRoom(plugin.getFirst(), player, Exit.WALKED);
        server.getScheduler().performTicks(TELEPORT_DELAY + 1);

        JsonObject skip = single(events("skip"));
        assertEquals("compatibility", skip.get("room").getAsString());
        assertTrue(skip.get("compatible").getAsBoolean());
        assertEquals("vanilla", skip.get("brand").getAsString());
        assertTrue(single(events("finish")).get("first").getAsBoolean());
    }

    @Test
    void aPlayerWhoConfirmedEarlierPassesOverTheCompatibilityRoom() {
        SecondRoom.clientVersion = p -> ProtocolVersion.v1_21_4;
        plugin.getConfirmDataManager().confirmIgnore(player.getUniqueId());
        plugin.getFirst().handleEnter(player);

        RoomListener.teleportToNextRoom(plugin.getFirst(), player, Exit.WALKED);

        assertFalse(single(events("skip")).get("compatible").getAsBoolean());
    }

    @Test
    void aPlayerWhoJoinsInTheCompatibilityRoomWithAFixedClientIsPassedOn() {
        // Where the player quit last time; logging in there is no teleport.
        player.setLocation(new Location(world, 22, 61, 2));

        server.getPluginManager().callEvent(new PlayerConnectEvent(player, PlayerConnectEvent.ConnectReason.JOIN_PROXY));
        server.getScheduler().performTicks(plugin.getConfig().getLong("joinDelay") + 1);

        JsonObject skip = single(events("skip"));
        assertEquals("compatibility", skip.get("room").getAsString());
        assertTrue(skip.get("compatible").getAsBoolean());
        assertTrue(events("exit").isEmpty(), "the player was never put in the room");
    }

    @ParameterizedTest(name = "turning the head {0} ticks after joining")
    @ValueSource(ints = {0, 5})
    void aPlayerWhoLooksAroundWhileJoiningIsPassedOnOnce(int moveAfter) {
        player.setLocation(new Location(world, 22, 61, 2));
        server.getPluginManager().callEvent(new PlayerConnectEvent(player, PlayerConnectEvent.ConnectReason.JOIN_PROXY));
        server.getScheduler().performTicks(moveAfter);

        // Turning the head is a move too: the room passes the player on before the join delay is over.
        player.simulatePlayerMove(new Location(world, 22, 61, 2, 90, 0));
        server.getScheduler().performTicks(plugin.getConfig().getLong("joinDelay") + TELEPORT_DELAY + 1);

        assertEquals(1, events("skip").size(), "passed over the room");
        assertEquals(1, events("finish").size(), "the welcome title and the tips start");
    }

    @Test
    void aPlayerWhoLeavesDuringTheJoinDelayIsNotPutInTheRoom() {
        SecondRoom.clientVersion = p -> ProtocolVersion.v1_21_4;
        player.setLocation(new Location(world, 22, 61, 2));
        server.getPluginManager().callEvent(new PlayerConnectEvent(player, PlayerConnectEvent.ConnectReason.JOIN_PROXY));

        player.disconnect();
        server.getScheduler().performTicks(plugin.getConfig().getLong("joinDelay") + 1);

        assertTrue(events("enter").isEmpty());
    }

    @Test
    void aPlayerInTheCompatibilityRoomWhoNeedNotBeThereLeavesItSkipped() {
        SecondRoom.clientVersion = p -> ProtocolVersion.v1_21_4;
        player.setLocation(new Location(world, 22, 61, 2));
        plugin.getSecond().handleEnter(player);
        // Confirmed on another server meanwhile, say; the room notices at the next move.
        plugin.getConfirmDataManager().confirmIgnore(player.getUniqueId());

        player.simulatePlayerMove(new Location(world, 22.5, 61, 2));
        server.getScheduler().performTicks(TELEPORT_DELAY + 1);

        assertEquals("skipped", single(events("exit")).get("how").getAsString());
        assertTrue(events("skip").isEmpty(), "the player was in the room, not passed over it");
    }

    @Test
    void finishingAgainIsRecordedAsNotTheFirstTime() throws IOException {
        Files.writeString(new File(plugin.getDataFolder(), "finishedPlayerList.uid").toPath(),
                player.getUniqueId() + System.lineSeparator());
        plugin.unloadData();
        plugin.loadData();

        IntroductionChain.startChain(player);

        assertFalse(single(events("finish")).get("first").getAsBoolean());
    }

    @Test
    void quittingInARoomIsRecorded() {
        plugin.getFirst().handleEnter(player);

        player.disconnect();

        assertEquals("quit", single(events("exit")).get("how").getAsString());
    }

    @Test
    void beingMovedOutOfARoomIsRecorded() {
        plugin.getFirst().handleEnter(player);

        player.simulatePlayerMove(new Location(world, 41, 61, 2));

        assertEquals("left", single(events("exit")).get("how").getAsString());
    }

    @Test
    void theServerStoppingWithAPlayerInARoomIsRecorded() {
        plugin.getFirst().handleEnter(player);

        server.getPluginManager().disablePlugin(plugin);

        assertEquals("stopped", single(events("exit")).get("how").getAsString());
        List<JsonObject> all = FlowLogFiles.read(flowFolder().toPath());
        assertEquals("stop", all.getLast().get("event").getAsString());
    }

    @Test
    void theLoadedPackIsRecorded() {
        packStatus.setCurrentRpStatus(RpPlayerStatus.SUCCESSFULLY_LOADED);
        plugin.getFirst().handleEnter(player);

        server.getScheduler().performTicks(1);

        JsonObject pack = single(events("pack"));
        assertEquals("welcome", pack.get("room").getAsString());
        assertEquals("loaded", pack.get("result").getAsString());
        assertTrue(pack.get("waitMs").getAsLong() >= 0);
    }

    @Test
    void aDeclinedPackIsRecorded() {
        packStatus.setCurrentRpStatus(RpPlayerStatus.DECLINED);
        plugin.getFirst().handleEnter(player);

        server.getScheduler().performTicks(1);

        assertEquals("declined", single(events("pack")).get("result").getAsString());
    }

    @Test
    void noPackAfterTenSecondsIsRecordedAndSoIsAPackThatComesLater() {
        // A new RpPlayerData reads NOT_SENT: no pack offered.
        plugin.getFirst().handleEnter(player);
        server.getScheduler().performTicks(250);

        packStatus.setCurrentRpStatus(RpPlayerStatus.SUCCESSFULLY_LOADED);
        server.getScheduler().performTicks(10);

        assertEquals(List.of("not_sent", "loaded"),
                events("pack").stream().map(e -> e.get("result").getAsString()).toList());
    }

    @Test
    void aPackThatNeverFinishesLoadingIsRecordedAsTimedOut() {
        packStatus.setCurrentRpStatus(RpPlayerStatus.SENT);
        plugin.getFirst().handleEnter(player);

        server.getScheduler().performTicks(5100);

        assertEquals("timeout", single(events("pack")).get("result").getAsString());
    }

    @Test
    void aReminderIsRecordedWithItsWarningButNotItsPack() {
        SecondRoom.clientVersion = p -> ProtocolVersion.v1_21_4;

        ((SecondRoom) plugin.getSecond()).startReminderTask(player);
        server.getScheduler().performTicks(250);

        assertEquals("version", single(events("reminder")).get("warning").getAsString());
        assertTrue(events("pack").isEmpty(), "pack results are recorded for the rooms only");
    }

    @Test
    void aConfirmOutsideTheRoomsIsRecorded() {
        SecondRoom.clientVersion = p -> ProtocolVersion.v1_21_4;
        // Paper registers Brigadier commands at the first dispatch.
        server.dispatchCommand(server.getConsoleSender(), "help");

        player.performCommand("confirm ignore");

        JsonObject confirm = single(events("confirm"));
        assertEquals("ignore", confirm.get("choice").getAsString());
        assertFalse(confirm.get("inRoom").getAsBoolean());
    }

    @Test
    void aConfirmInTheCompatibilityRoomIsRecordedAndSoIsTheExit() {
        SecondRoom.clientVersion = p -> ProtocolVersion.v1_21_4;
        server.dispatchCommand(server.getConsoleSender(), "help");
        player.teleport(new Location(world, 22, 61, 2));
        plugin.getSecond().handleEnter(player);

        player.performCommand("confirm ignore");
        server.getScheduler().performTicks(TELEPORT_DELAY + 1);

        assertTrue(single(events("confirm")).get("inRoom").getAsBoolean());
        JsonObject exit = single(events("exit"));
        assertEquals("compatibility", exit.get("room").getAsString());
        assertEquals("confirmed", exit.get("how").getAsString());
    }

    private File flowFolder() {
        return new File(plugin.getDataFolder(), "flow");
    }

    /** The events of one kind so far; writes the waiting ones first, which ends the log. */
    private List<JsonObject> events(String event) {
        plugin.getFlowLog().close();
        return FlowLogFiles.read(flowFolder().toPath()).stream()
                .filter(e -> event.equals(e.get("event").getAsString()))
                .toList();
    }

    private static JsonObject single(List<JsonObject> events) {
        assertEquals(1, events.size(), "events: " + events);
        return events.getFirst();
    }
}
