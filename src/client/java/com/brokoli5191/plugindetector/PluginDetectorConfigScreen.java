package com.brokoli5191.plugindetector;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class PluginDetectorConfigScreen extends Screen {
    private final Screen parent;

    public PluginDetectorConfigScreen(Screen parent) {
        super(Component.literal("Server Plugin Detector"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = this.height / 2 - 24;

        this.addRenderableWidget(Button.builder(enabledText(), button -> {
            PluginDetectorConfig.toggleEnabled();
            button.setMessage(enabledText());
        }).bounds(centerX - 100, startY, 200, 20).build());

        this.addRenderableWidget(Button.builder(delayText(), button -> {
            PluginDetectorConfig.cycleDetectionDelay();
            button.setMessage(delayText());
        }).bounds(centerX - 100, startY + 32, 200, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> this.minecraft.setScreen(this.parent))
                .bounds(centerX - 100, startY + 64, 200, 20)
                .build());
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    private static Component enabledText() {
        return Component.literal("Detector: " + (PluginDetectorConfig.isEnabled() ? "Enabled" : "Disabled"));
    }

    private static Component delayText() {
        return Component.literal("Detection delay: " + PluginDetectorConfig.detectionDelaySeconds() + "s");
    }
}
