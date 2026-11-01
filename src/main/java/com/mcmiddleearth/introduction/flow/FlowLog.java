package com.mcmiddleearth.introduction.flow;

import com.google.gson.JsonObject;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

/**
 * The flow log: one JSON object per line for each step a player takes through the rooms, in a
 * file per UTC day, flow/YYYY-MM-DD.jsonl in the plugin's folder. The backend's FlowEvents and
 * the proxy's VersionCheck say which events there are; docs/flow-log.md in the repository
 * describes them for readers such as hub-flow. It uses nothing of Paper or Velocity, so both sides
 * of the plugin write their log with it.
 * <p>
 * On the backend {@link #log} is called on the server thread, which builds the line: player state
 * is only safe to read there. On the proxy it is called from several threads at once. One
 * background thread writes the lines, in the order of their times, so neither waits for the disk.
 * Each line is appended on its own, so a reader can read a file at any time; only a last line still
 * being written can be incomplete.
 */
public final class FlowLog {

    private static final Pattern DAY_FILE = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})\\.jsonl");
    // Lines that may wait for the disk at once.
    static final int MAX_WAITING = 10_000;

    private final Path folder;
    private final int keepDays;
    private final Clock clock;
    private final ExecutorService writer;
    // Where a problem is reported: the server's or the proxy's log.
    private final Consumer<String> warn;
    private volatile boolean closed;
    // Whether events are being dropped because too many wait to be written; reported once per spell.
    private volatile boolean dropping;

    // Used by the writer thread only.
    private LocalDate cleanedUpOn;
    private boolean writeFailing;

    FlowLog(Path folder, int keepDays, Clock clock, ExecutorService writer, Consumer<String> warn) {
        this.folder = folder;
        this.keepDays = keepDays;
        this.clock = clock;
        this.writer = writer;
        this.warn = warn;
    }

    private FlowLog() {
        this(null, 0, null, null, null);
        closed = true;
    }

    /**
     * The log as config.yml sets it, with flowLog.enabled and flowLog.keepDays.
     *
     * @param folder   the folder for the day files
     * @param keepDays days to keep; 0 keeps them all
     * @param warn     where problems are reported
     */
    public static FlowLog open(Path folder, boolean enabled, int keepDays, Consumer<String> warn) {
        if(!enabled) {
            return disabled();
        }
        // At most MAX_WAITING lines wait for the disk: a flood of events, such as refused logins at
        // the proxy, can't fill the memory. Beyond it, events are dropped and that is reported.
        ExecutorService writer = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(MAX_WAITING), task -> {
                    Thread thread = new Thread(task, "MCME-Introduction flow log");
                    thread.setDaemon(true);
                    return thread;
                });
        return new FlowLog(folder, keepDays, Clock.systemUTC(), writer, warn);
    }

    /**
     * A log that drops every event.
     */
    public static FlowLog disabled() {
        return new FlowLog();
    }

    /**
     * Records an event. A failure, in building the event or in writing it, never reaches the
     * caller: the rooms work the same with or without the log.
     *
     * @param event  the event's name
     * @param player the player it is about; null for an event about the server
     * @param fields adds the event's own fields
     */
    public void log(String event, @Nullable UUID player, Consumer<JsonObject> fields) {
        if(closed) {
            return;
        }
        // The time is taken and the line queued in one step, so lines from several threads (the
        // proxy's logins) are written in the order of their times.
        synchronized(this) {
            Instant time = clock.instant().truncatedTo(ChronoUnit.MILLIS);
            String line;
            try {
                JsonObject json = new JsonObject();
                json.addProperty("time", time.toString());
                json.addProperty("event", event);
                if(player != null) {
                    json.addProperty("uuid", player.toString());
                }
                fields.accept(json);
                line = json.toString();
            } catch (RuntimeException e) {
                warn.accept("Flow log: could not record " + event + ": " + e);
                return;
            }
            LocalDate day = LocalDate.ofInstant(time, ZoneOffset.UTC);
            try {
                writer.execute(() -> write(day, line));
                dropping = false;
            } catch (RejectedExecutionException e) {
                if(!closed && !dropping) {
                    warn.accept("Flow log: too many events are waiting to be written; dropping events until it catches up.");
                }
                dropping = !closed;
            }
        }
    }

    /**
     * Writes the events still waiting and stops the writer. Events logged after this are dropped.
     */
    public void close() {
        if(closed) {
            return;
        }
        closed = true;
        writer.shutdown();
        try {
            if(!writer.awaitTermination(5, TimeUnit.SECONDS)) {
                warn.accept("Flow log: some events were not written in time and are lost.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void write(LocalDate day, String line) {
        // Once a day, and before the event, which is written whether the clean-up worked or not.
        if(!day.equals(cleanedUpOn)) {
            cleanedUpOn = day;
            deleteOldDays(day);
        }
        try {
            Files.createDirectories(folder);
            Files.writeString(folder.resolve(day + ".jsonl"), line + "\n", StandardCharsets.UTF_8, CREATE, APPEND);
            writeFailing = false;
        } catch (IOException | RuntimeException e) {
            // Once per failing spell, not once per event.
            if(!writeFailing) {
                warn.accept("Flow log: could not write to " + folder + ": " + e);
            }
            writeFailing = true;
        }
    }

    private void deleteOldDays(LocalDate today) {
        if(keepDays <= 0 || !Files.isDirectory(folder)) {
            return;
        }
        LocalDate oldestKept = today.minusDays(keepDays);
        try (DirectoryStream<Path> files = Files.newDirectoryStream(folder, "*.jsonl")) {
            for(Path file : files) {
                Matcher name = DAY_FILE.matcher(file.getFileName().toString());
                if(name.matches() && isBefore(name.group(1), oldestKept)) {
                    delete(file);
                }
            }
        } catch (IOException | RuntimeException e) {
            warn.accept("Flow log: could not look for old days in " + folder + ": " + e);
        }
    }

    // A file a reader holds open, for one, cannot be deleted on every system: it waits for the next day.
    private void delete(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            warn.accept("Flow log: could not delete " + file.getFileName() + ": " + e);
        }
    }

    private static boolean isBefore(String day, LocalDate oldestKept) {
        try {
            return LocalDate.parse(day).isBefore(oldestKept);
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
