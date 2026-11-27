package com.livingworld;

import com.livingworld.audio.AmbientSounds;
import com.livingworld.config.ConfigManager;
import com.livingworld.config.Settings;
import com.livingworld.core.Wind;
import com.livingworld.core.WorldProbe;
import com.livingworld.core.WorldProbe.Snapshot;
import com.livingworld.particles.ParticleDirector;
import com.livingworld.performance.PerformanceManager;
import com.livingworld.visual.ScreenGrading;
import com.livingworld.weather.StormEffects;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LivingWorldClient implements ClientModInitializer {
    public static final String ID = "livingworld";
    public static final Logger LOGGER = LoggerFactory.getLogger("LivingWorldUltimate");

    /** Latest world snapshot, read by the dashboard and debug overlay. Null outside the Overworld. */
    public static volatile Snapshot snapshot;

    private static int ticks, sinceUpdate, sinceSave;

    @Override
    public void onInitializeClient() {
        ConfigManager.load();
        ScreenGrading.register();
        ClientTickEvents.END_CLIENT_TICK.register(LivingWorldClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            AmbientSounds.reset();
            snapshot = null;
        });
        LOGGER.info("Living World Ultimate loaded (client-side only, no entities added)");
    }

    private static void tick(Minecraft mc) {
        if (++sinceSave >= 600) {
            sinceSave = 0;
            if (ConfigManager.isDirty()) ConfigManager.save();
        }
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        ticks++;
        sinceUpdate++;

        if (!Settings.MASTER.on()) {
            snapshot = null;
            ScreenGrading.tick(null, 1);
            StormEffects.flash = 0f;
            return;
        }

        int interval = Math.max(1, Math.round(Settings.PERF_INTERVAL.get()));
        Snapshot s = snapshot;
        if (sinceUpdate >= interval || s == null) {
            int dt = sinceUpdate;
            sinceUpdate = 0;
            s = WorldProbe.capture(mc);
            snapshot = s;
            PerformanceManager.tick(mc, dt);
            Wind.tick(s, dt);
            AmbientSounds.tick(mc, s, dt);
            ParticleDirector.tick(mc, s, dt);
        }
        // These run every tick: bolts are short lived and fades should stay smooth.
        StormEffects.tick(mc, s);
        ScreenGrading.tick(s, 1);
    }
}
