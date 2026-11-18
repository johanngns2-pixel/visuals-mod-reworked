package dev.visuals.effects;

import dev.visuals.VisualsMod;
import dev.visuals.config.ConfigManager;
import dev.visuals.config.def.Tabs;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
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
        if (cfg.isEffectActive(Tabs.FIREFLIES)) {
            fireflies(cfg, level, player);
        }
        if (cfg.isEffectActive(Tabs.WILDLIFE)) {
            wildlife(cfg, level, player);
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
        if (level.getMaxLocalRawBrightness(pos) > 10) return;
        double drift = (Math.random() - 0.5) * 0.012;
        level.addParticle(ParticleTypes.END_ROD, x, y, z, drift, 0.006, drift);
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
