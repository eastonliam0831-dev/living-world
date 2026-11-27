package com.livingworld.core;

import com.livingworld.config.Settings;
import com.livingworld.core.WorldProbe.Area;
import com.livingworld.core.WorldProbe.Snapshot;

import java.util.Random;

/** Smooth procedural wind. Drives particle drift and which wind sounds play. */
public final class Wind {
    private static final Random R = new Random();
    private static float t, gust;
    public static float intensity = 0.3f;
    private static float angle;

    private Wind() {}

    public static void tick(Snapshot s, int dt) {
        if (s == null || !Settings.WIND_ON.on()) { intensity = 0f; return; }
        t += 0.02f * dt;
        angle = t * 0.05f + 0.8f * (float) Math.sin(t * 0.11f);
        float gustAmt = Settings.WIND_GUSTS.get();
        if (gust < 0.05f && R.nextFloat() < 0.004f * dt * gustAmt) gust = 1f;
        gust *= (float) Math.pow(0.985, dt);
        float base = 0.3f + 0.2f * (float) Math.sin(t * 0.37f) + 0.1f * (float) Math.sin(t * 1.1f + 2f);
        float v = base + gust * 0.6f * gustAmt + s.rain() * 0.2f;
        if (Settings.WIND_STORM.on()) v += s.thunder() * 0.5f;
        if (s.area() == Area.MOUNTAIN) v += 0.25f;
        if (s.cave() || s.underwater()) v *= 0.1f;
        intensity = Math.max(0f, Math.min(2.5f, v * Settings.WIND_STRENGTH.get()));
    }

    /** Horizontal drift in blocks per tick for particles. */
    public static double driftX() { return Math.cos(angle) * intensity * 0.05; }
    public static double driftZ() { return Math.sin(angle) * intensity * 0.05; }
    public static boolean gusting() { return gust > 0.3f; }
}
