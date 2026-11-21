package dev.visuals.effects;

import dev.visuals.VisualsMod;
import dev.visuals.config.ConfigManager;
import dev.visuals.config.def.Tabs;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Lightweight client-only ambient particle effects.
 *
 * <p>These are deliberately particles, not entities: they never affect collision, AI, drops,
 * server state or gameplay. The engine is throttled and obeys the global/category switches.
 */
public final class VisualEffects {
    private static boolean registered;
    private static long tickCounter;
    private static int touchEffectsThisTick;

    private VisualEffects() {}

    public static void register() {
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(VisualEffects::tick);
    }

    private static void tick(Minecraft client) {
        if (client.level == null || client.player == null || client.isPaused()) return;
        tickCounter++;
        if ((tickCounter & 3L) != 0L) return;

        ConfigManager cfg = VisualsMod.config();
        if (cfg == null || !Boolean.TRUE.equals(cfg.getRaw(ConfigManager.MOD_ENABLED_ID))) return;

        Player player = client.player;
        Level level = client.level;
        touchEffectsThisTick = 0;
        if (cfg.isEffectActive(Tabs.FIREFLIES)) {
            fireflies(cfg, level, player);
        }
        if (cfg.isEffectActive(Tabs.WILDLIFE)) {
            wildlife(cfg, level, player);
        }
        if (cfg.isEffectActive(Tabs.FOLIAGE)) {
            fallingLeaves(cfg, level, player);
        }
        if (cfg.isEffectActive(Tabs.SKY)) {
            shootingStars(cfg, level, player);
        }
        if (cfg.isEffectActive(Tabs.EVENTS)) {
            meteorShowers(cfg, level, player);
        }
        if (cfg.isEffectActive(Tabs.TOUCH)) {
            waterRings(cfg, level, player);
            groundDust(cfg, level, player);
            snowSteps(cfg, level, player);
            surfaceSteps(cfg, level, player);
            plantResponse(cfg, level, player);
        }
    }

    private static void fireflies(ConfigManager cfg, Level level, Player player) {
        if (!bool(cfg, "fireflies.swarm", true)) return;
        String time = string(cfg, "fireflies.time", "night");
        long dayTime = level.getDayTime() % 24000L;
        boolean night = dayTime >= 13000L && dayTime <= 23000L;
        boolean dusk = dayTime >= 11000L && dayTime <= 14000L;
        if ("night".equals(time) && !night) return;
        if ("dusk".equals(time) && !dusk) return;

        int amount = Math.max(0, Math.min(100, integer(cfg, "fireflies.amount", 24)));
        if (amount == 0) return;
        String style = string(cfg, "fireflies.style", "pulse");
        double phase = (tickCounter % 40L) / 40.0;
        double pulse = 0.65 + 0.35 * Math.sin(phase * Math.PI * 2.0);
        double flicker = Math.random() < 0.22 ? 0.28 : 1.0;
        double styleScale = switch (style) {
            case "flicker" -> flicker;
            case "trail" -> 0.72;
            default -> pulse;
        };
        int color = integer(cfg, Math.random() < 0.5 ? "fireflies.primaryColor" : "fireflies.secondaryColor",
                0xFFFFE78A) & 0xFFFFFF;
        float glow = (float) Math.max(0.05, Math.min(1.5,
                number(cfg, "fireflies.glow", 0.8) * styleScale));

        // Amount controls the maximum attempted particles per update; rejection keeps the swarm sparse.
        for (int i = 0; i < amount; i++) {
            if (Math.random() > 0.35) continue;
            double x = player.getX() + (Math.random() - 0.5) * 14.0;
            double y = player.getY() + 1.0 + Math.random() * 3.0;
            double z = player.getZ() + (Math.random() - 0.5) * 14.0;
            BlockPos pos = BlockPos.containing(x, y, z);
            // Keep ambient particles out of solid blocks; this also avoids wasting particles
            // where the player could never see them.
            if (!level.getBlockState(pos).isAir()) continue;
            // In bright areas, the optional torch preference keeps fireflies near light sources.
            // In darkness they can appear naturally without an expensive block scan.
            if (level.getMaxLocalRawBrightness(pos) > 10
                    && bool(cfg, "fireflies.nearTorches", true)
                    && !hasNearbyTorch(level, pos)) {
                continue;
            }
            double drift = (Math.random() - 0.5) * ("trail".equals(style) ? 0.035 : 0.012);
            double vertical = "trail".equals(style) ? 0.002 : 0.006;
            level.addParticle(new DustParticleOptions(color, glow), x, y, z, drift, vertical, drift);
            if ("trail".equals(style) && Math.random() < 0.55) {
                level.addParticle(new DustParticleOptions(color, Math.max(0.05f, glow * 0.55f)),
                        x - drift * 2.0, y - 0.035, z - drift * 2.0,
                        drift * 0.45, 0.001, drift * 0.45);
            }
        }
    }

