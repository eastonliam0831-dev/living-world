package com.livingworld.config;

import com.livingworld.config.Setting.Kind;
import com.livingworld.config.Setting.Tag;

import java.util.*;

/**
 * Registry of every setting. Rule for this file: a setting is only added when the code that reads it exists.
 */
public final class Settings {
    public static final Map<String, Setting> ALL = new LinkedHashMap<>();
    public static final List<String> CATEGORIES = List.of(
            "Sounds", "Wind", "Weather", "Particles", "Screen Effects", "Seasons", "Biomes", "Performance");

    private static Setting reg(Setting s) { ALL.put(s.id, s); return s; }
    private static Set<Tag> t(Tag... tags) { Set<Tag> s = EnumSet.noneOf(Tag.class); s.addAll(Arrays.asList(tags)); return s; }
    private static Setting toggle(String id, String cat, String label, boolean def, Tag... tags) {
        return reg(new Setting(id, cat, label, Kind.TOGGLE, 0, 1, def ? 1 : 0, 1, null, t(tags)));
    }
    private static Setting slider(String id, String cat, String label, float min, float max, float def, float step, Tag... tags) {
        return reg(new Setting(id, cat, label, Kind.SLIDER, min, max, def, step, null, t(tags)));
    }
    private static Setting choice(String id, String cat, String label, int def, String[] ch, Tag... tags) {
        return reg(new Setting(id, cat, label, Kind.CHOICE, 0, ch.length - 1, def, 1, ch, t(tags)));
    }

    // ---- Dashboard
    public static final Setting MASTER = toggle("master", "Dashboard", "Enable Living World Ultimate", true, Tag.VISUAL, Tag.AUDIO);
    public static final Setting PRESET = choice("preset", "Dashboard", "Quality preset", 2,
            new String[]{"Custom", "Low", "Medium", "High", "Ultra", "Extreme"}, Tag.PERFORMANCE);

    // ---- Sounds
    public static final Setting SOUND_MASTER = slider("sound.master", "Sounds", "Master ambient volume", 0, 1.5f, 1f, 0.05f, Tag.AUDIO);
    public static final Setting SOUND_FREQ = slider("sound.freq", "Sounds", "Sound frequency", 0.25f, 2f, 1f, 0.05f, Tag.AUDIO);
    public static final Setting SOUND_DIST = slider("sound.dist", "Sounds", "Sound distance", 0.5f, 1.5f, 1f, 0.05f, Tag.AUDIO);
    public static final Setting SND_WIND = toggle("sound.wind", "Sounds", "Wind ambience", true, Tag.AUDIO);
    public static final Setting SND_WIND_VOL = slider("sound.wind.vol", "Sounds", "Wind volume", 0, 1.5f, 1f, 0.05f, Tag.AUDIO);
    public static final Setting SND_FOREST = toggle("sound.forest", "Sounds", "Forest and bird ambience", true, Tag.AUDIO);
    public static final Setting SND_FOREST_VOL = slider("sound.forest.vol", "Sounds", "Forest volume", 0, 1.5f, 1f, 0.05f, Tag.AUDIO);
    public static final Setting SND_NIGHT = toggle("sound.night", "Sounds", "Night ambience (crickets, owls, frogs)", true, Tag.AUDIO);
    public static final Setting SND_NIGHT_VOL = slider("sound.night.vol", "Sounds", "Night volume", 0, 1.5f, 1f, 0.05f, Tag.AUDIO);
    public static final Setting SND_WATER = toggle("sound.water", "Sounds", "Ocean, beach, river and swamp water", true, Tag.AUDIO);
    public static final Setting SND_WATER_VOL = slider("sound.water.vol", "Sounds", "Water volume", 0, 1.5f, 1f, 0.05f, Tag.AUDIO);
    public static final Setting SND_CAVE = toggle("sound.cave", "Sounds", "Cave ambience", true, Tag.AUDIO);
    public static final Setting SND_CAVE_VOL = slider("sound.cave.vol", "Sounds", "Cave volume", 0, 1.5f, 1f, 0.05f, Tag.AUDIO);
    public static final Setting SND_THUNDER = toggle("sound.thunder", "Sounds", "Rolling thunder layer", true, Tag.AUDIO);
    public static final Setting SND_THUNDER_VOL = slider("sound.thunder.vol", "Sounds", "Thunder volume", 0, 1.5f, 1f, 0.05f, Tag.AUDIO);
    public static final Setting SND_UNDER = toggle("sound.under", "Sounds", "Underwater ambience", true, Tag.AUDIO);
    public static final Setting SND_UNDER_VOL = slider("sound.under.vol", "Sounds", "Underwater volume", 0, 1.5f, 1f, 0.05f, Tag.AUDIO);

