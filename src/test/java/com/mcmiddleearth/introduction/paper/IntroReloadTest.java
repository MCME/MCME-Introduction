package com.mcmiddleearth.introduction.paper;

import com.mcmiddleearth.introduction.paper.listener.RoomListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.RegisteredListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * /intro reload takes the plugin's data down and loads it again a second later. It tells the
 * sender how it went, and a second reload in that second must not load everything twice.
 */
class IntroReloadTest extends PluginTestBase {

    // The reload loads the data again 20 ticks later.
    private static final int RELOAD_DELAY = 20;

    @BeforeEach
    void registerCommands() {
        // Paper registers Brigadier commands at the first dispatch.
        server.dispatchCommand(server.getConsoleSender(), "help");
        saidToConsole();
    }

    @Test
    void aReloadTellsTheSenderWhenItStartsAndWhenItIsDone() {
        server.dispatchCommand(server.getConsoleSender(), "intro reload");
        assertTrue(saidToConsole().contains("Reloading"), "said at the start");

        server.getScheduler().performTicks(RELOAD_DELAY + 5);

        assertTrue(saidToConsole().contains("Reload done"), "said at the end");
    }

    @Test
    void aSecondReloadWhileOneIsRunningIsIgnored() {
        server.dispatchCommand(server.getConsoleSender(), "intro reload");
        server.dispatchCommand(server.getConsoleSender(), "intro reload");

        assertTrue(saidToConsole().contains("running already"));
        server.getScheduler().performTicks(RELOAD_DELAY + 5);
        assertEquals(1, HandlerList.getRegisteredListeners(plugin).stream()
                .map(RegisteredListener::getListener)
                .distinct()
                .filter(RoomListener.class::isInstance)
                .count(), "room listeners");
    }

    @Test
    void aReloadThatFailsSaysSoAndALaterOneWorks() throws IOException {
        // A position with two numbers: building the rooms fails.
        File locations = new File(plugin.getDataFolder(), "locations.yml");
        Files.writeString(locations.toPath(), "firstRoom:\n  world: world\n  pos1: 1 2\n");

        server.dispatchCommand(server.getConsoleSender(), "intro reload");
        server.getScheduler().performTicks(RELOAD_DELAY + 5);
        assertTrue(saidToConsole().contains("Reload failed"), "said after the failure");

        Files.delete(locations.toPath());
        server.dispatchCommand(server.getConsoleSender(), "intro reload");
        server.getScheduler().performTicks(RELOAD_DELAY + 5);
        assertTrue(saidToConsole().contains("Reload done"), "said after the second reload");
    }

    /** Everything said to the console since the last call, as plain text. */
    private String saidToConsole() {
        ConsoleCommandSenderMock console = (ConsoleCommandSenderMock) server.getConsoleSender();
        StringBuilder said = new StringBuilder();
        Component message;
        while ((message = console.nextComponentMessage()) != null) {
            said.append(PlainTextComponentSerializer.plainText().serialize(message)).append('\n');
        }
        return said.toString();
    }
}
