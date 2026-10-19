package com.mcmiddleearth.introduction.paper.rooms;

import com.mcmiddleearth.architect.serverResoucePack.RpPlayerData;
import com.mcmiddleearth.introduction.paper.IntroductionChain;
import com.mcmiddleearth.introduction.paper.flow.FlowEvents.Exit;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * After the rooms a player gets a sequence of tips, as advancement toasts. They belong after the
 * rooms: a player who enters a room again gets none while inside.
 */
class RoomTipsTest extends RoomTestBase {

    // The default config.yml shows the first tip 600 ticks after the start, then one every 600.
    private static final int FIRST_TIP = 600;
    private static final int TIP_PERIOD = 600;

    private Function<Player, RpPlayerData> realPackStatus;
    private final List<String> tipsShown = new ArrayList<>();
    private PlayerMock player;

    @BeforeEach
    void setUp() throws IOException {
        realPackStatus = Room.rpPlayerData;
        Room.rpPlayerData = p -> new RpPlayerData();
        player = new PlayerMock(server, "Newcomer") {
            @Override
            public String getClientBrandName() {
                return "vanilla";
            }

            // MockBukkit does not implement advancement progress; record the tips shown instead.
            @Override
            public AdvancementProgress getAdvancementProgress(Advancement advancement) {
                AdvancementProgress progress = Mockito.mock(AdvancementProgress.class);
                Mockito.when(progress.awardCriteria(Mockito.anyString())).thenAnswer(award -> {
                    String key = advancement.getKey().asString();
                    if (key.startsWith("mcme:introchain")) {
                        tipsShown.add(key);
                    }
                    return true;
                });
                return progress;
            }
        };
        server.addPlayer(player);
        // A player who finished the intro before: starting the tips sends no network broadcast,
        // which would need MCME-Connect.
        Files.writeString(new File(plugin.getDataFolder(), "finishedPlayerList.uid").toPath(),
                player.getUniqueId() + System.lineSeparator());
        plugin.unloadData();
        plugin.loadData();
    }

    @AfterEach
    void restorePackStatus() {
        Room.rpPlayerData = realPackStatus;
    }

    @Test
    void enteringARoomStopsTheTips() {
        IntroductionChain.startChain(player);
        server.getScheduler().performTicks(FIRST_TIP + 10);
        assertEquals(List.of("mcme:introchain0"), tipsShown, "tips before entering a room");

        plugin.getFirst().handleEnter(player);
        server.getScheduler().performTicks(TIP_PERIOD * 3);

        assertEquals(List.of("mcme:introchain0"), tipsShown, "tips after entering a room");
        plugin.getFirst().handleExit(player, false, Exit.LEFT);
    }

    @Test
    void loadingTheTipsAgainShowsEachTipOnce() {
        IntroductionChain.load();

        IntroductionChain.startChain(player);
        server.getScheduler().performTicks(FIRST_TIP + TIP_PERIOD * 2L * tipCount());

        assertEquals(allTips(), tipsShown);
    }

    @Test
    void theTipsCarryOnAfterAReloadInTheirTurn() {
        IntroductionChain.startChain(player);

        // The first tip's turn comes while the plugin is reloading.
        server.getScheduler().performTicks(FIRST_TIP - 10);
        reload();
        server.getScheduler().performTicks(FIRST_TIP + TIP_PERIOD * 2L * tipCount());

        assertEquals(allTips(), tipsShown);
    }

    @Test
    void theTipsCarryOnAfterAReloadWhileOneIsShown() {
        IntroductionChain.startChain(player);

        // A tip is taken back 200 ticks after it is shown, here while the plugin is reloading.
        server.getScheduler().performTicks(FIRST_TIP + 190);
        reload();
        server.getScheduler().performTicks(FIRST_TIP + TIP_PERIOD * 2L * tipCount());

        assertEquals(allTips(), tipsShown);
    }

    /** What /intro reload does: the data is loaded again 20 ticks after it was taken down. */
    private void reload() {
        plugin.unloadData();
        server.getScheduler().performTicks(20);
        plugin.loadData();
    }

    private int tipCount() {
        return plugin.getConfig().getStringList("introductionChain.advancementDisplays").size();
    }

    /** Each tip's advancement key, in order. */
    private List<String> allTips() {
        return IntStream.range(0, tipCount()).mapToObj(i -> "mcme:introchain" + i).toList();
    }
}