    // ---- Wind
    public static final Setting WIND_ON = toggle("wind.on", "Wind", "Dynamic wind", true, Tag.VISUAL, Tag.AUDIO);
    public static final Setting WIND_STRENGTH = slider("wind.strength", "Wind", "Wind strength", 0, 2f, 1f, 0.05f, Tag.VISUAL, Tag.AUDIO);
    public static final Setting WIND_GUSTS = slider("wind.gusts", "Wind", "Gust amount", 0, 1f, 0.5f, 0.05f, Tag.VISUAL, Tag.AUDIO);
    public static final Setting WIND_STORM = toggle("wind.storm", "Wind", "Storms strengthen wind", true, Tag.VISUAL, Tag.AUDIO);

    // ---- Weather
    public static final Setting RAIN_SPLASH = toggle("rain.splash", "Weather", "Extra rain splashes on ground and water", true, Tag.VISUAL);
    public static final Setting RAIN_SPLASH_DENS = slider("rain.splash.dens", "Weather", "Rain splash density", 0, 2f, 1f, 0.05f, Tag.VISUAL, Tag.PERFORMANCE);
    public static final Setting RAIN_DRIPS = toggle("rain.drips", "Weather", "Water dripping from leaves in rain", true, Tag.VISUAL);
    public static final Setting SNOW_FLURRY = toggle("snow.flurry", "Weather", "Extra wind-driven snowflakes", true, Tag.VISUAL);
    public static final Setting SNOW_FLURRY_DENS = slider("snow.flurry.dens", "Weather", "Snowflake density", 0, 2f, 1f, 0.05f, Tag.VISUAL, Tag.PERFORMANCE);
    public static final Setting THUNDER_DELAY = toggle("thunder.delay", "Weather", "Distance-delayed thunder after lightning", true, Tag.AUDIO);

    // ---- Particles
    public static final Setting PART_MASTER = toggle("part.master", "Particles", "Environmental particles", true, Tag.VISUAL);
    public static final Setting PART_DENSITY = slider("part.density", "Particles", "Particle density", 0, 2f, 1f, 0.05f, Tag.VISUAL, Tag.PERFORMANCE);
    public static final Setting PART_RADIUS = slider("part.radius", "Particles", "Particle radius (blocks)", 6, 32, 20, 1, Tag.VISUAL, Tag.PERFORMANCE);
    public static final Setting PART_MAX = slider("part.max", "Particles", "Max new particles per tick", 1, 40, 12, 1, Tag.VISUAL, Tag.PERFORMANCE);
    public static final Setting P_DUST = toggle("part.dust", "Particles", "Sunlit dust motes", true, Tag.VISUAL);
    public static final Setting P_CAVE_DUST = toggle("part.cavedust", "Particles", "Cave dust", true, Tag.VISUAL);
    public static final Setting P_POLLEN = toggle("part.pollen", "Particles", "Floating pollen", true, Tag.VISUAL);
    public static final Setting P_PETALS = toggle("part.petals", "Particles", "Falling petals (spring)", true, Tag.VISUAL);
    public static final Setting P_LEAVES = toggle("part.leaves", "Particles", "Falling leaves", true, Tag.VISUAL);
    public static final Setting P_FIREFLY = toggle("part.firefly", "Particles", "Night fireflies", true, Tag.VISUAL);
    public static final Setting P_SAND = toggle("part.sand", "Particles", "Wind-blown sand", true, Tag.VISUAL);
    public static final Setting P_SPORES = toggle("part.spores", "Particles", "Lush cave spores", true, Tag.VISUAL);
    public static final Setting P_EMBERS = toggle("part.embers", "Particles", "Campfire embers and ash", true, Tag.VISUAL);
    public static final Setting P_STEPS = toggle("part.steps", "Particles", "Footstep dust and snow puffs", true, Tag.VISUAL);

