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
        if (cfg.isEffectActive(Tabs.TOUCH)) {
            waterRings(cfg, level, player);
            groundDust(cfg, level, player);
            snowSteps(cfg, level, player);
            surfaceSteps(cfg, level, player);
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

        int amount = integer(cfg, "fireflies.amount", 24);
        if (amount <= 0) return;
        double chance = Math.min(0.8, amount / 180.0);
        if (Math.random() > chance) return;

        double range = 7.0;
        double x = player.getX() + (Math.random() - 0.5) * range * 2.0;
        double y = player.getY() + 1.0 + Math.random() * 3.0;
        double z = player.getZ() + (Math.random() - 0.5) * range * 2.0;
        BlockPos pos = BlockPos.containing(x, y, z);
        boolean nearTorch = bool(cfg, "fireflies.nearTorches", true) && hasNearbyTorch(level, pos);
        if (level.getMaxLocalRawBrightness(pos) > 10 && !nearTorch) return;
        double drift = (Math.random() - 0.5) * 0.012;
        int color = integer(cfg, Math.random() < 0.5 ? "fireflies.primaryColor" : "fireflies.secondaryColor",
                0xFFFFE78A);
        float glow = (float) Math.max(0.15, Math.min(1.5, number(cfg, "fireflies.glow", 0.8)));
        level.addParticle(new DustParticleOptions(color, glow),
                x, y, z, drift, 0.006, drift);
    }

    private static void wildlife(ConfigManager cfg, Level level, Player player) {
        int amount = integer(cfg, "wildlife.amount", 12);
        if (amount <= 0 || Math.random() > Math.min(0.35, amount / 160.0)) return;
        boolean day = level.getDayTime() % 24000L < 12000L;
        if (bool(cfg, "wildlife.dayNightCycle", true) && !day) return;

        String type;
        if (bool(cfg, "wildlife.butterflies", true)) type = "butterfly";
        else if (bool(cfg, "wildlife.dragonflies", true)) type = "dragonfly";
        else if (bool(cfg, "wildlife.birds", true)) type = "bird";
        else return;

        double range = Math.max(8.0, Math.min(32.0, number(cfg, "wildlife.spawnRange", 24.0)));
        double x = player.getX() + (Math.random() - 0.5) * range * 2.0;
        double y = player.getY() + 1.5 + Math.random() * 4.0;
        double z = player.getZ() + (Math.random() - 0.5) * range * 2.0;
        double drift = "bird".equals(type) ? 0.025 : 0.006;
        level.addParticle(ParticleTypes.HAPPY_VILLAGER, x, y, z,
                (Math.random() - 0.5) * drift, 0.002, (Math.random() - 0.5) * drift);
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
     * Uses the vanilla cherry-leaf particle around actual leaf blocks. This is a visual
     * approximation (not a new particle texture) and never changes blocks.
     */
    private static void fallingLeaves(ConfigManager cfg, Level level, Player player) {
        if (!bool(cfg, "foliage.fallingLeaves", true)) return;
        int amount = integer(cfg, "foliage.leafAmount", 24);
        int particleCap = integer(cfg, "foliage.particleCap", 500);
        if (amount <= 0 || particleCap <= 0) return;

        int range = Math.max(8, Math.min(64, integer(cfg, "foliage.renderRange", 32)));
        int attempts = Math.max(1, Math.min(18, Math.min(amount / 3, particleCap)));
        double speed = Math.max(0.1, Math.min(2.0, number(cfg, "foliage.fallSpeed", 0.7)));
        // Reuse the weather wind controls to influence the horizontal drift of falling leaves.
        double windStrength = Math.max(0.0, Math.min(1.0, number(cfg, "weather.windStrength", 0.35)));
        double windRadians = Math.toRadians(number(cfg, "weather.windDirection", 0.0));
        double windX = Math.sin(windRadians) * windStrength * 0.018;
        double windZ = Math.cos(windRadians) * windStrength * 0.018;
        boolean gusts = bool(cfg, "weather.windGusts", true);
        for (int i = 0; i < attempts; i++) {
            int x = player.blockPosition().getX() + (int) (Math.random() * range * 2 - range);
            int y = player.blockPosition().getY() + (int) (Math.random() * 12 - 4);
            int z = player.blockPosition().getZ() + (int) (Math.random() * range * 2 - range);
            BlockPos leafPos = new BlockPos(x, y, z);
            if (!level.getBlockState(leafPos).is(BlockTags.LEAVES)) continue;

            double px = x + Math.random();
            double py = y + 0.85 + Math.random() * 0.25;
            double pz = z + Math.random();
            double gust = gusts ? 0.75 + Math.random() * 0.5 : 1.0;
            double driftX = (Math.random() - 0.5) * 0.012 * speed + windX * gust;
            double driftZ = (Math.random() - 0.5) * 0.012 * speed + windZ * gust;
            level.addParticle(ParticleTypes.CHERRY_LEAVES, px, py, pz,
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
