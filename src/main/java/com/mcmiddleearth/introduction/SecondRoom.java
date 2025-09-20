package com.mcmiddleearth.introduction;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;

public class SecondRoom extends Room {
    public SecondRoom(Location pos1, Location pos2, Location tpTarget, NamespacedKey... cameraOverlay) {
        super(pos1, pos2, tpTarget, cameraOverlay);
    }
}
