package com.livingworld.particles;

import com.livingworld.config.Settings;
import com.livingworld.core.Wind;
import com.livingworld.core.WorldProbe;
import com.livingworld.core.WorldProbe.Area;
import com.livingworld.core.WorldProbe.Season;
import com.livingworld.core.WorldProbe.Snapshot;
import com.livingworld.performance.PerformanceManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Random;

/**
 * Spawns ambient particles around the player using only vanilla particle types and ClientLevel.addParticle.
 * Everything shares one per-update budget so load stays bounded.
 */
public final class ParticleDirector {
    private static final Random R = new Random();
    private static final int[] AUTUMN = {0xC8561A, 0xD98A1F, 0xA83A1A, 0xE0B030};

    public static int perSecond;
    private static int spawned, secondTimer, budget, stepTimer;
    private static ClientLevel level;
    private static LocalPlayer player;

    private ParticleDirector() {}

    private static int count(float rate) {
        int n = (int) rate;
        if (R.nextFloat() < rate - n) n++;
        return n;
    }

    private static void emit(ParticleOptions o, double x, double y, double z, double vx, double vy, double vz) {
        if (budget <= 0) return;
        budget--; spawned++;
        level.addParticle(o, x, y, z, vx, vy, vz);
    }

    private static double[] ring(double minD, double maxD) {
        double a = R.nextDouble() * Math.PI * 2, d = minD + R.nextDouble() * (maxD - minD);
        return new double[]{player.getX() + Math.cos(a) * d, player.getZ() + Math.sin(a) * d};
    }

    private static boolean sky(double x, double y, double z) { return level.canSeeSky(BlockPos.containing(x, y, z)); }

