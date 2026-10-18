package com.mcmiddleearth.introduction.paper.flow;

import com.google.common.util.concurrent.MoreExecutors;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The flow log's file format: one JSON object per line, in a file per UTC day, old days deleted.
 * The writer runs on the calling thread here, so a line is on disk when log() returns.
 */
class FlowLogTest {

    private static final UUID PLAYER = UUID.fromString("4b6f1c2e-9a3d-4e5f-8a7b-6c5d4e3f2a1b");

    @TempDir
    Path folder;

    private final SettableClock clock = new SettableClock(Instant.parse("2026-10-18T10:15:30.123Z"));

    private FlowLog openLog(int keepDays) {
        return new FlowLog(folder, keepDays, clock, MoreExecutors.newDirectExecutorService(), Logger.getAnonymousLogger());
    }

    @Test
    void anEventIsOneJsonLineWithItsTimeNameAndPlayer() {
        FlowLog log = openLog(90);

        log.log("enter", PLAYER, e -> e.addProperty("room", "welcome"));

        List<JsonObject> events = FlowLogFiles.read(folder);
        assertEquals(1, events.size());
        JsonObject event = events.getFirst();
        assertEquals("2026-10-18T10:15:30.123Z", event.get("time").getAsString());
        assertEquals("enter", event.get("event").getAsString());
        assertEquals(PLAYER.toString(), event.get("uuid").getAsString());
        assertEquals("welcome", event.get("room").getAsString());
    }

    @Test
    void anEventWithoutAPlayerHasNoUuid() {
        FlowLog log = openLog(90);

        log.log("start", null, e -> {});

        assertFalse(FlowLogFiles.read(folder).getFirst().has("uuid"));
    }

    @Test
    void eachEventGoesInTheFileOfItsUtcDay() {
        FlowLog log = openLog(90);

        clock.set(Instant.parse("2026-10-18T23:59:59.900Z"));
        log.log("enter", PLAYER, e -> {});
        clock.set(Instant.parse("2026-10-19T00:00:00.100Z"));
        log.log("exit", PLAYER, e -> {});

        assertEquals(List.of("enter"), eventNames(folder.resolve("2026-10-18.jsonl")));
        assertEquals(List.of("exit"), eventNames(folder.resolve("2026-10-19.jsonl")));
    }

    @Test
    void textWithQuotesAndLineBreaksStaysOnOneLine() throws IOException {
        FlowLog log = openLog(90);

        log.log("enter", PLAYER, e -> e.addProperty("brand", "my \"client\"\nline two"));

        List<String> lines = Files.readAllLines(folder.resolve("2026-10-18.jsonl"));
        assertEquals(1, lines.size());
        assertEquals("my \"client\"\nline two", FlowLogFiles.read(folder).getFirst().get("brand").getAsString());
    }

    @Test
    void aMissingTextIsWrittenAsNull() {
        FlowLog log = openLog(90);

        log.log("enter", PLAYER, e -> e.addProperty("brand", (String) null));

        JsonObject event = FlowLogFiles.read(folder).getFirst();
        assertTrue(event.has("brand"), "the field is there");
        assertTrue(event.get("brand").isJsonNull());
    }

    @Test
    void daysOlderThanKeepDaysAreDeleted() throws IOException {
        Files.writeString(folder.resolve("2026-07-19.jsonl"), "{}\n");  // 91 days before 2026-10-18
        Files.writeString(folder.resolve("2026-07-20.jsonl"), "{}\n");  // 90 days before
        Files.writeString(folder.resolve("notes.txt"), "not ours");
        FlowLog log = openLog(90);

        log.log("start", null, e -> {});

        assertFalse(Files.exists(folder.resolve("2026-07-19.jsonl")), "91 days old");
        assertTrue(Files.exists(folder.resolve("2026-07-20.jsonl")), "90 days old");
        assertTrue(Files.exists(folder.resolve("notes.txt")), "a file the log did not write");
    }

    @Test
    void oldDaysAreAlsoDeletedWhenTheDayChanges() throws IOException {
        FlowLog log = openLog(90);
        log.log("start", null, e -> {});
        Files.writeString(folder.resolve("2026-07-20.jsonl"), "{}\n");

        clock.set(Instant.parse("2026-10-19T00:00:01Z"));
        log.log("enter", PLAYER, e -> {});

        assertFalse(Files.exists(folder.resolve("2026-07-20.jsonl")), "91 days old on the new day");
    }

    @Test
    void anOldDayThatCannotBeDeletedDoesNotStopTheLog() throws IOException {
        // A folder with a day file's name cannot be deleted while it holds something.
        Path stuck = Files.createDirectories(folder.resolve("2026-07-19.jsonl"));
        Files.writeString(stuck.resolve("inside"), "x");
        Files.writeString(folder.resolve("2026-07-18.jsonl"), "{}\n");
        FlowLog log = openLog(90);

        log.log("enter", PLAYER, e -> {});
        log.log("exit", PLAYER, e -> {});

        Path today = folder.resolve("2026-10-18.jsonl");
        assertTrue(Files.exists(today), "today's file was written");
        assertEquals(List.of("enter", "exit"), eventNames(today));
        assertFalse(Files.exists(folder.resolve("2026-07-18.jsonl")), "the other old day is deleted");
    }

    @Test
    void keepDaysZeroKeepsEveryDay() throws IOException {
        Files.writeString(folder.resolve("2020-01-01.jsonl"), "{}\n");
        FlowLog log = openLog(0);

        log.log("start", null, e -> {});

        assertTrue(Files.exists(folder.resolve("2020-01-01.jsonl")));
    }

    @Test
    void anEventThatFailsToBuildIsDroppedWithoutReachingTheCaller() {
        FlowLog log = openLog(90);

        assertDoesNotThrow(() -> log.log("enter", PLAYER, e -> { throw new IllegalStateException("no client"); }));
        log.log("exit", PLAYER, e -> {});

        assertEquals(List.of("exit"), eventNames(folder.resolve("2026-10-18.jsonl")));
    }

    @Test
    void eventsAfterCloseAreDropped() {
        FlowLog log = openLog(90);
        log.close();

        assertDoesNotThrow(() -> log.log("enter", PLAYER, e -> {}));

        assertTrue(FlowLogFiles.read(folder).isEmpty());
    }

    @Test
    void aDisabledLogWritesNothing() {
        FlowLog log = FlowLog.disabled();

        log.log("enter", PLAYER, e -> {});

        assertTrue(FlowLogFiles.read(folder).isEmpty());
    }

    private static List<String> eventNames(Path file) {
        return FlowLogFiles.readFile(file).stream().map(e -> e.get("event").getAsString()).toList();
    }

    private static final class SettableClock extends Clock {

        private Instant now;

        SettableClock(Instant now) {
            this.now = now;
        }

        void set(Instant now) {
            this.now = now;
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }
    }
}
