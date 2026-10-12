package com.mcmiddleearth.introduction.paper.rooms;

import com.mcmiddleearth.architect.serverResoucePack.RpPlayerData;
import com.mcmiddleearth.architect.serverResoucePack.RpPlayerStatus;
import com.mcmiddleearth.introduction.paper.PluginTestBase;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A room's camera overlay needs the resource pack, so a room waits for the pack before it shows the
 * overlay and its chat text. When no pack is offered at all, the chat text comes on its own.
 */
class RoomResourcePackTest extends PluginTestBase {

    private Function<Player, RpPlayerData> realPackStatus;
    private final RpPlayerData packStatus = new RpPlayerData();
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        realPackStatus = Room.rpPlayerData;
        Room.rpPlayerData = p -> packStatus;
        // Without its advancement, the first room awards none (MockBukkit cannot show it).
        plugin.getConfig().set("firstRoom.advancementDisplay", null);
        plugin.unloadData();
        plugin.loadData();
        player = server.addPlayer();
    }

    @AfterEach
    void restorePackStatus() {
        Room.rpPlayerData = realPackStatus;
    }

    @Test
    void withoutAResourcePackTheWelcomeTextComesAfterTenSeconds() {
        // A new RpPlayerData reads NOT_SENT: no pack offered.
        plugin.getFirst().handleEnter(player);

        server.getScheduler().performTicks(150);
        assertNull(player.nextComponentMessage(), "no chat text after 7.5 s");

        server.getScheduler().performTicks(100);
        assertWelcomeText(player.nextComponentMessage());

        server.getScheduler().performTicks(400);
        assertNull(player.nextComponentMessage(), "the welcome text comes once");
    }

    @Test
    void withTheResourcePackTheOverlayAndTheWelcomeTextComeTogether() {
        packStatus.setCurrentRpStatus(RpPlayerStatus.SUCCESSFULLY_LOADED);
        plugin.getFirst().handleEnter(player);

        server.getScheduler().performTicks(1);

        assertEquals("minecraft:gui/overlay/welcome_screen",
                player.getInventory().getItem(EquipmentSlot.HEAD).getItemMeta().getEquippable().getCameraOverlay().asString());
        assertWelcomeText(player.nextComponentMessage());
        server.getScheduler().performTicks(400);
        assertNull(player.nextComponentMessage(), "the welcome text comes once");
    }

    private static void assertWelcomeText(Component message) {
        assertNotNull(message, "the welcome text was sent");
        // The default config.yml's messageWelcome links the rules.
        String text = PlainTextComponentSerializer.plainText().serialize(message);
        assertTrue(text.contains("Rules"), text);
    }
}