    private static void wildlife(ConfigManager cfg, Level level, Player player) {
        int amount = Math.max(0, Math.min(50, integer(cfg, "wildlife.amount", 12)));
        if (amount == 0) return;
        boolean day = level.getDayTime() % 24000L < 12000L;
        boolean dayNightCycle = bool(cfg, "wildlife.dayNightCycle", true);

        // Daytime species and nocturnal bats use separate windows when the cycle is enabled.
        java.util.ArrayList<String> enabled = new java.util.ArrayList<>();
        if (!dayNightCycle || day) {
            if (bool(cfg, "wildlife.butterflies", true)) enabled.add("butterfly");
            if (bool(cfg, "wildlife.dragonflies", true)) enabled.add("dragonfly");
            if (bool(cfg, "wildlife.birds", true)) enabled.add("bird");
        }
        if (bool(cfg, "wildlife.bats", true) && (!dayNightCycle || !day)) enabled.add("bat");
        if (enabled.isEmpty()) return;

        double range = Math.max(8.0, Math.min(64.0, number(cfg, "wildlife.spawnRange", 24.0)));
        // "Amount" is the maximum number of ambient particles emitted per effect update.
        // Keep a hard cap so a high setting cannot create an unbounded particle burst.
        for (int i = 0; i < amount; i++) {
            if (Math.random() > 0.35) continue;
            String type = enabled.get((int) (Math.random() * enabled.size()));
            double x = player.getX() + (Math.random() - 0.5) * range * 2.0;
            double y = player.getY() + 1.5 + Math.random() * 4.0;
            double z = player.getZ() + (Math.random() - 0.5) * range * 2.0;
            BlockPos spawnPos = BlockPos.containing(x, y, z);
            if (!level.getBlockState(spawnPos).isAir()) continue;
            double drift = "bird".equals(type) ? 0.025
                    : ("bat".equals(type) ? 0.035 : ("dragonfly".equals(type) ? 0.012 : 0.006));
            var particle = "butterfly".equals(type) ? ParticleTypes.CHERRY_LEAVES
                    : ("dragonfly".equals(type) ? ParticleTypes.END_ROD
                    : ("bat".equals(type) ? ParticleTypes.ASH : ParticleTypes.HAPPY_VILLAGER));
            double vertical = "bird".equals(type) ? 0.004
                    : ("bat".equals(type) ? (Math.random() - 0.5) * 0.018 : (Math.random() - 0.5) * 0.004);
            level.addParticle(particle, x, y, z,
                    (Math.random() - 0.5) * drift, vertical, (Math.random() - 0.5) * drift);
        }
    }


