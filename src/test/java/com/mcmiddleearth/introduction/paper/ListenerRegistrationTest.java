package com.mcmiddleearth.introduction.paper;

import com.mcmiddleearth.introduction.paper.listener.ConfirmPreLoginListener;
import com.mcmiddleearth.introduction.paper.listener.GlowListener;
import com.mcmiddleearth.introduction.paper.listener.RoomListener;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredListener;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ListenerRegistrationTest extends PluginTestBase {

    @Test
    void eachListenerIsRegisteredOnceAtStartup() {
        assertEquals(1, registeredInstancesOf(RoomListener.class), "RoomListener");
        assertEquals(1, registeredInstancesOf(GlowListener.class), "GlowListener");
        assertEquals(1, registeredInstancesOf(ConfirmPreLoginListener.class), "ConfirmPreLoginListener");
    }

    private long registeredInstancesOf(Class<? extends Listener> type) {
        return HandlerList.getRegisteredListeners(plugin).stream()
                .map(RegisteredListener::getListener)
                .distinct()
                .filter(type::isInstance)
                .count();
    }
}