    public static void tick(Minecraft mc, Snapshot s, int dt) {
        secondTimer += dt;
        if (secondTimer >= 20) { perSecond = spawned; spawned = 0; secondTimer = 0; }
        if (s == null || !Settings.PART_MASTER.on() || !WorldProbe.areaEnabled(s)) return;
        level = mc.level; player = mc.player;
        float factor = PerformanceManager.factor();
        float k = Settings.PART_DENSITY.get() * factor * dt;
        budget = Math.max(1, (int) (Settings.PART_MAX.get() * dt * Math.min(1f, factor + 0.25f)));
        double radius = Settings.PART_RADIUS.get();
        double wx = Wind.driftX(), wz = Wind.driftZ(), py = player.getY();
        Area a = s.area();
        Season se = s.season();
        float si = Settings.SEASON_INT.get();
        float rain = s.rain();
        boolean precipSnow = s.cold();

        if (s.underwater()) return;

        // ---- Sunlit dust motes / cave dust
        if (Settings.P_DUST.on() && s.outdoors() && s.daylight() > 0.2f && rain < 0.4f) {
            for (int i = count(0.5f * k); i > 0; i--) {
                double[] p = ring(2, Math.min(radius, 12));
                emit(ParticleTypes.WHITE_ASH, p[0], py - 1 + R.nextDouble() * 5, p[1], wx, 0.002, wz);
            }
        } else if (Settings.P_CAVE_DUST.on() && s.cave()) {
            for (int i = count(0.25f * k); i > 0; i--) {
                double[] p = ring(1, 8);
                emit(ParticleTypes.WHITE_ASH, p[0], py + R.nextDouble() * 3, p[1], 0, -0.001, 0);
            }
        }

        // ---- Pollen: spring and summer, or green biomes with seasons off
        boolean pollenSeason = se == Season.SPRING || se == Season.SUMMER || (se == Season.OFF && (a == Area.PLAINS || a == Area.FOREST));
        if (Settings.P_POLLEN.on() && pollenSeason && s.outdoors() && s.daylight() > 0.3f && rain < 0.2f
                && (a == Area.PLAINS || a == Area.FOREST || a == Area.JUNGLE)) {
            for (int i = count(0.3f * k * (se == Season.OFF ? 1f : si)); i > 0; i--) {
                double[] p = ring(2, Math.min(radius, 14));
                emit(new DustParticleOptions(0xFFE066, 0.5f), p[0], py + R.nextDouble() * 4, p[1],
                        wx * 1.5, 0.003 + R.nextDouble() * 0.004, wz * 1.5);
            }
        }

        // ---- Spring petals
        if (Settings.P_PETALS.on() && se == Season.SPRING && s.outdoors() && rain < 0.5f
                && (a == Area.PLAINS || a == Area.FOREST)) {
            for (int i = count(0.2f * k * si); i > 0; i--) {
                double[] p = ring(2, Math.min(radius, 14));
                emit(ParticleTypes.CHERRY_LEAVES, p[0], py + 4 + R.nextDouble() * 5, p[1], wx * 2, -0.02, wz * 2);
            }
        }

        // ---- Falling leaves: autumn colours, or a light green trickle in forests
        if (Settings.P_LEAVES.on() && s.outdoors() && (a == Area.FOREST || a == Area.PLAINS || a == Area.JUNGLE)
                && (se == Season.AUTUMN || (se == Season.OFF && a == Area.FOREST))) {
            boolean autumn = se == Season.AUTUMN;
            for (int i = count((autumn ? 0.35f * si : 0.08f) * k); i > 0; i--) {
                double[] p = ring(2, Math.min(radius, 14));
                int col = autumn ? AUTUMN[R.nextInt(AUTUMN.length)] : 0x5E8F3A;
                emit(new DustParticleOptions(col, 0.9f), p[0], py + 3 + R.nextDouble() * 6, p[1],
                        wx * 3, -0.03, wz * 3);
            }
        }

        // ---- Fireflies
        if (Settings.P_FIREFLY.on() && s.night() > 0.6f && s.outdoors() && rain < 0.2f
                && (a == Area.FOREST || a == Area.SWAMP || a == Area.PLAINS || a == Area.JUNGLE)) {
            for (int i = count(0.15f * k); i > 0; i--) {
                double[] p = ring(3, Math.min(radius, 16));
                emit(ParticleTypes.FIREFLY, p[0], py - 1 + R.nextDouble() * 4, p[1], 0, 0, 0);
            }
        }

        // ---- Wind-blown sand
        if (Settings.P_SAND.on() && a == Area.DESERT && s.outdoors() && Wind.intensity > 0.45f) {
            for (int i = count(0.6f * Wind.intensity * k); i > 0; i--) {
                double[] p = ring(3, Math.min(radius, 16));
                emit(new DustParticleOptions(0xD8C48A, 1.0f), p[0], py - 1 + R.nextDouble() * 2.5, p[1],
                        wx * 6, 0.004, wz * 6);
            }
        }

        // ---- Lush cave spores
        if (Settings.P_SPORES.on() && s.cave() && s.lush()) {
            for (int i = count(0.25f * k); i > 0; i--) {
                double[] p = ring(2, 10);
                emit(ParticleTypes.SPORE_BLOSSOM_AIR, p[0], py + R.nextDouble() * 4, p[1], 0, -0.002, 0);
            }
        }

        // ---- Snow: flurries in snowy precipitation, sparse flakes in winter
        if (Settings.SNOW_FLURRY.on() && s.outdoors()) {
            float amount = 0f;
            if (precipSnow && rain > 0.1f) amount = 2.0f * rain;
            else if (se == Season.WINTER) amount = 0.15f * si;
            for (int i = count(amount * Settings.SNOW_FLURRY_DENS.get() * k); i > 0; i--) {
                double[] p = ring(1, Math.min(radius, 16));
                emit(ParticleTypes.SNOWFLAKE, p[0], py + 3 + R.nextDouble() * 8, p[1],
                        wx * 4, -0.04 - R.nextDouble() * 0.03, wz * 4);
            }
        }

        // ---- Rain: ground and water splashes, leaf drips
        if (rain > 0.2f && !precipSnow && s.outdoors()) {
            if (Settings.RAIN_SPLASH.on()) {
                for (int i = count(3f * rain * Settings.RAIN_SPLASH_DENS.get() * k); i > 0; i--) {
                    double[] p = ring(2, Math.min(radius, 14));
                    BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(p[0], py, p[1]));
                    boolean water = level.getFluidState(top.below()).is(FluidTags.WATER);
                    emit(water ? ParticleTypes.SPLASH : ParticleTypes.RAIN,
                            p[0], top.getY() + 0.05, p[1], 0, 0, 0);
                }
            }
            if (Settings.RAIN_DRIPS.on() && (a == Area.FOREST || a == Area.JUNGLE || a == Area.SWAMP)) {
                for (int i = count(0.6f * rain * k); i > 0; i--) {
                    double[] p = ring(2, 10);
                    int x = (int) Math.floor(p[0]), z = (int) Math.floor(p[1]);
                    int topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                    if (level.getBlockState(new BlockPos(x, topY - 1, z)).is(BlockTags.LEAVES))
                        emit(ParticleTypes.FALLING_WATER, x + R.nextDouble(), topY - 1.05, z + R.nextDouble(), 0, 0, 0);
                }
            }
        }