    /**
     * Draws occasional cosmetic meteor streaks with short-lived glowing dust particles.
     * This is a client particle effect, not an entity or a change to the world.
     */
    private static void meteorShowers(ConfigManager cfg, Level level, Player player) {
        if (!bool(cfg, "events.meteorShowers", true)) return;
        double frequency = Math.max(0.0, Math.min(5.0, number(cfg, "events.frequency", 0.5)));
        if (frequency <= 0.0 || Math.random() > 0.0005 * frequency) return;

        // Keep the streak above the player and within a modest visible radius.
        double startX = player.getX() + (Math.random() - 0.5) * 48.0;
        double startY = player.getY() + 22.0 + Math.random() * 18.0;
        double startZ = player.getZ() + (Math.random() - 0.5) * 48.0;
        double velocityX = (Math.random() - 0.5) * 0.42;
        double velocityY = -0.22 - Math.random() * 0.12;
        double velocityZ = (Math.random() - 0.5) * 0.42;
        int color = Math.random() < 0.7 ? 0xFFF6D5A2 : 0xFFB8D8FF;

        for (int i = 0; i < 9; i++) {
            double progress = i / 9.0;
            double x = startX - velocityX * progress * 5.0;
            double y = startY - velocityY * progress * 5.0;
            double z = startZ - velocityZ * progress * 5.0;
            BlockPos pos = BlockPos.containing(x, y, z);
            if (!level.getBlockState(pos).isAir()) continue;
            float scale = i == 0 ? 1.25f : 0.72f;
            level.addParticle(new DustParticleOptions(color, scale), x, y, z,
                    velocityX, velocityY, velocityZ);
        }
    }

    /** Configurable night-sky shooting stars using the Sky tab's frequency, color and trail length. */
    private static void shootingStars(ConfigManager cfg, Level level, Player player) {
        long dayTime = level.getDayTime() % 24000L;
        if (dayTime < 12500L || dayTime > 23500L) return;
        double frequency = Math.max(0.0, Math.min(10.0, number(cfg, "sky.shootingFrequency", 1.0)));
        if (frequency <= 0.0 || Math.random() > frequency / 300.0) return;
        int trail = Math.max(2, Math.min(60, integer(cfg, "sky.shootingTrail", 12)));
        int color = integer(cfg, "sky.shootingColor", 0xFFFFFFFF) & 0xFFFFFF;
        double startX = player.getX() + (Math.random() - 0.5) * 56.0;
        double startY = player.getY() + 24.0 + Math.random() * 24.0;
        double startZ = player.getZ() + (Math.random() - 0.5) * 56.0;
        double velocityX = (Math.random() - 0.5) * 0.55;
        double velocityY = -0.28 - Math.random() * 0.16;
        double velocityZ = (Math.random() - 0.5) * 0.55;
        for (int i = 0; i < trail; i++) {
            double progress = i / (double) trail;
            double x = startX - velocityX * progress * 5.0;
            double y = startY - velocityY * progress * 5.0;
            double z = startZ - velocityZ * progress * 5.0;
            if (!level.getBlockState(BlockPos.containing(x, y, z)).isAir()) continue;
            float size = (float) (1.15 * (1.0 - progress * 0.55));
            level.addParticle(new DustParticleOptions(color, size), x, y, z, velocityX, velocityY, velocityZ);
        }
    }

    /** Checks for nearby vanilla torch light sources without loading additional chunks. */
    private static boolean hasNearbyTorch(Level level, BlockPos center) {
        for (BlockPos check : BlockPos.betweenClosed(
                center.offset(-4, -2, -4), center.offset(4, 2, 4))) {
            var block = level.getBlockState(check).getBlock();
            if (block == Blocks.TORCH || block == Blocks.WALL_TORCH
                    || block == Blocks.SOUL_TORCH || block == Blocks.SOUL_WALL_TORCH) {
                return true;
            }
        }
        return false;
    }

