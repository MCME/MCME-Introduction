package com.mcmiddleearth.introduction.velocity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The proxy's flow log follows the same flowLog settings as the backends, from the same
 * config.yml, and is on, kept 90 days, whenever they can't be read.
 */
class FlowLogSettingsTest {

    @TempDir
    Path dir;

    @Test
    void theSettingsComeFromTheConfig() throws IOException {
        Path config = Files.writeString(dir.resolve("config.yml"), """
                flowLog:
                  enabled: false
                  keepDays: 30
                """);

        assertEquals(new FlowLogSettings(false, 30), FlowLogSettings.read(config));
    }

    @Test
    void missingSettingsAreTheShippedOnes() throws IOException {
        Path config = Files.writeString(dir.resolve("config.yml"), "supportedVersions: ['26.2']\n");

        assertEquals(new FlowLogSettings(true, 90), FlowLogSettings.read(config));
    }

    @Test
    void aFileThatCantBeReadGivesTheShippedSettings() throws IOException {
        assertEquals(new FlowLogSettings(true, 90), FlowLogSettings.read(dir.resolve("missing.yml")));
        Path broken = Files.writeString(dir.resolve("broken.yml"), "flowLog: [\n");
        assertEquals(new FlowLogSettings(true, 90), FlowLogSettings.read(broken));
        Path notANumber = Files.writeString(dir.resolve("nan.yml"), "flowLog:\n  keepDays: lots\n");
        assertEquals(new FlowLogSettings(true, 90), FlowLogSettings.read(notANumber));
    }
}