    // ---- Screen effects
    public static final Setting GRADE_ON = toggle("grade.on", "Screen Effects", "Colour grading", true, Tag.VISUAL);
    public static final Setting GRADE_INT = slider("grade.int", "Screen Effects", "Grading intensity", 0, 2f, 1f, 0.05f, Tag.VISUAL);
    public static final Setting GRADE_NIGHT = toggle("grade.night", "Screen Effects", "Blue night grading", true, Tag.VISUAL);
    public static final Setting GRADE_SUNSET = toggle("grade.sunset", "Screen Effects", "Warm sunrise and sunset grading", true, Tag.VISUAL);
    public static final Setting GRADE_WATER = toggle("grade.water", "Screen Effects", "Underwater tint", true, Tag.VISUAL);
    public static final Setting GRADE_STORM = toggle("grade.storm", "Screen Effects", "Darker storm grading", true, Tag.VISUAL);
    public static final Setting DROPLETS = toggle("screen.droplets", "Screen Effects", "Rain droplets on screen", true, Tag.VISUAL);
    public static final Setting DROPLET_INT = slider("screen.droplets.int", "Screen Effects", "Droplet amount", 0, 2f, 1f, 0.05f, Tag.VISUAL);
    public static final Setting FLASH = toggle("screen.flash", "Screen Effects", "Lightning flash", true, Tag.VISUAL);
    public static final Setting FLASH_INT = slider("screen.flash.int", "Screen Effects", "Lightning flash strength", 0, 2f, 1f, 0.05f, Tag.VISUAL);

    // ---- Seasons
    public static final Setting SEASON = choice("season", "Seasons", "Season", 0,
            new String[]{"Off", "Auto (real date)", "Spring", "Summer", "Autumn", "Winter"}, Tag.VISUAL);
    public static final Setting SEASON_INT = slider("season.int", "Seasons", "Season effect strength", 0, 2f, 1f, 0.05f, Tag.VISUAL);

    // ---- Biomes (gate sounds and particles per environment)
    public static final Setting B_FOREST = toggle("biome.forest", "Biomes", "Forest effects", true, Tag.VISUAL, Tag.AUDIO);
    public static final Setting B_PLAINS = toggle("biome.plains", "Biomes", "Plains effects", true, Tag.VISUAL, Tag.AUDIO);
    public static final Setting B_DESERT = toggle("biome.desert", "Biomes", "Desert and badlands effects", true, Tag.VISUAL, Tag.AUDIO);
    public static final Setting B_JUNGLE = toggle("biome.jungle", "Biomes", "Jungle effects", true, Tag.VISUAL, Tag.AUDIO);
    public static final Setting B_SWAMP = toggle("biome.swamp", "Biomes", "Swamp effects", true, Tag.VISUAL, Tag.AUDIO);
    public static final Setting B_MOUNTAIN = toggle("biome.mountain", "Biomes", "Mountain effects", true, Tag.VISUAL, Tag.AUDIO);
    public static final Setting B_BEACH = toggle("biome.beach", "Biomes", "Beach effects", true, Tag.VISUAL, Tag.AUDIO);
    public static final Setting B_OCEAN = toggle("biome.ocean", "Biomes", "Ocean effects", true, Tag.VISUAL, Tag.AUDIO);
    public static final Setting B_RIVER = toggle("biome.river", "Biomes", "River effects", true, Tag.VISUAL, Tag.AUDIO);
    public static final Setting B_SNOWY = toggle("biome.snowy", "Biomes", "Snowy biome effects", true, Tag.VISUAL, Tag.AUDIO);
    public static final Setting B_CAVE = toggle("biome.cave", "Biomes", "Cave effects", true, Tag.VISUAL, Tag.AUDIO);
    public static final Setting B_LUSH = toggle("biome.lush", "Biomes", "Lush cave effects", true, Tag.VISUAL, Tag.AUDIO);

    // ---- Performance
    public static final Setting PERF_MODE = toggle("perf.mode", "Performance", "Performance-friendly mode (halves effects)", false, Tag.PERFORMANCE);
    public static final Setting PERF_AUTO = toggle("perf.auto", "Performance", "Auto-reduce particles when FPS is low", true, Tag.PERFORMANCE);
    public static final Setting PERF_FPS = slider("perf.fps", "Performance", "Target FPS for auto-reduce", 20, 144, 50, 5, Tag.PERFORMANCE);
    public static final Setting PERF_INTERVAL = slider("perf.interval", "Performance", "Environment update interval (ticks)", 1, 10, 2, 1, Tag.PERFORMANCE);
    public static final Setting PERF_DEBUG = toggle("perf.debug", "Performance", "Debug overlay", false, Tag.PERFORMANCE);

    private Settings() {}

    public static void resetCategory(String category) {
        for (Setting s : ALL.values()) if (s.category.equals(category)) s.reset();
    }
    public static void resetAll() { for (Setting s : ALL.values()) s.reset(); }
}
