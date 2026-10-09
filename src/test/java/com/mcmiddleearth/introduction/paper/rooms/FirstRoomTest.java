package com.mcmiddleearth.introduction.paper.rooms;

import com.mcmiddleearth.introduction.paper.PluginTestBase;
import org.bukkit.Location;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FirstRoomTest extends PluginTestBase {

    @Test
    void welcomeTextIsNotSavedWithTheWorld() {
        TextDisplay display = spawnWelcomeText();

        verify(display).setPersistent(false);
    }

    @Test
    void welcomeTextFacesThePlayer() {
        TextDisplay display = spawnWelcomeText();

        verify(display).setBillboard(Display.Billboard.CENTER);
    }

    private TextDisplay spawnWelcomeText() {
        DisplayRecordingWorld world = new DisplayRecordingWorld("intro");
        server.addWorld(world);
        YamlConfiguration locations = new YamlConfiguration();
        locations.set("world", "intro");
        locations.set("displayEntity.pos", "1 2 3");
        locations.set("displayEntity.text", "{\"text\":\"Welcome\"}");

        new FirstRoom(plugin.getConfig().getConfigurationSection("firstRoom"), locations);

        assertEquals(1, world.displays.size(), "spawned text displays");
        return world.displays.get(0);
    }

    /**
     * MockBukkit cannot spawn text displays, so this world hands out mocks and records them.
     */
    private static final class DisplayRecordingWorld extends WorldMock {

        private final List<TextDisplay> displays = new ArrayList<>();

        DisplayRecordingWorld(String name) {
            super(new WorldCreator(name));
        }

        @Override
        public <T extends Entity> T spawn(Location location, Class<T> clazz) {
            return spawn(location, clazz, entity -> { });
        }

        @Override
        public <T extends Entity> T spawn(Location location, Class<T> clazz, Consumer<? super T> function) {
            if (clazz != TextDisplay.class) {
                return super.spawn(location, clazz, function);
            }
            T display = clazz.cast(mock(TextDisplay.class));
            function.accept(display);
            displays.add((TextDisplay) display);
            return display;
        }
    }
}
