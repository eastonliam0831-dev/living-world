package com.livingworld.visual;

import com.livingworld.LivingWorldClient;
import com.livingworld.audio.AmbientSounds;
import com.livingworld.config.Settings;
import com.livingworld.core.Wind;
import com.livingworld.core.WorldProbe;
import com.livingworld.core.WorldProbe.Snapshot;
import com.livingworld.particles.ParticleDirector;
import com.livingworld.performance.PerformanceManager;
import com.livingworld.weather.StormEffects;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Colour grading and rain droplets drawn as translucent HUD layers under the hotbar.
 * This is a screen tint, not a shader: it is cheap and works with or without shader packs.
 */
public final class ScreenGrading {
    private static final Random R = new Random();
    // channels: night, sunset, underwater, storm
    private static final int[] RGB = {0x14285A, 0xFF7A2E, 0x0E5A78, 0x05080F};
    private static final float[] cur = new float[4], target = new float[4];

    private record Drop(float x, float y, int life, int age, float size) {}
    private static final List<Drop> DROPS = new ArrayList<>();

    private ScreenGrading() {}

    public static void register() {
        HudElementRegistry.attachElementBefore(VanillaHudElements.HOTBAR,
                Identifier.fromNamespaceAndPath(LivingWorldClient.ID, "grading"), ScreenGrading::extractGrading);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(LivingWorldClient.ID, "debug"), ScreenGrading::extractDebug);
    }

    public static void tick(Snapshot s, int dt) {
        float in = Settings.GRADE_INT.get();
        boolean on = s != null && Settings.MASTER.on() && Settings.GRADE_ON.on();
        float indoors = s != null && !s.outdoors() ? 0.4f : 1f;
        target[0] = on && Settings.GRADE_NIGHT.on() ? 0.20f * s.night() * indoors * in : 0f;
        target[1] = on && Settings.GRADE_SUNSET.on() && s.outdoors() ? 0.13f * s.sunset() * in : 0f;
        target[2] = on && Settings.GRADE_WATER.on() && s.underwater() ? 0.25f * Math.min(1f, in) : 0f;
        target[3] = on && Settings.GRADE_STORM.on() && s.outdoors()
                ? 0.22f * Math.max(s.thunder(), s.rain() * 0.6f) * in : 0f;
        float k = 1f - (float) Math.pow(0.95, dt);
        for (int i = 0; i < 4; i++) cur[i] += (target[i] - cur[i]) * k;

        // droplets
        boolean wet = s != null && Settings.MASTER.on() && Settings.DROPLETS.on() && s.outdoors() && !s.underwater()
                && !s.cold() && s.rain() > 0.3f;
        if (wet && DROPS.size() < 60) {
            float rate = s.rain() * Settings.DROPLET_INT.get() * 0.5f * dt;
            for (int i = (int) rate + (R.nextFloat() < rate - (int) rate ? 1 : 0); i > 0; i--)
                DROPS.add(new Drop(R.nextFloat(), R.nextFloat() * 0.8f, 100 + R.nextInt(120), 0, 1 + R.nextFloat() * 1.5f));
        }
        for (Iterator<Drop> it = DROPS.iterator(); it.hasNext(); ) {
            Drop d = it.next();
            if (d.age + dt >= d.life) { it.remove(); }
        }
        for (int i = 0; i < DROPS.size(); i++) {
            Drop d = DROPS.get(i);
            float slide = d.age > 40 ? 0.0006f * dt : 0f;
            DROPS.set(i, new Drop(d.x, d.y + slide, d.life, d.age + dt, d.size));
        }
    }

    private static int argb(float alpha, int rgb) {
        int a = Math.max(0, Math.min(255, (int) (alpha * 255f)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    private static void extractGrading(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        for (int i = 0; i < 4; i++)
            if (cur[i] > 0.004f) g.fill(0, 0, w, h, argb(cur[i], RGB[i]));

        for (Drop d : DROPS) {
            float life = 1f - (float) d.age / d.life;
            float a = Math.min(0.45f, life * 0.8f);
            int x = (int) (d.x * w), y = (int) (d.y * h), sz = Math.max(1, (int) d.size);
            if (d.age > 40) g.fill(x, y - 4 - sz, x + 1, y, argb(a * 0.4f, 0xC8E0F0)); // trail
            g.fill(x, y, x + sz + 1, y + sz + 2, argb(a, 0xDCEBF5));
        }

        if (Settings.MASTER.on() && Settings.FLASH.on() && StormEffects.flash > 0.01f)
            g.fill(0, 0, w, h, argb(StormEffects.flash * 0.55f * Settings.FLASH_INT.get(), 0xFFFFFF));
    }

    private static void extractDebug(GuiGraphicsExtractor g, DeltaTracker delta) {
        if (!Settings.PERF_DEBUG.on()) return;
        Minecraft mc = Minecraft.getInstance();
        Snapshot s = LivingWorldClient.snapshot;
        String[] lines = {
                "Living World Ultimate",
                s == null ? "no snapshot (not in Overworld)" : "area " + s.area().label + (s.cave() ? " cave" : "")
                        + "  rain " + String.format("%.2f", s.rain()) + "  thunder " + String.format("%.2f", s.thunder()),
                "wind " + String.format("%.2f", Wind.intensity) + "  particles/s " + ParticleDirector.perSecond
                        + "  quality " + (int) (PerformanceManager.factor() * 100) + "%",
                "sound layers played " + AmbientSounds.played + "  profile " + AmbientSounds.profile};
        int y = 4;
        for (String l : lines) { g.text(mc.font, l, 4, y, 0xFFB8F5D0, true); y += 10; }
    }
}
