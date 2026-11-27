package com.livingworld.core;

import com.livingworld.config.Settings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

import java.lang.reflect.Method;
import java.time.LocalDate;

/**
 * Single adapter between the mod and Minecraft's world state. Anything likely to be renamed between
 * Minecraft releases lives here so a fix is a one-file job.
 */
public final class WorldProbe {
    public enum Area {
        FOREST("Forest"), PLAINS("Plains"), DESERT("Desert"), JUNGLE("Jungle"), SWAMP("Swamp"),
        MOUNTAIN("Mountain"), BEACH("Beach"), OCEAN("Ocean"), RIVER("River"), SNOWY("Snowy");
        public final String label;
        Area(String l) { label = l; }
    }

    public enum Season {
        OFF("Off"), SPRING("Spring"), SUMMER("Summer"), AUTUMN("Autumn"), WINTER("Winter");
        public final String label;
        Season(String l) { label = l; }
    }

    public record Snapshot(Area area, boolean cave, boolean lush, boolean underwater, boolean outdoors,
                           boolean cold, float rain, float thunder, float sun, float night, float sunset,
                           float daylight, Season season, BlockPos pos) {}

    private static Method timeMethod;
    private static boolean timeResolved, timeWarned;

    private WorldProbe() {}

    /** Time of day in ticks 0..23999. Resolved by reflection so a rename does not break the build. */
    private static long timeOfDay(ClientLevel level) {
        if (!timeResolved) {
            timeResolved = true;
            for (String name : new String[]{"getDayTime", "getOverworldClockTime", "getGameTime"}) {
                try { timeMethod = level.getClass().getMethod(name); break; } catch (ReflectiveOperationException ignored) {}
            }
        }
        try {
            if (timeMethod != null) {
                long t = ((Number) timeMethod.invoke(level)).longValue();
                return ((t % 24000L) + 24000L) % 24000L;
            }
        } catch (ReflectiveOperationException ignored) {}
        if (!timeWarned) {
            timeWarned = true;
            com.livingworld.LivingWorldClient.LOGGER.warn("Could not read world time; assuming noon");
        }
        return 6000L;
    }

    public static Season season() {
        int i = Settings.SEASON.index();
        return switch (i) {
            case 1 -> {
                int m = LocalDate.now().getMonthValue();
                yield m >= 3 && m <= 5 ? Season.SPRING : m <= 8 && m >= 6 ? Season.SUMMER
                        : m >= 9 && m <= 11 ? Season.AUTUMN : Season.WINTER;
            }
            case 2 -> Season.SPRING;
            case 3 -> Season.SUMMER;
            case 4 -> Season.AUTUMN;
            case 5 -> Season.WINTER;
            default -> Season.OFF;
        };
    }

    /** Returns null outside the Overworld or when no world is loaded. */
    public static Snapshot capture(Minecraft mc) {
        ClientLevel level = mc.level;
        LocalPlayer p = mc.player;
        if (level == null || p == null || level.dimension() != Level.OVERWORLD) return null;

        BlockPos pos = p.blockPosition();
        Holder<Biome> b = level.getBiome(pos);
        boolean outdoors = level.canSeeSky(pos.above());
        boolean lush = b.is(Biomes.LUSH_CAVES);
        boolean cold = b.value().getBaseTemperature() <= 0.15f;
        int sky = level.getBrightness(LightLayer.SKY, pos);
        boolean cave = !outdoors && sky <= 1 && pos.getY() < level.getSeaLevel() + 8;

        Area area;
        if (b.is(BiomeTags.IS_OCEAN) || b.is(BiomeTags.IS_DEEP_OCEAN)) area = Area.OCEAN;
        else if (b.is(BiomeTags.IS_BEACH)) area = Area.BEACH;
        else if (b.is(BiomeTags.IS_RIVER)) area = Area.RIVER;
        else if (b.is(Biomes.SWAMP) || b.is(Biomes.MANGROVE_SWAMP)) area = Area.SWAMP;
        else if (b.is(BiomeTags.IS_JUNGLE)) area = Area.JUNGLE;
        else if (b.is(Biomes.DESERT) || b.is(BiomeTags.IS_BADLANDS)) area = Area.DESERT;
        else if (cold) area = Area.SNOWY;
        else if (b.is(BiomeTags.IS_MOUNTAIN) || b.is(BiomeTags.IS_HILL)) area = Area.MOUNTAIN;
        else if (b.is(BiomeTags.IS_FOREST) || b.is(BiomeTags.IS_TAIGA)) area = Area.FOREST;
        else area = Area.PLAINS;

        long tod = timeOfDay(level);
        double angle = (tod - 6000L) / 24000.0 * Math.PI * 2.0;
        float sun = (float) Math.cos(angle); // 1 at noon, -1 at midnight
        float night = clamp((0.05f - sun) / 0.4f);
        float sunset = clamp(1f - Math.abs(sun) / 0.3f);
        float daylight = clamp((sun + 0.1f) / 0.4f);

        return new Snapshot(area, cave, lush, p.isUnderWater(), outdoors, cold,
                level.getRainLevel(1f), level.getThunderLevel(1f), sun, night, sunset, daylight, season(), pos);
    }

    /** Honours the per-environment toggles on the Biomes page. */
    public static boolean areaEnabled(Snapshot s) {
        if (s.underwater()) return true;
        if (s.cave()) return s.lush() ? Settings.B_LUSH.on() : Settings.B_CAVE.on();
        return switch (s.area()) {
            case FOREST -> Settings.B_FOREST.on();
            case PLAINS -> Settings.B_PLAINS.on();
            case DESERT -> Settings.B_DESERT.on();
            case JUNGLE -> Settings.B_JUNGLE.on();
            case SWAMP -> Settings.B_SWAMP.on();
            case MOUNTAIN -> Settings.B_MOUNTAIN.on();
            case BEACH -> Settings.B_BEACH.on();
            case OCEAN -> Settings.B_OCEAN.on();
            case RIVER -> Settings.B_RIVER.on();
            case SNOWY -> Settings.B_SNOWY.on();
        };
    }

    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }
}
