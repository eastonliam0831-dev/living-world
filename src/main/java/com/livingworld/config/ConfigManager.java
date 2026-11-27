package com.livingworld.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.livingworld.LivingWorldClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static boolean dirty;

    private ConfigManager() {}

    private static Path dir() { return FabricLoader.getInstance().getConfigDir().resolve("livingworld"); }
    private static Path profilesDir() { return dir().resolve("profiles"); }

    public static void markDirty() { dirty = true; }
    public static boolean isDirty() { return dirty; }

    public static String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("format", 1);
        JsonObject s = new JsonObject();
        for (Setting st : Settings.ALL.values()) s.addProperty(st.id, st.get());
        root.add("settings", s);
        return GSON.toJson(root);
    }

    /** Applies settings from JSON; unknown keys are ignored. Returns false if the text is not valid. */
    public static boolean applyJson(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject s = root.getAsJsonObject("settings");
            if (s == null) return false;
            for (Map.Entry<String, JsonElement> e : s.entrySet()) {
                Setting st = Settings.ALL.get(e.getKey());
                if (st != null) st.set(e.getValue().getAsFloat());
            }
            markDirty();
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public static void load() {
        Path f = dir().resolve("config.json");
        try {
            if (Files.exists(f)) applyJson(Files.readString(f));
            dirty = false;
        } catch (IOException e) {
            LivingWorldClient.LOGGER.warn("Could not read config", e);
        }
    }

    public static void save() {
        try {
            Files.createDirectories(dir());
            Files.writeString(dir().resolve("config.json"), toJson());
            dirty = false;
        } catch (IOException e) {
            LivingWorldClient.LOGGER.warn("Could not write config", e);
        }
    }

    public static String sanitize(String name) {
        String n = name == null ? "" : name.replaceAll("[^A-Za-z0-9 _-]", "").trim();
        return n.length() > 40 ? n.substring(0, 40) : n;
    }

    public static List<String> profiles() {
        List<String> out = new ArrayList<>();
        try {
            if (Files.isDirectory(profilesDir())) {
                try (Stream<Path> s = Files.list(profilesDir())) {
                    s.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".json"))
                            .forEach(n -> out.add(n.substring(0, n.length() - 5)));
                }
            }
        } catch (IOException e) {
            LivingWorldClient.LOGGER.warn("Could not list profiles", e);
        }
        Collections.sort(out);
        return out;
    }

    public static boolean saveProfile(String name) {
        String n = sanitize(name);
        if (n.isEmpty()) return false;
        try {
            Files.createDirectories(profilesDir());
            Files.writeString(profilesDir().resolve(n + ".json"), toJson());
            return true;
        } catch (IOException e) {
            LivingWorldClient.LOGGER.warn("Could not save profile", e);
            return false;
        }
    }

    public static boolean loadProfile(String name) {
        try {
            return applyJson(Files.readString(profilesDir().resolve(sanitize(name) + ".json")));
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean deleteProfile(String name) {
        try {
            return Files.deleteIfExists(profilesDir().resolve(sanitize(name) + ".json"));
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean copyProfile(String from, String to) {
        String t = sanitize(to);
        if (t.isEmpty()) return false;
        try {
            Files.copy(profilesDir().resolve(sanitize(from) + ".json"), profilesDir().resolve(t + ".json"),
                    StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
