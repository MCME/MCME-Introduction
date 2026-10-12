package com.mcmiddleearth.introduction.paper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The plugin registers advancements for the first room and for each tip of the introduction chain.
 * Paper keeps a registered advancement until the server stops and refuses to register its key
 * twice, so neither /intro reload nor a start after a crash may try to register one again.
 */
class AdvancementLifecycleTest extends PluginTestBase {

    private static final String FIRST_ROOM = "mcme:intro_first";
    private static final String FIRST_TIP = "mcme:introchain0";

    @Test
    void introReloadRunsNoFullDataReload() {
        runIntroReload();

        assertEquals(0, advancements().dataReloads(), "full data reloads");
    }

    @Test
    void advancementsStayRegisteredAcrossIntroReload() {
        runIntroReload();

        assertTrue(advancements().isRegistered(FIRST_ROOM));
        assertTrue(advancements().isRegistered(FIRST_TIP));
        assertTrue(advancements().hasFile(FIRST_ROOM), "a later /minecraft:reload keeps only advancements with a file");
        assertTrue(advancements().hasFile(FIRST_TIP), "a later /minecraft:reload keeps only advancements with a file");
    }

    @Test
    void advancementsLeftByACrashDoNotStopThePluginFromStarting() {
        restartAfterCrash(FIRST_ROOM, FIRST_TIP);

        assertTrue(plugin.isEnabled());
    }

    @Test
    void aCleanShutdownDeletesTheAdvancementFiles() {
        // So the next start registers them anew, with the texts config.yml has by then.
        PluginTestBase.AdvancementServerMock advancements = advancements();

        server.getPluginManager().disablePlugin(plugin);

        assertFalse(advancements.hasFile(FIRST_ROOM));
        assertFalse(advancements.hasFile(FIRST_TIP));
    }

    private void runIntroReload() {
        // MockBukkit registers Brigadier commands at the first dispatch.
        server.dispatchCommand(server.getConsoleSender(), "help");
        assertTrue(server.dispatchCommand(server.getConsoleSender(), "intro reload"), "/intro reload ran");
        // The reload loads the data again 20 ticks later.
        server.getScheduler().performTicks(25);
        assertTrue(plugin.isEnabled());
    }
}
