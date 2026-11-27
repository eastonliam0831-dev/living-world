package com.livingworld.audio;

import com.livingworld.config.Settings;
import com.livingworld.core.Wind;
import com.livingworld.core.WorldProbe.Area;
import com.livingworld.core.WorldProbe.Snapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Randomised 3D ambient sound layers. Every layer has its own cooldown, never overlaps itself, and picks a
 * random variant from sounds.json. Sound events are client-only (not registered) so no server support is needed.
 */
public final class AmbientSounds {
    private static final Map<String, SoundEvent> EVENTS = new HashMap<>();
    private static final Map<String, Integer> COOLDOWN = new HashMap<>();
    private static final Map<String, SoundInstance> LAST = new HashMap<>();
    private static final List<double[]> PENDING_THUNDER = new ArrayList<>(); // {ticksLeft, distance}
    private static final Random R = new Random();

    public static int played;
    public static String profile = "-";

    private AmbientSounds() {}

    private static SoundEvent ev(String key) {
        return EVENTS.computeIfAbsent(key, k ->
                SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("livingworld", k)));
    }

    public static void reset() {
        COOLDOWN.clear(); LAST.clear(); PENDING_THUNDER.clear(); profile = "-";
    }

    /** Called by StormEffects when lightning strikes at a distance. */
    public static void queueThunder(double distance) {
        if (!Settings.SND_THUNDER.on() || !Settings.THUNDER_DELAY.on()) return;
        PENDING_THUNDER.add(new double[]{Math.min(160, distance / 3.0), distance});
    }

    private static boolean play(Minecraft mc, String layer, String event, float vol, double minD, double maxD,
                                double yOff, int interval, float pitchLo, float pitchHi) {
        if (COOLDOWN.getOrDefault(layer, 0) > 0) return false;
        SoundInstance last = LAST.get(layer);
        if (last != null && mc.getSoundManager().isActive(last)) return false;
        float dist = Settings.SOUND_DIST.get();
        float v = vol * Settings.SOUND_MASTER.get();
        if (v <= 0.01f) return false;
        double a = R.nextDouble() * Math.PI * 2;
        double d = (minD + R.nextDouble() * (maxD - minD)) * dist;
        double x = mc.player.getX() + Math.cos(a) * d, z = mc.player.getZ() + Math.sin(a) * d;
        double y = mc.player.getY() + yOff;
        float pitch = pitchLo + R.nextFloat() * (pitchHi - pitchLo);
        SimpleSoundInstance inst = new SimpleSoundInstance(ev(event), SoundSource.AMBIENT, v * dist, pitch,
                RandomSource.create(), x, y, z);
        mc.getSoundManager().play(inst);
        LAST.put(layer, inst);
        float f = Math.max(0.1f, Settings.SOUND_FREQ.get());
        COOLDOWN.put(layer, (int) (interval / f * (0.7 + R.nextDouble() * 0.8)));
        played++;
        return true;
    }

    public static void tick(Minecraft mc, Snapshot s, int dt) {
        for (Iterator<Map.Entry<String, Integer>> it = COOLDOWN.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Integer> e = it.next();
            int left = e.getValue() - dt;
            if (left <= 0) it.remove(); else e.setValue(left);
        }
        for (Iterator<double[]> it = PENDING_THUNDER.iterator(); it.hasNext(); ) {
            double[] p = it.next();
            p[0] -= dt;
            if (p[0] <= 0) {
                it.remove();
                String ev = p[1] > 60 ? "thunder_far" : "thunder_roll";
                float v = (float) Math.max(0.5, 1.6 - p[1] / 120.0) * Settings.SND_THUNDER_VOL.get();
                COOLDOWN.remove("thunder");
                LAST.remove("thunder");
                play(mc, "thunder", ev, v, 14, 22, 8, 20, 0.9f, 1.05f);
            }
        }
        if (s == null || !com.livingworld.core.WorldProbe.areaEnabled(s)) { profile = "-"; return; }

        Area a = s.area();
        boolean day = s.daylight() > 0.25f, night = s.night() > 0.5f;
        float rain = s.rain();

        // Underwater: nothing else plays.
        if (s.underwater()) {
            profile = "Underwater";
            if (Settings.SND_UNDER.on()) {
                float v = Settings.SND_UNDER_VOL.get();
                play(mc, "under", "underwater", 0.55f * v, 0, 2, 0, 140, 0.95f, 1.05f);
                play(mc, "bubbles", "bubbles", 0.45f * v, 0.5f, 3, 0.5, 90, 0.9f, 1.2f);
            }
            return;
        }

        // Caves
        if (s.cave()) {
            profile = s.lush() ? "Lush cave" : "Cave";
            if (Settings.SND_CAVE.on()) {
                float v = Settings.SND_CAVE_VOL.get();
                play(mc, "drip", "drip", 0.6f * v, 4, 12, 1, s.lush() ? 45 : 75, 0.85f, 1.2f);
                play(mc, "rumble", "rumble", 0.5f * v, 8, 16, 0, 700, 0.9f, 1.0f);
            }
            return;
        }

        if (!s.outdoors()) { profile = "Indoors"; return; }

        profile = a.label + (night ? " (night)" : day ? " (day)" : " (dusk)");

        // Wind
        if (Settings.SND_WIND.on() && Settings.WIND_ON.on() && Wind.intensity > 0.12f) {
            float w = Wind.intensity;
            String e = w < 0.45f ? "wind_gentle" : w < 0.95f ? "wind_gust" : "wind_storm";
            float v = Math.max(0.1f, Math.min(1.2f, 0.2f + 0.45f * w)) * Settings.SND_WIND_VOL.get();
            play(mc, "wind", e, v, 7, 14, 1, w > 0.95f ? 90 : 140, 0.92f, 1.06f);
            if (a == Area.DESERT && w > 0.4f)
                play(mc, "sand", "wind_sand", 0.5f * Settings.SND_WIND_VOL.get(), 6, 12, 1, 200, 0.95f, 1.05f);
        }

        // Forest and birds
        if (Settings.SND_FOREST.on()) {
            float v = Settings.SND_FOREST_VOL.get();
            if (a == Area.FOREST || a == Area.JUNGLE)
                play(mc, "leaves", "leaves", 0.5f * v, 5, 12, 2, 200, 0.9f, 1.1f);
            if (day && rain < 0.5f && (a == Area.FOREST || a == Area.JUNGLE || a == Area.PLAINS))
                play(mc, "bird", "bird", 0.45f * v, 10, 22, 4, a == Area.JUNGLE ? 160 : 260, 0.9f, 1.15f);
        }

        // Night
        if (Settings.SND_NIGHT.on() && night && rain < 0.4f) {
            float v = Settings.SND_NIGHT_VOL.get();
            if (a == Area.FOREST || a == Area.PLAINS || a == Area.JUNGLE || a == Area.SWAMP)
                play(mc, "crickets", "crickets", 0.4f * v, 6, 14, 0, 300, 0.95f, 1.05f);
            if (a == Area.FOREST) play(mc, "owl", "owl", 0.5f * v, 14, 24, 5, 900, 0.9f, 1.05f);
            if (a == Area.SWAMP || a == Area.JUNGLE) play(mc, "frog", "frog", 0.5f * v, 8, 16, 0, 400, 0.9f, 1.1f);
        }

        // Water
        if (Settings.SND_WATER.on()) {
            float v = Settings.SND_WATER_VOL.get();
            if (a == Area.OCEAN || a == Area.BEACH)
                play(mc, "wave", "wave", (a == Area.BEACH ? 0.9f : 0.7f) * v, 9, 18, 0, 170, 0.92f, 1.08f);
            else if (a == Area.RIVER)
                play(mc, "river", "river", 0.5f * v, 4, 10, 0, 220, 0.95f, 1.05f);
            else if (a == Area.SWAMP)
                play(mc, "bubbles", "bubbles", 0.35f * v, 4, 10, 0, 260, 0.8f, 1.0f);
        }

        // Thunder rumble layer during storms (separate from lightning-triggered thunder)
        if (Settings.SND_THUNDER.on() && s.thunder() > 0.5f)
            play(mc, "thunder", R.nextBoolean() ? "thunder_far" : "thunder_roll",
                    0.9f * Settings.SND_THUNDER_VOL.get(), 14, 22, 8, 500, 0.9f, 1.05f);
    }
}
