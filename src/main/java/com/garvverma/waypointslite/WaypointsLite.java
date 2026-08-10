package com.garvverma.waypointslite;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public class WaypointsLite implements ModInitializer {

    @Override
    public void onInitialize() {
        WaypointManager.init(FabricLoader.getInstance().getConfigDir().resolve("waypointslite"));
        WaypointCommand.register();
    }
}
