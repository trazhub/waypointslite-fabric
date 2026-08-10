package com.garvverma.waypointslite;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class WaypointManager {

    private static Path dataFile;
    private static int maxPerPlayer = 25;
    private static Map<String, Object> data = new LinkedHashMap<>();

    public static void init(Path dir) {
        try { Files.createDirectories(dir); } catch (IOException ignored) {}
        dataFile = dir.resolve("waypoints.yml");
        Path configFile = dir.resolve("config.yml");
        loadConfig(configFile);
        load();
    }

    private static void loadConfig(Path configFile) {
        if (!Files.exists(configFile)) {
            try (Writer w = Files.newBufferedWriter(configFile)) {
                w.write("# WaypointsLite Fabric configuration\n");
                w.write("max-per-player: 25\n");
            } catch (IOException ignored) {}
            return;
        }
        try (Reader r = Files.newBufferedReader(configFile)) {
            Yaml yaml = new Yaml();
            Map<?, ?> cfg = yaml.load(r);
            if (cfg != null && cfg.containsKey("max-per-player")) {
                maxPerPlayer = ((Number) cfg.get("max-per-player")).intValue();
            }
        } catch (IOException ignored) {}
    }

    @SuppressWarnings("unchecked")
    private static void load() {
        if (!Files.exists(dataFile)) return;
        try (Reader r = Files.newBufferedReader(dataFile)) {
            Yaml yaml = new Yaml();
            Map<String, Object> loaded = yaml.load(r);
            if (loaded != null) data = loaded;
        } catch (IOException ignored) {}
    }

    private static void save() {
        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        opts.setIndent(2);
        Yaml yaml = new Yaml(opts);
        try (Writer w = Files.newBufferedWriter(dataFile)) {
            yaml.dump(data, w);
        } catch (IOException ignored) {}
    }

    public static int getMaxPerPlayer() { return maxPerPlayer; }

    @SuppressWarnings("unchecked")
    public static Set<String> getWaypointNames(String uuid) {
        Object playerData = data.get(uuid);
        if (playerData instanceof Map<?, ?> map) {
            return new LinkedHashSet<>(((Map<String, Object>) map).keySet());
        }
        return Collections.emptySet();
    }

    @SuppressWarnings("unchecked")
    public static boolean hasWaypoint(String uuid, String name) {
        Object playerData = data.get(uuid);
        if (playerData instanceof Map<?, ?> map) {
            return ((Map<String, Object>) map).containsKey(name.toLowerCase());
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public static Waypoint getWaypoint(String uuid, String name) {
        Object playerData = data.get(uuid);
        if (!(playerData instanceof Map<?, ?> playerMap)) return null;
        Object wpData = ((Map<String, Object>) playerMap).get(name.toLowerCase());
        if (!(wpData instanceof Map<?, ?> wpMap)) return null;
        Map<String, Object> wp = (Map<String, Object>) wpMap;
        return new Waypoint(
            name.toLowerCase(),
            (String) wp.get("world"),
            ((Number) wp.get("x")).doubleValue(),
            ((Number) wp.get("y")).doubleValue(),
            ((Number) wp.get("z")).doubleValue(),
            ((Number) wp.get("yaw")).floatValue(),
            ((Number) wp.get("pitch")).floatValue()
        );
    }

    @SuppressWarnings("unchecked")
    public static boolean setWaypoint(String uuid, String name, String world, double x, double y, double z, float yaw, float pitch) {
        String lower = name.toLowerCase();
        Map<String, Object> playerData = (Map<String, Object>) data.computeIfAbsent(uuid, k -> new LinkedHashMap<>());
        if (!playerData.containsKey(lower) && playerData.size() >= maxPerPlayer) return false;
        Map<String, Object> wp = new LinkedHashMap<>();
        wp.put("world", world);
        wp.put("x", x);
        wp.put("y", y);
        wp.put("z", z);
        wp.put("yaw", (double) yaw);
        wp.put("pitch", (double) pitch);
        playerData.put(lower, wp);
        save();
        return true;
    }

    @SuppressWarnings("unchecked")
    public static boolean deleteWaypoint(String uuid, String name) {
        Object playerData = data.get(uuid);
        if (!(playerData instanceof Map<?, ?> map)) return false;
        boolean removed = ((Map<String, Object>) map).remove(name.toLowerCase()) != null;
        if (removed) save();
        return removed;
    }
}
