# Living World Ultimate (stage 1)

Client-side Fabric mod for Minecraft Java 26.3 (Java 25). Adds **no mobs, animals or entities** and needs nothing on the server.

## Status: source complete for stage 1, NOT yet compiled or launched

This was written in an environment with no network access, so Gradle, Minecraft and Fabric could not be downloaded.
What was verified: all Java parses, internal references between mod classes resolve, all 51 audio files are valid
mono OGG Vorbis, and `sounds.json` matches the files. What was **not** verified: that the Minecraft 26.3 API calls
compile, and that the game launches. Treat the first build as the real test.

## Build
1. Open https://fabricmc.net/develop, choose 26.3, and copy the Loom, Fabric Loader and Fabric API versions into `gradle.properties`.
   Put the Mod Menu version for 26.3 in `modmenu_version`.
2. Install Gradle 9.4+ once and run `gradle wrapper --gradle-version 9.4.1`, then `./gradlew build`.
3. Jar appears in `build/libs/`. Run with `./gradlew runClient`.

## Most likely first compile errors (all isolated, quick to fix)
| Spot | File | Why it may differ |
|---|---|---|
| `getBaseTemperature()` | core/WorldProbe | may be renamed |
| world time (reflection, falls back to noon) | core/WorldProbe | 26.x reworked world clocks; check the log for "Could not read world time" |
| `ParticleTypes.FIREFLY`, `CHERRY_LEAVES`, `SPORE_BLOSSOM_AIR` | particles/ParticleDirector | particle names |
| `new DustParticleOptions(int, float)` | particles/ParticleDirector | constructor shape |
| `new SimpleSoundInstance(event, source, vol, pitch, RandomSource, x, y, z)` | audio/AmbientSounds | constructor shape |
| `mc.getFps()` | performance/PerformanceManager | accessor name |
| `mc.gui.setScreen(...)`, `extractRenderState`, `graphics.text/fill` | gui/LwScreen, visual/ScreenGrading | written to the 26.2 Fabric docs |
| `extractBackground` (deliberately no @Override) | gui/LwScreen | if the hook has another name, vanilla background shows and the menu still works |

## What works (every menu control maps to code)
- **Sounds**: 17 synthesized original ambient sounds x3 variants, randomised 3D placement, per-layer cooldowns, no self-overlap. Layers: wind (gentle/gust/storm by live wind), forest + birds, night crickets/owls/frogs, ocean/beach waves, river, swamp bubbles, cave drips/rumble, thunder rolls, underwater hum/bubbles, desert sand hiss.
- **Wind**: procedural wind with gusts, stronger in mountains and storms; drives particle drift and sound choice.
- **Weather**: extra rain splashes (ground and water), leaf drips, wind-driven snowflakes, lightning flash, thunder delayed by lightning distance.
- **Particles**: dust motes, cave dust, pollen, spring petals, autumn/forest leaves, fireflies, blown sand, lush-cave spores, campfire embers/ash, footstep dust/snow puffs. Shared budget, density, radius, max per tick.
- **Screen effects**: night blue, sunrise/sunset warm, underwater, storm darkening, rain droplets on screen. This is a screen tint, not a shader, so it works with or without shader packs.
- **Seasons**: Off / Auto (real date) / Spring / Summer / Autumn / Winter, affecting pollen, petals, leaves and snow.
- **Biomes page**: toggles for 12 environments gating both sound and particles.
- **Performance**: update interval, performance-friendly mode, auto-reduce by FPS, debug overlay.
- **Menu**: Mod Menu configure button, sidebar, search, filters (All/Enabled/Disabled/Modified/Perf/Visual/Audio), paged rows, reset per row/page/all, quality presets (Low..Extreme), 9 premade profiles, save/load/rename/duplicate/delete, clipboard export/import, auto-backup before bulk changes, persistence in `config/livingworld/`.

## Not implemented yet (deliberately absent from the menu)
God rays / volumetric light, fog distance and colour control, vegetation sway, sky/cloud/star changes, dynamic held-item light, water reflections, camera bob, rain-on-glass sound variants, per-biome overrides of individual values. These need render-pipeline mixins that must be written against the real 26.3 sources; do them after stage 1 compiles.

## Sounds
`tools/generate_sounds.py` regenerates the audio (numpy, scipy, ffmpeg). The audio is synthetic and original; replace any `.ogg` in `assets/livingworld/sounds/` with a recording (mono, same name) for more realism.
