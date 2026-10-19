package com.mcmiddleearth.introduction.paper;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * finishedPlayerList.uid lists the players who finished the introduction once, one UUID per line.
 * They get no network-wide broadcast when they finish again. One bad line must not stop the
 * plugin, and adding a player must not put the players already listed at risk.
 */
class FinishedPlayersTest extends PluginTestBase {

    private static final String NL = System.lineSeparator();
    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();

    @Test
    void blankAndBrokenLinesAreSkipped() throws IOException {
        // A blank line in the middle, spaces, and a last line cut off by a crash.
        Files.writeString(file(), first + NL + NL + "  " + second + "  " + NL + "4b6f1c2e-9a3d-4e");

        assertDoesNotThrow(this::reload);

        assertTrue(IntroductionChain.hasFinished(first), "the first player");
        assertTrue(IntroductionChain.hasFinished(second), "the second player");
    }

    @Test
    void aNewFinisherIsAddedAtTheEndOfTheFile() throws IOException {
        String listed = first + NL + "not a player" + NL;
        Files.writeString(file(), listed);
        reload();

        IntroductionChain.addFinishedPlayer(second);

        assertEquals(listed + second + NL, Files.readString(file()));
    }

    @Test
    void aNewFinisherAfterACutLineGetsALineOfTheirOwn() throws IOException {
        Files.writeString(file(), first + NL + "4b6f1c2e-9a3d-4e");
        reload();

        IntroductionChain.addFinishedPlayer(second);
        reload();

        assertTrue(IntroductionChain.hasFinished(second));
    }

    private Path file() {
        return new File(plugin.getDataFolder(), "finishedPlayerList.uid").toPath();
    }

    private void reload() {
        plugin.unloadData();
        plugin.loadData();
    }
}
