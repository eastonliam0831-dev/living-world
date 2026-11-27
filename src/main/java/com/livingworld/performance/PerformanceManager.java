package com.livingworld.performance;

import com.livingworld.config.Settings;
import net.minecraft.client.Minecraft;

/** Scales particle load down when the frame rate is under the target. */
public final class PerformanceManager {
    private static float factor = 1f;
    private static int timer;

    private PerformanceManager() {}

    public static void tick(Minecraft mc, int dt) {
        timer += dt;
        if (timer < 20) return;
        timer = 0;
        if (!Settings.PERF_AUTO.on()) { factor = 1f; return; }
        float fps = mc.getFps();
        float target = Settings.PERF_FPS.get();
        float want = fps >= target ? 1f : Math.max(0.2f, fps / target);
        factor += (want - factor) * 0.5f;
    }

    /** 0.1 .. 1.0 multiplier applied to particle load. */
    public static float factor() { return Math.max(0.1f, factor * (Settings.PERF_MODE.on() ? 0.5f : 1f)); }
}
