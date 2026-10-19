package com.mcmiddleearth.introduction.paper.flow;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mcmiddleearth.introduction.paper.IntroductionPlugin;
import com.mcmiddleearth.introduction.paper.rooms.Room;
import com.mcmiddleearth.introduction.paper.rooms.SecondRoom;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * The events of the flow log, one method each. docs/flow-log.md in the repository describes them
 * for readers such as hub-flow, so a change here is a change to that description. Events and
 * fields may be added; a field never changes its meaning under the same name.
 */
public final class FlowEvents {

    /**
     * How a player left a room.
     */
    public enum Exit {
        /** Stepped left, to walk on to the next room. */
        WALKED,
        /** Used /confirm optifine or /confirm ignore. */
        CONFIRMED,
        /** Passed on without stepping left: the client is fine now, or the player confirmed before. */
        SKIPPED,
        /** Left the server. */
        QUIT,
        /** Got out of the room some other way, such as a teleport. */
        LEFT,
        /** The plugin stopped: the server stopped, or /intro reload. */
        STOPPED;

        private String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private FlowEvents() {
    }

    public static void start(String pluginVersion, List<String> supportedVersions) {
        log().log("start", null, event -> {
            event.addProperty("plugin", pluginVersion);
            JsonArray versions = new JsonArray();
            supportedVersions.forEach(versions::add);
            event.add("supportedVersions", versions);
        });
    }

    public static void stop() {
        log().log("stop", null, event -> { });
    }

    /**
     * The player was put in a room.
     */
    public static void enter(Room room, Player player) {
        log().log("enter", player.getUniqueId(), event -> {
            event.addProperty("room", room.getFlowName());
            addClient(event, player);
            if(room instanceof SecondRoom second) {
                event.addProperty("warning", second.getWarning(player).key());
            }
        });
    }

    /**
     * The player was passed over a room without being put in it.
     */
    public static void skip(Room room, Player player) {
        log().log("skip", player.getUniqueId(), event -> {
            event.addProperty("room", room.getFlowName());
            addClient(event, player);
            event.addProperty("compatible", second().isCompatible(player));
        });
    }

    /**
     * What came of the resource pack a room waits for to show its picture.
     *
     * @param result loaded, not_sent, timeout, or the failure Architect reports, in lower case
     * @param waitingSince when the room started waiting
     */
    public static void pack(Room room, Player player, String result, Instant waitingSince) {
        log().log("pack", player.getUniqueId(), event -> {
            event.addProperty("room", room.getFlowName());
            event.addProperty("result", result);
            event.addProperty("waitMs", Duration.between(waitingSince, Instant.now()).toMillis());
        });
    }

    /**
     * The player left a room.
     *
     * @param entered when the player was put in the room, if known
     */
    public static void exit(Room room, Player player, Exit how, @Nullable Instant entered) {
        log().log("exit", player.getUniqueId(), event -> {
            event.addProperty("room", room.getFlowName());
            event.addProperty("how", how.key());
            if(entered != null) {
                event.addProperty("stayedMs", Duration.between(entered, Instant.now()).toMillis());
            }
        });
    }

    /**
     * The player is through the rooms: the welcome title and the tips start.
     *
     * @param first whether it is the player's first time
     */
    public static void finish(Player player, boolean first) {
        log().log("finish", player.getUniqueId(), event -> event.addProperty("first", first));
    }

    /**
     * The compatibility warning was shown again, outside the rooms.
     */
    public static void reminder(Player player, SecondRoom.Warning warning) {
        log().log("reminder", player.getUniqueId(), event -> event.addProperty("warning", warning.key()));
    }

    /**
     * The player used /confirm.
     *
     * @param choice optifine, ignore or reset
     * @param inRoom whether the player was in one of the rooms
     */
    public static void confirm(Player player, String choice, boolean inRoom) {
        log().log("confirm", player.getUniqueId(), event -> {
            event.addProperty("choice", choice);
            event.addProperty("inRoom", inRoom);
        });
    }

    private static void addClient(JsonObject event, Player player) {
        SecondRoom second = second();
        ProtocolVersion version = second.getClientVersion(player);
        event.addProperty("version", version.getName());
        event.addProperty("protocol", version.getVersion());
        event.addProperty("brand", player.getClientBrandName());
        event.addProperty("marker", second.isMcmeMarker(player));
        event.addProperty("supported", second.isSupportedVersion(version));
    }

    private static FlowLog log() {
        return IntroductionPlugin.getInstance().getFlowLog();
    }

    private static SecondRoom second() {
        return (SecondRoom) IntroductionPlugin.getInstance().getSecond();
    }
}
