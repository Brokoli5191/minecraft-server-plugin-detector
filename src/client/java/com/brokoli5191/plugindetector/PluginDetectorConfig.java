package com.brokoli5191.plugindetector;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PluginDetectorConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("server-plugin-detector.json");

    private static boolean enabled = true;
    private static int detectionDelaySeconds = 5;

    private PluginDetectorConfig() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static int detectionDelaySeconds() {
        return detectionDelaySeconds;
    }

    public static void cycleDetectionDelay() {
        detectionDelaySeconds = switch (detectionDelaySeconds) {
            case 0 -> 3;
            case 3 -> 5;
            case 5 -> 10;
            case 10 -> 15;
            default -> 0;
        };
        save();
    }

    public static void setEnabled(boolean enabled) {
        PluginDetectorConfig.enabled = enabled;
        save();
    }

    public static void toggleEnabled() {
        setEnabled(!enabled);
    }

    public static void load() {
        if (!Files.exists(CONFIG_PATH)) {
            save();
            return;
        }

        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            Data data = GSON.fromJson(reader, Data.class);
            enabled = data == null || data.enabled;
            detectionDelaySeconds = data == null ? 5 : clamp(data.detectionDelaySeconds, 0, 30);
        } catch (IOException exception) {
            PluginDetector.logger().warn("Failed to load config, using defaults", exception);
            enabled = true;
            detectionDelaySeconds = 5;
        }
    }

    private static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());

            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(new Data(enabled, detectionDelaySeconds), writer);
            }
        } catch (IOException exception) {
            PluginDetector.logger().warn("Failed to save config", exception);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Data(boolean enabled, int detectionDelaySeconds) {
    }
}
