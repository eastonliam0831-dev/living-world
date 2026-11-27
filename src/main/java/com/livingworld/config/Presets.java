package com.livingworld.config;

import java.util.LinkedHashMap;
import java.util.Map;

/** Quality presets and the premade profiles. They only write to settings that exist. */
public final class Presets {
    public static final Map<String, Runnable> PREMADE = new LinkedHashMap<>();

    private static void p(Setting s, float v) { s.set(v); }

    /** index: 1 Low .. 5 Extreme */
    public static void applyQuality(int index) {
        float[][] t = {
            // density, radius, max/tick, sound freq, interval, grade intensity
            {0.35f, 12, 4, 0.6f, 4, 0.6f},
            {0.7f, 16, 8, 0.85f, 3, 0.9f},
            {1.0f, 20, 12, 1.0f, 2, 1.0f},
            {1.4f, 26, 20, 1.2f, 1, 1.2f},
            {2.0f, 32, 40, 1.5f, 1, 1.4f}};
        float[] r = t[Math.max(1, Math.min(5, index)) - 1];
        p(Settings.PART_DENSITY, r[0]); p(Settings.PART_RADIUS, r[1]); p(Settings.PART_MAX, r[2]);
        p(Settings.SOUND_FREQ, r[3]); p(Settings.PERF_INTERVAL, r[4]); p(Settings.GRADE_INT, r[5]);
        boolean low = index == 1;
        for (Setting s : new Setting[]{Settings.P_PETALS, Settings.P_LEAVES, Settings.P_SPORES,
                Settings.P_EMBERS, Settings.P_STEPS, Settings.DROPLETS}) s.set(low ? 0 : 1);
        Settings.RAIN_SPLASH_DENS.set(low ? 0.5f : index >= 4 ? 1.5f : 1f);
        Settings.PRESET.set(index);
    }

    static {
        PREMADE.put("Realistic Nature", () -> { p(Settings.SOUND_MASTER, 1f); p(Settings.WIND_STRENGTH, 1f);
            p(Settings.PART_DENSITY, 1f); p(Settings.GRADE_INT, 0.8f); p(Settings.SEASON, 1); });
        PREMADE.put("Cinematic", () -> { p(Settings.GRADE_INT, 1.5f); p(Settings.PART_DENSITY, 1.2f);
            p(Settings.DROPLETS, 1); p(Settings.FLASH, 1); p(Settings.SOUND_MASTER, 0.8f); });
        PREMADE.put("Cozy Atmosphere", () -> { p(Settings.GRADE_INT, 1.2f); p(Settings.SOUND_MASTER, 0.7f);
            p(Settings.WIND_STRENGTH, 0.6f); p(Settings.P_EMBERS, 1); p(Settings.P_FIREFLY, 1); });
        PREMADE.put("Dark Nights", () -> { p(Settings.GRADE_INT, 1.8f); p(Settings.GRADE_NIGHT, 1);
            p(Settings.SND_NIGHT_VOL, 1.3f); p(Settings.P_FIREFLY, 1); });
        PREMADE.put("Beautiful Sunsets", () -> { p(Settings.GRADE_SUNSET, 1); p(Settings.GRADE_INT, 1.6f);
            p(Settings.P_POLLEN, 1); p(Settings.P_DUST, 1); });
        PREMADE.put("Heavy Storms", () -> { p(Settings.WIND_STRENGTH, 1.6f); p(Settings.WIND_GUSTS, 1f);
            p(Settings.SND_THUNDER_VOL, 1.4f); p(Settings.SND_WIND_VOL, 1.3f); p(Settings.RAIN_SPLASH_DENS, 2f);
            p(Settings.GRADE_STORM, 1); p(Settings.FLASH_INT, 1.5f); p(Settings.DROPLET_INT, 1.5f); });
        PREMADE.put("Forest Immersion", () -> { p(Settings.SND_FOREST_VOL, 1.3f); p(Settings.P_LEAVES, 1);
            p(Settings.P_DUST, 1); p(Settings.PART_DENSITY, 1.3f); p(Settings.RAIN_DRIPS, 1); });
        PREMADE.put("Ultra Graphics", () -> applyQuality(4));
        PREMADE.put("Performance Friendly", () -> { applyQuality(1); p(Settings.PERF_MODE, 1); });
    }

    private Presets() {}
}
