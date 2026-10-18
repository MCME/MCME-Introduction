package com.mcmiddleearth.introduction.paper.flow;

import com.google.gson.JsonObject;
import org.bukkit.configuration.ConfigurationSection;

import javax.annotation.Nullable;
import java.io.File;
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
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

/**
 * The flow log: one JSON object per line for each step a player takes through the rooms, in a
 * file per UTC day, flow/YYYY-MM-DD.jsonl in the plugin's folder. {@link FlowEvents} says which
 * events there are; docs/flow-log.md in the repository describes them for readers such as
 * hub-flow.
 * <p>
 * {@link #log} is called on the server thread, which builds the line: player state is only safe
 * to read there. One background thread writes the lines, so the server never waits for the disk.
 * Each line is appended on its own, so a reader can read a file at any time; only a last line
 * still being written can be incomplete.
 */
public final class FlowLog {

    private static final Pattern DAY_FILE = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})\\.jsonl");

    private final Path folder;
    private final int keepDays;
    private final Clock clock;
    private final ExecutorService writer;
    private final Logger logger;
    private volatile boolean closed;

    // Used by the writer thread only.
    private LocalDate cleanedUpOn;
    private boolean writeFailing;

    FlowLog(Path folder, int keepDays, Clock clock, ExecutorService writer, Logger logger) {
        this.folder = folder;
        this.keepDays = keepDays;
        this.clock = clock;
        this.writer = writer;
        this.logger = logger;
    }

    private FlowLog() {
        this(null, 0, null, null, null);
        closed = true;
    }

    /**
     * The log as config.yml sets it: flowLog.enabled and flowLog.keepDays.
     */
    public static FlowLog open(File pluginFolder, ConfigurationSection config, Logger logger) {
        if(!config.getBoolean("flowLog.enabled", true)) {
            return disabled();
        }
        ExecutorService writer = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "MCME-Introduction flow log");
            thread.setDaemon(true);
            return thread;
        });
        return new FlowLog(new File(pluginFolder, "flow").toPath(), config.getInt("flowLog.keepDays", 90),
                           Clock.systemUTC(), writer, logger);
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
            logger.warning("Flow log: could not record " + event + ": " + e);
            return;
        }
        LocalDate day = LocalDate.ofInstant(time, ZoneOffset.UTC);
        try {
            writer.execute(() -> write(day, line));
        } catch (RejectedExecutionException ignore) {
            // Closed meanwhile.
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
                logger.warning("Flow log: some events were not written in time and are lost.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void write(LocalDate day, String line) {
        try {
            Files.createDirectories(folder);
            if(!day.equals(cleanedUpOn)) {
                deleteOldDays(day);
                cleanedUpOn = day;
            }
            Files.writeString(folder.resolve(day + ".jsonl"), line + "\n", StandardCharsets.UTF_8, CREATE, APPEND);
            writeFailing = false;
        } catch (IOException e) {
            // Once per failing spell, not once per event.
            if(!writeFailing) {
                logger.warning("Flow log: could not write to " + folder + ": " + e);
            }
            writeFailing = true;
        }
    }

    private void deleteOldDays(LocalDate today) throws IOException {
        if(keepDays <= 0) {
            return;
        }
        LocalDate oldestKept = today.minusDays(keepDays);
        try (DirectoryStream<Path> files = Files.newDirectoryStream(folder, "*.jsonl")) {
            for(Path file : files) {
                Matcher name = DAY_FILE.matcher(file.getFileName().toString());
                if(name.matches() && isBefore(name.group(1), oldestKept)) {
                    Files.deleteIfExists(file);
                }
            }
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