        // ---- Campfire embers and ash
        if (Settings.P_EMBERS.on()) {
            for (int i = 0; i < 6; i++) {
                double[] p = ring(1, Math.min(radius, 14));
                BlockPos bp = BlockPos.containing(p[0], py + R.nextInt(5) - 2, p[1]);
                BlockState st = level.getBlockState(bp);
                if (st.is(BlockTags.CAMPFIRES) && st.hasProperty(BlockStateProperties.LIT) && st.getValue(BlockStateProperties.LIT)
                        && R.nextFloat() < 0.5f * Math.min(1f, k)) {
                    emit(ParticleTypes.ASH, bp.getX() + 0.5, bp.getY() + 0.9, bp.getZ() + 0.5,
                            wx * 2 + (R.nextDouble() - 0.5) * 0.02, 0.03, wz * 2 + (R.nextDouble() - 0.5) * 0.02);
                    if (R.nextFloat() < 0.3f)
                        emit(ParticleTypes.SMALL_FLAME, bp.getX() + 0.5, bp.getY() + 0.8, bp.getZ() + 0.5,
                                (R.nextDouble() - 0.5) * 0.02, 0.04, (R.nextDouble() - 0.5) * 0.02);
                }
            }
        }

        // ---- Footstep dust and snow puffs
        stepTimer += dt;
        if (Settings.P_STEPS.on() && stepTimer >= 3 && player.onGround() && !player.isInWater()
                && player.getDeltaMovement().horizontalDistanceSqr() > 0.008) {
            stepTimer = 0;
            BlockState below = level.getBlockState(player.blockPosition().below());
            boolean snowHere = level.getBlockState(player.blockPosition()).is(Blocks.SNOW) || below.is(Blocks.SNOW_BLOCK);
            boolean dusty = below.is(BlockTags.SAND) || below.is(Blocks.GRAVEL) || below.is(BlockTags.DIRT);
            if (snowHere || dusty) {
                for (int i = 0; i < 2; i++)
                    emit(new BlockParticleOption(ParticleTypes.BLOCK, snowHere ? Blocks.SNOW_BLOCK.defaultBlockState() : below),
                            player.getX() + (R.nextDouble() - 0.5) * 0.4, player.getY() + 0.05,
                            player.getZ() + (R.nextDouble() - 0.5) * 0.4, 0, 0.02, 0);
                if (snowHere) emit(ParticleTypes.SNOWFLAKE, player.getX(), player.getY() + 0.1, player.getZ(), 0, 0.03, 0);
            }
        }
    }
}
