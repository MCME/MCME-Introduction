package com.mcmiddleearth.introduction.paper;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginLoadTest extends PluginTestBase {

    @Test
    void pluginEnablesWithBothRooms() {
        assertTrue(plugin.isEnabled());
        assertNotNull(plugin.getFirst());
        assertNotNull(plugin.getSecond());
    }

    @Test
    void aBrokenLocationsFileIsAnError() throws IOException {
        // A typo turns every room off: the admin must hear of it.
        Files.writeString(new File(plugin.getDataFolder(), "locations.yml").toPath(), "firstRoom: [world\n");

        List<LogRecord> logged = logged(() -> {
            plugin.unloadData();
            plugin.loadData();
        });

        assertTrue(logged.stream().anyMatch(r -> r.getLevel() == Level.SEVERE && r.getMessage().contains("locations.yml")),
                "logged: " + logged.stream().map(LogRecord::getMessage).toList());
    }

    @Test
    void noLocationsFileIsNoError() {
        // A server without rooms, such as every backend but the hub.
        List<LogRecord> logged = logged(() -> {
            plugin.unloadData();
            plugin.loadData();
        });

        assertTrue(logged.stream().noneMatch(r -> r.getLevel().intValue() >= Level.WARNING.intValue()),
                "logged: " + logged.stream().map(LogRecord::getMessage).toList());
    }

    /** What the plugin logs while the action runs. */
    private List<LogRecord> logged(Runnable action) {
        List<LogRecord> records = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        plugin.getLogger().addHandler(handler);
        try {
            action.run();
        } finally {
            plugin.getLogger().removeHandler(handler);
        }
        return records;
    }
}
