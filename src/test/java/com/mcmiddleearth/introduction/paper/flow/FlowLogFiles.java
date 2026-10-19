package com.mcmiddleearth.introduction.paper.flow;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Reads flow log files back the way a reader such as hub-flow would: every day's file in date
 * order, one JSON object per line.
 */
public final class FlowLogFiles {

    private FlowLogFiles() {
    }

    /** All events in the folder, oldest day first; none when the folder does not exist. */
    public static List<JsonObject> read(Path folder) {
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(file -> file.getFileName().toString().endsWith(".jsonl"))
                    .sorted()
                    .flatMap(file -> readFile(file).stream())
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static List<JsonObject> readFile(Path file) {
        try {
            return Files.readAllLines(file).stream()
                    .map(line -> JsonParser.parseString(line).getAsJsonObject())
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
