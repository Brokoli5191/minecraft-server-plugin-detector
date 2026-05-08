package com.brokoli5191.plugindetector;

import net.fabricmc.api.ClientModInitializer;

public final class PluginDetectorClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        PluginDetectorConfig.load();
    }
}
