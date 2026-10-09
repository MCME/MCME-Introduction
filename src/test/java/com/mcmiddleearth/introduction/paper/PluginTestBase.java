package com.mcmiddleearth.introduction.paper;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.util.UnsafeValuesMock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * Starts a MockBukkit server with the plugin loaded, for tests that need the whole plugin.
 * <p>
 * MockBukkit cannot run this plugin unaided, so three things are filled in here:
 * <ul>
 *   <li>ProtocolLib is not running, so {@link ProtocolLibrary#getProtocolManager()} returns a
 *       mock manager that accepts the chat packet listener.</li>
 *   <li>MockBukkit does not implement loading advancements from JSON, which the rooms and the
 *       introduction chain do at startup. The server hands out mock advancements instead.</li>
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
        server = MockBukkit.mock(new AdvancementServerMock());
        protocolLibrary = Mockito.mockStatic(ProtocolLibrary.class);
        protocolLibrary.when(ProtocolLibrary::getProtocolManager).thenReturn(Mockito.mock(ProtocolManager.class));
        plugin = MockBukkit.load(IntroductionPlugin.class);
    }

    @AfterEach
    protected void stopServer() {
        try {
            MockBukkit.unmock();
        } finally {
            protocolLibrary.close();
        }
    }

    private static final class AdvancementServerMock extends ServerMock {

        private final UnsafeValuesMock unsafe = new UnsafeValuesMock() {
            @Override
            public Advancement loadAdvancement(NamespacedKey key, String advancement) {
                return Mockito.mock(Advancement.class);
            }

            @Override
            public boolean removeAdvancement(NamespacedKey key) {
                return true;
            }
        };

        @Override
        public UnsafeValuesMock getUnsafe() {
            return unsafe;
        }
    }
}
