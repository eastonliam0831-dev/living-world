package com.livingworld.weather;

import com.livingworld.audio.AmbientSounds;
import com.livingworld.config.Settings;
import com.livingworld.core.WorldProbe.Snapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;

import java.util.HashSet;
import java.util.Set;

/** Detects lightning bolts on the client to drive the screen flash and distance-delayed thunder. */
public final class StormEffects {
    public static float flash;
    private static final Set<Integer> SEEN = new HashSet<>();

    private StormEffects() {}

    /** Runs every client tick because a lightning bolt entity only lives for a few ticks. */
    public static void tick(Minecraft mc, Snapshot s) {
        flash = Math.max(0f, flash - 0.07f);
        if (s == null || mc.level == null || mc.player == null || (s.thunder() < 0.01f && s.rain() < 0.3f)) {
            SEEN.clear();
            return;
        }
        Set<Integer> present = new HashSet<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof LightningBolt) {
                present.add(e.getId());
                if (SEEN.add(e.getId())) strike(mc, e.position().distanceTo(mc.player.position()));
            }
        }
        SEEN.retainAll(present);
    }

    private static void strike(Minecraft mc, double distance) {
        if (Settings.FLASH.on()) {
            float strength = (float) Math.max(0.08, Math.min(1.0, 1.1 - distance / 200.0));
            flash = Math.max(flash, strength);
        }
        AmbientSounds.queueThunder(distance);
    }
}
