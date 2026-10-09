package com.mcmiddleearth.introduction.paper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginLoadTest extends PluginTestBase {

    @Test
    void pluginEnablesWithBothRooms() {
        assertTrue(plugin.isEnabled());
        assertNotNull(plugin.getFirst());
        assertNotNull(plugin.getSecond());
    }
}