    /**
     * Spawns particles using the nearby leaf block's own state, so the particle texture
     * follows the tree instead of always using the cherry-leaf sprite. Blocks are untouched.
     */
    private static void fallingLeaves(ConfigManager cfg, Level level, Player player) {
        if (!bool(cfg, "foliage.fallingLeaves", true)) return;
        int amount = integer(cfg, "foliage.leafAmount", 24);
        int particleCap = integer(cfg, "foliage.particleCap", 500);
        if (amount <= 0 || particleCap <= 0) return;

        int range = Math.max(8, Math.min(128, integer(cfg, "foliage.renderRange", 32)));
        int attempts = Math.max(1, Math.min(100, Math.min(amount, particleCap)));
        double speed = Math.max(0.1, Math.min(2.0, number(cfg, "foliage.fallSpeed", 0.7)));
        // Reuse the weather wind controls to influence the horizontal drift of falling leaves.
        double windStrength = Math.max(0.0, Math.min(1.0, number(cfg, "weather.windStrength", 0.35)));
        double windRadians = Math.toRadians(number(cfg, "weather.windDirection", 0.0));
        double windX = Math.sin(windRadians) * windStrength * 0.018;
        double windZ = Math.cos(windRadians) * windStrength * 0.018;
        boolean gusts = bool(cfg, "weather.windGusts", true);
        for (int i = 0; i < attempts; i++) {
            int x = player.blockPosition().getX() + (int) (Math.random() * range * 2 - range);
            int y = player.blockPosition().getY() + (int) (Math.random() * 24 - 6);
            int z = player.blockPosition().getZ() + (int) (Math.random() * range * 2 - range);
            BlockPos leafPos = new BlockPos(x, y, z);
            var leafState = level.getBlockState(leafPos);
            if (!leafState.is(BlockTags.LEAVES)) continue;

            double px = x + Math.random();
            double py = y + 0.85 + Math.random() * 0.25;
            double pz = z + Math.random();
            // Gusts evolve over time so nearby leaves react to the same passing breeze.
            double gustWave = 0.72 + 0.28 * Math.sin((tickCounter + i * 3L) * 0.11);
            double gust = gusts ? gustWave * (0.9 + Math.random() * 0.2) : 1.0;
            double driftX = (Math.random() - 0.5) * 0.012 * speed + windX * gust;
            double driftZ = (Math.random() - 0.5) * 0.012 * speed + windZ * gust;
            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, leafState), px, py, pz,
                    driftX, -0.012 * speed, driftZ);
        }
    }

    /** Emits a small vanilla splash while the player moves through water. */
    private static void waterRings(ConfigManager cfg, Level level, Player player) {
        if (!bool(cfg, "touch.waterRings", true) || player.getDeltaMovement().horizontalDistanceSqr() < 0.0025) return;
        if (Math.random() > 0.45) return;
        BlockPos feet = player.blockPosition();
        BlockPos waterPos = level.getFluidState(feet).is(FluidTags.WATER) ? feet : feet.below();
        if (!level.getFluidState(waterPos).is(FluidTags.WATER)) return;
        double x = player.getX() + (Math.random() - 0.5) * 0.7;
        double z = player.getZ() + (Math.random() - 0.5) * 0.7;
        double y = waterPos.getY() + 0.9;
        if (!canEmitTouch(cfg)) return;
        level.addParticle(ParticleTypes.SPLASH, x, y, z, 0.0, 0.015, 0.0);
        touchEffectsThisTick++;
    }

    /** Small foot-level dust puffs on loose ground while walking; purely cosmetic. */
    private static void groundDust(ConfigManager cfg, Level level, Player player) {
        if (!bool(cfg, "touch.dust", true) || !player.onGround()) return;
        if (player.getDeltaMovement().horizontalDistanceSqr() < 0.0064 || Math.random() > 0.32) return;
        BlockPos ground = player.blockPosition().below();
        var block = level.getBlockState(ground).getBlock();
        if (block != Blocks.SAND && block != Blocks.RED_SAND && block != Blocks.GRAVEL) return;
        double x = player.getX() + (Math.random() - 0.5) * 0.45;
        double z = player.getZ() + (Math.random() - 0.5) * 0.45;
        if (!canEmitTouch(cfg)) return;
        level.addParticle(ParticleTypes.POOF, x, ground.getY() + 1.02, z,
                (Math.random() - 0.5) * 0.012, 0.008, (Math.random() - 0.5) * 0.012);
        touchEffectsThisTick++;
    }


    /** Small snowflake puffs at the player's feet on snow; a particle approximation, not terrain deformation. */
    private static void snowSteps(ConfigManager cfg, Level level, Player player) {
        if (!bool(cfg, "touch.snowDents", true) || !player.onGround()) return;
        if (player.getDeltaMovement().horizontalDistanceSqr() < 0.0064 || Math.random() > 0.24) return;
        BlockPos ground = player.blockPosition().below();
        var block = level.getBlockState(ground).getBlock();
        if (block != Blocks.SNOW && block != Blocks.SNOW_BLOCK && block != Blocks.POWDER_SNOW) return;
        double x = player.getX() + (Math.random() - 0.5) * 0.4;
        double z = player.getZ() + (Math.random() - 0.5) * 0.4;
        if (!canEmitTouch(cfg)) return;
        level.addParticle(ParticleTypes.SNOWFLAKE, x, ground.getY() + 1.03, z,
                (Math.random() - 0.5) * 0.008, 0.012, (Math.random() - 0.5) * 0.008);
        touchEffectsThisTick++;
    }


    /**
     * Emits a tiny amount of the actual surface block's particles behind moving players.
     * This is a short-lived particle trail, not a persistent footprint decal.
     */
    private static void surfaceSteps(ConfigManager cfg, Level level, Player player) {
        if (!bool(cfg, "touch.footprints", true) || !player.onGround()) return;
        if (player.getDeltaMovement().horizontalDistanceSqr() < 0.01 || Math.random() > 0.12) return;
        BlockPos ground = player.blockPosition().below();
        var state = level.getBlockState(ground);
        if (state.isAir() || !state.getFluidState().isEmpty()) return;

        double speed = Math.min(0.045, player.getDeltaMovement().horizontalDistance() * 0.12);
        double x = player.getX() - player.getDeltaMovement().x * 1.4;
        double z = player.getZ() - player.getDeltaMovement().z * 1.4;
        if (!canEmitTouch(cfg)) return;
        level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state),
                x, ground.getY() + 1.02, z,
                (Math.random() - 0.5) * speed, 0.012, (Math.random() - 0.5) * speed);
        touchEffectsThisTick++;
    }

    /**
     * Cosmetic response near grass and flowers. The current implementation uses tiny leaf
     * particles as feedback; it does not deform or animate block models.
     */
    private static void plantResponse(ConfigManager cfg, Level level, Player player) {
        if (!bool(cfg, "touch.bendingPlants", true) || !player.onGround()) return;
        double speedSqr = player.getDeltaMovement().horizontalDistanceSqr();
        if (speedSqr < 0.004 || Math.random() > 0.18) return;
        double strength = Math.max(0.0, Math.min(1.0, number(cfg, "touch.bendStrength", 0.65)));
        if (strength <= 0.0) return;
        BlockPos base = player.blockPosition();
        BlockPos plant = null;
        for (BlockPos candidate : BlockPos.betweenClosed(
                base.offset(-1, -1, -1), base.offset(1, 0, 1))) {
            var state = level.getBlockState(candidate);
            if (state.is(Blocks.SHORT_GRASS) || state.is(Blocks.FERN)
                    || state.is(BlockTags.FLOWERS)) {
                plant = candidate.immutable();
                break;
            }
        }
        if (plant == null || !canEmitTouch(cfg)) return;
        double x = plant.getX() + 0.25 + Math.random() * 0.5;
        double y = plant.getY() + 0.45 + Math.random() * 0.25;
        double z = plant.getZ() + 0.25 + Math.random() * 0.5;
        double push = Math.min(0.025, player.getDeltaMovement().horizontalDistance() * 0.08) * strength;
        level.addParticle(ParticleTypes.CHERRY_LEAVES, x, y, z,
                player.getDeltaMovement().x * push, 0.003, player.getDeltaMovement().z * push);
        touchEffectsThisTick++;
    }

    /** Enforces the per-update budget shared by all touch/environment particle effects. */
    private static boolean canEmitTouch(ConfigManager cfg) {
        int limit = Math.max(0, integer(cfg, "touch.maxEffects", 80));
        return touchEffectsThisTick < limit;
    }

    private static boolean bool(ConfigManager cfg, String id, boolean fallback) {
        Object value = cfg.getRaw(id);
        return value instanceof Boolean b ? b : fallback;
    }

    private static int integer(ConfigManager cfg, String id, int fallback) {
        Object value = cfg.getRaw(id);
        return value instanceof Number n ? n.intValue() : fallback;
    }

    private static double number(ConfigManager cfg, String id, double fallback) {
        Object value = cfg.getRaw(id);
        return value instanceof Number n ? n.doubleValue() : fallback;
    }

    private static String string(ConfigManager cfg, String id, String fallback) {
        Object value = cfg.getRaw(id);
        return value instanceof String s ? s : fallback;
    }
}
