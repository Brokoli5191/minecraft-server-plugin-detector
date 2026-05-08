package com.brokoli5191.plugindetector;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public final class PluginDetector {
    private static final Logger LOGGER = LoggerFactory.getLogger("Server Plugin Detector");
    private static final ScheduledExecutorService DETECTION_EXECUTOR = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "Server Plugin Detector");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicInteger DETECTION_GENERATION = new AtomicInteger();
    private static final Set<String> VANILLA_NAMESPACES = Set.of(
            "minecraft",
            "bukkit",
            "spigot",
            "paper",
            "purpur",
            "fabric",
            "brigadier"
    );
    private static final Map<String, String> KNOWN_COMMANDS = createKnownCommands();

    private static String lastFingerprint = "";

    private PluginDetector() {
    }

    public static Logger logger() {
        return LOGGER;
    }

    public static void analyze(CommandDispatcher<SharedSuggestionProvider> dispatcher) {
        if (!PluginDetectorConfig.isEnabled()) {
            return;
        }

        int generation = DETECTION_GENERATION.incrementAndGet();
        List<String> commandNames = dispatcher.getRoot().getChildren().stream()
                .map(CommandNode::getName)
                .toList();

        DETECTION_EXECUTOR.schedule(
                () -> analyzeDelayed(generation, commandNames),
                PluginDetectorConfig.detectionDelaySeconds(),
                TimeUnit.SECONDS
        );
    }

    private static void analyzeDelayed(int generation, List<String> commandNames) {
        if (!PluginDetectorConfig.isEnabled() || generation != DETECTION_GENERATION.get()) {
            return;
        }

        DetectionResult result = detect(commandNames);
        String fingerprint = result.fingerprint();

        if (fingerprint.equals(lastFingerprint)) {
            return;
        }

        lastFingerprint = fingerprint;
        LOGGER.info("Detected likely server plugins: {}", result.logLine());

        Minecraft.getInstance().execute(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null) {
                return;
            }

            for (String line : result.chatLines()) {
                minecraft.player.sendSystemMessage(Component.literal("[PluginDetector] " + line));
            }
        });
    }

    private static DetectionResult detect(Collection<String> rootCommands) {
        Map<String, Set<String>> pluginEvidence = new TreeMap<>();
        Set<String> exposedNamespaces = new TreeSet<>();

        for (String name : rootCommands) {
            String lowerName = name.toLowerCase(Locale.ROOT);
            int namespaceSeparator = lowerName.indexOf(':');

            if (namespaceSeparator > 0) {
                String namespace = lowerName.substring(0, namespaceSeparator);

                if (!VANILLA_NAMESPACES.contains(namespace)) {
                    exposedNamespaces.add(namespace);
                    pluginEvidence.computeIfAbsent(toDisplayName(namespace), ignored -> new LinkedHashSet<>()).add(name);
                }
            }

            String commandKey = namespaceSeparator > 0 ? lowerName.substring(namespaceSeparator + 1) : lowerName;
            String knownPlugin = name.indexOf(':') > 0 ? KNOWN_COMMANDS.get(commandKey) : KNOWN_COMMANDS.get(lowerName);

            if (knownPlugin != null) {
                pluginEvidence.computeIfAbsent(knownPlugin, ignored -> new LinkedHashSet<>()).add(name);
            }
        }

        List<PluginHit> hits = pluginEvidence.entrySet().stream()
                .map(entry -> new PluginHit(entry.getKey(), sortEvidence(entry.getValue())))
                .sorted(Comparator.comparing(PluginHit::name, String.CASE_INSENSITIVE_ORDER))
                .toList();

        return new DetectionResult(hits, new ArrayList<>(exposedNamespaces));
    }

    private static List<String> sortEvidence(Set<String> evidence) {
        return evidence.stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private static String toDisplayName(String namespace) {
        String[] parts = namespace.split("[_\\-]");
        StringBuilder builder = new StringBuilder();

        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }

            if (!builder.isEmpty()) {
                builder.append(' ');
            }

            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }

        return builder.isEmpty() ? namespace : builder.toString();
    }

    private static Map<String, String> createKnownCommands() {
        Map<String, String> commands = new LinkedHashMap<>();
        add(commands, "LuckPerms", "luckperms", "lp", "perm", "perms", "permission", "permissions");
        add(commands, "WorldEdit", "worldedit", "we", "wand", "pos1", "pos2", "schem", "schematic");
        add(commands, "WorldGuard", "worldguard", "wg", "region", "regions", "rg");
        add(commands, "Essentials", "essentials", "ess", "home", "sethome", "tpa", "tpaccept", "tpdeny", "warp", "warps", "back", "heal", "feed", "fly");
        add(commands, "Vault", "vault", "eco", "economy", "bal", "balance");
        add(commands, "PlaceholderAPI", "placeholderapi", "papi", "parse");
        add(commands, "ViaVersion", "viaversion", "via", "vvbukkit");
        add(commands, "ViaBackwards", "viabackwards");
        add(commands, "ProtocolLib", "protocollib", "protocol", "packet");
        add(commands, "CoreProtect", "coreprotect", "co", "core", "inspect", "lookup", "rollback", "restore");
        add(commands, "GriefPrevention", "griefprevention", "claims", "claim", "trust", "untrust", "accesstrust", "containertrust");
        add(commands, "Lands", "lands", "land", "nations", "nation");
        add(commands, "Towny", "towny", "town", "resident", "plot");
        add(commands, "Factions", "factions", "faction", "f");
        add(commands, "Residence", "residence", "res");
        add(commands, "Citizens", "citizens", "npc", "trait");
        add(commands, "Denizen", "denizen", "ex", "exs", "npcselect");
        add(commands, "MythicMobs", "mythicmobs", "mm", "mob", "mobs");
        add(commands, "Shopkeepers", "shopkeepers", "shopkeeper");
        add(commands, "ChestShop", "chestshop", "iteminfo");
        add(commands, "QuickShop", "quickshop", "qs");
        add(commands, "Multiverse", "multiverse", "mv", "mvtp", "mvcreate", "mvdelete");
        add(commands, "BungeeCord/Velocity", "server", "glist", "alert");
        add(commands, "LiteBans", "litebans", "history", "dupeip");
        add(commands, "AdvancedBan", "advancedban", "aban", "amute", "awarn", "banlist");
        add(commands, "GrimAC", "grim", "grimac", "alerts", "verbose");
        add(commands, "Vulcan", "vulcan");
        add(commands, "Matrix", "matrix");
        add(commands, "Spartan", "spartan");
        add(commands, "NoCheatPlus", "ncp", "nocheatplus");
        add(commands, "AntiCheatReplay", "acreplay", "anticheatreplay");
        return Map.copyOf(commands);
    }

    private static void add(Map<String, String> commands, String plugin, String... aliases) {
        for (String alias : aliases) {
            commands.put(alias, plugin);
        }
    }

    private record DetectionResult(List<PluginHit> hits, List<String> namespaces) {
        String fingerprint() {
            return hits.stream()
                    .map(hit -> hit.name() + ":" + String.join(",", hit.evidence()))
                    .collect(Collectors.joining("|"));
        }

        String chatLine() {
            if (hits.isEmpty()) {
                return "No obvious plugin commands exposed.";
            }

            return "Likely plugins: " + hits.stream()
                    .limit(8)
                    .map(PluginHit::name)
                    .collect(Collectors.joining(", "));
        }

        List<String> chatLines() {
            if (hits.isEmpty()) {
                return List.of("No obvious plugin commands exposed.");
            }

            List<String> lines = new ArrayList<>();
            lines.add("Likely plugins (" + hits.size() + "): " + hits.stream()
                    .map(PluginHit::name)
                    .collect(Collectors.joining(", ")));

            hits.stream()
                    .map(hit -> hit.name() + " via " + String.join(", ", hit.evidence()))
                    .forEach(lines::add);

            return lines;
        }

        String logLine() {
            if (hits.isEmpty()) {
                return "none";
            }

            return hits.stream()
                    .map(hit -> hit.name() + " via " + String.join(", ", hit.evidence()))
                    .collect(Collectors.joining("; "));
        }
    }

    private record PluginHit(String name, List<String> evidence) {
    }
}
