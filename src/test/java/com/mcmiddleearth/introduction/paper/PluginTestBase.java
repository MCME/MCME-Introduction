package com.mcmiddleearth.introduction.paper;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import net.kyori.adventure.key.Key;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.util.UnsafeValuesMock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Starts a MockBukkit server with the plugin loaded, for tests that need the whole plugin.
 * <p>
 * MockBukkit cannot run this plugin unaided, so three things are filled in here:
 * <ul>
 *   <li>ProtocolLib is not running, so {@link ProtocolLibrary#getProtocolManager()} returns a
 *       mock manager that accepts the chat packet listener.</li>
 *   <li>MockBukkit does not implement loading advancements from JSON, which the rooms and the
 *       introduction chain do at startup. {@link AdvancementServerMock} keeps them the way Paper
 *       does.</li>
 *   <li>MockBukkit ignores {@code depend:}, so MCME-Architect, ViaVersion and MCME-Connect are
 *       absent. Tests must not reach code that calls into them.</li>
 * </ul>
 */
public abstract class PluginTestBase {

    protected ServerMock server;
    protected IntroductionPlugin plugin;
    private MockedStatic<ProtocolLibrary> protocolLibrary;

    @BeforeEach
    protected void startServer() {
        start(new AdvancementServerMock());
    }

    @AfterEach
    protected void stopServer() {
        try {
            if (MockBukkit.isMocked()) {
                MockBukkit.unmock();
            }
        } finally {
            if (protocolLibrary != null) {
                protocolLibrary.close();
                protocolLibrary = null;
            }
        }
    }

    /**
     * Stops the server and starts a new one as after a crash: the plugin had no chance to delete
     * the advancement files it saved, so the server loads those advancements before the plugin
     * starts.
     */
    protected void restartAfterCrash(String... leftoverAdvancements) {
        stopServer();
        start(new AdvancementServerMock(leftoverAdvancements));
    }

    protected AdvancementServerMock advancements() {
        return (AdvancementServerMock) server;
    }

    private void start(AdvancementServerMock serverMock) {
        server = MockBukkit.mock(serverMock);
        protocolLibrary = Mockito.mockStatic(ProtocolLibrary.class);
        protocolLibrary.when(ProtocolLibrary::getProtocolManager).thenReturn(Mockito.mock(ProtocolManager.class));
        plugin = MockBukkit.load(IntroductionPlugin.class);
    }

    /**
     * Keeps advancements the way Paper 26.2 does. Loading one whose key is already registered
     * fails. A loaded advancement is saved as a file when asked to persist; removing an advancement
     * only deletes that file and leaves it registered. A data reload keeps only the advancements
     * that have a file.
     */
    public static final class AdvancementServerMock extends ServerMock {

        private final Map<NamespacedKey, Advancement> registered = new HashMap<>();
        private final Set<NamespacedKey> files = new HashSet<>();
        private int dataReloads;

        private final UnsafeValuesMock unsafe = new UnsafeValuesMock() {
            @Override
            public Advancement loadAdvancement(NamespacedKey key, String advancement) {
                return loadAdvancement(key, advancement, true);
            }

            @Override
            public Advancement loadAdvancement(Key key, String advancement, boolean persist) {
                NamespacedKey id = NamespacedKey.fromString(key.asString());
                if (registered.containsKey(id)) {
                    throw new IllegalArgumentException("Advancement " + id + " already exists");
                }
                registered.put(id, mockAdvancement(id));
                if (persist) {
                    files.add(id);
                }
                return registered.get(id);
            }

            @Override
            public boolean removeAdvancement(NamespacedKey key) {
                return files.remove(key);
            }
        };

        AdvancementServerMock(String... leftoverAdvancements) {
            for (String leftover : leftoverAdvancements) {
                NamespacedKey id = NamespacedKey.fromString(leftover);
                files.add(id);
                registered.put(id, mockAdvancement(id));
            }
        }

        private static Advancement mockAdvancement(NamespacedKey id) {
            Advancement advancement = Mockito.mock(Advancement.class);
            Mockito.when(advancement.getKey()).thenReturn(id);
            return advancement;
        }

        @Override
        public UnsafeValuesMock getUnsafe() {
            return unsafe;
        }

        @Override
        public Advancement getAdvancement(NamespacedKey key) {
            return registered.get(key);
        }

        @Override
        public void reloadData() {
            dataReloads++;
            registered.keySet().retainAll(files);
        }

        public int dataReloads() {
            return dataReloads;
        }

        public boolean isRegistered(String key) {
            return registered.containsKey(NamespacedKey.fromString(key));
        }

        public boolean hasFile(String key) {
            return files.contains(NamespacedKey.fromString(key));
        }
    }
}
