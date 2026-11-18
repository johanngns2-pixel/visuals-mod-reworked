package dev.visuals.gui.preview;

import dev.visuals.VisualsMod;
import dev.visuals.config.ConfigManager;
import dev.visuals.config.OptionDef;
import dev.visuals.config.def.SkyOptions;
import dev.visuals.config.def.Tabs;
import dev.visuals.gui.Theme;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Eingebaute Beispielszene (Wiese, Wald, See) mit Himmel, Sternen, Sonne, Mond, Sternschnuppen, Jahreszeit,
 * Wetter und Wind. Wird mit einfachen Flächen gezeichnet und liest die Optionen live aus der Config.
 *
 * <p>Wichtig: Das ist eine stilisierte Vorschau, kein Abbild deiner echten Welt. Sie zeigt, wie sich die
 * Einstellungen auswirken, nutzt aber nichts vom Weltrenderer.
 */
public final class ScenePreview {
    private static final int[] HILL_SPRING = {0xFF6DB354, 0xFF4F9A45};
    private static final int[] HILL_SUMMER = {0xFF4E9B3F, 0xFF367F33};
    private static final int[] HILL_AUTUMN = {0xFFC2802E, 0xFF9C5E22};
    private static final int[] HILL_WINTER = {0xFFEFF3F6, 0xFFD5DEE6};
    private static final int[] LEAF_SPRING = {0xFF7FD06A, 0xFFF2A7C8};
    private static final int[] LEAF_SUMMER = {0xFF2F7D32, 0xFF3C9440};
    private static final int[] LEAF_AUTUMN = {0xFFD9622B, 0xFFE0A02E};
    private static final int[] LEAF_WINTER = {0xFFE8EEF2, 0xFFC9D6DE};

    private ScenePreview() {
    }

    /** Liest Optionen: bei "Vorher/Nachher" oder ausgeschalteter Kategorie die Standardwerte. */
    private static final class Settings {
        private final ConfigManager cfg = VisualsMod.config();
        private final boolean vanilla;

        Settings(boolean compare) {
            this.vanilla = compare || !cfg.isEffectActive(Tabs.SKY);
        }

        <T> T v(OptionDef<T> def) {
            return vanilla ? def.defaultValue() : cfg.get(def);
        }
    }

    public static void render(GuiGraphics g, int x, int y, int w, int h, PreviewState st) {
        Settings s = new Settings(st.compare);
        double t = System.currentTimeMillis() / 1000.0;
        int horizon = y + (int) (h * 0.66);

        g.enableScissor(x, y, x + w, y + h);

        double night = nightFactor(st.hour);
        double overcast = st.weather == PreviewState.CLEAR ? 0.0 : st.weather == PreviewState.STORM ? 0.55 : 0.35;
        int sky = skyColor(s, st.hour);
        sky = Theme.mix(sky, 0xFF6C7078, overcast * (1.0 - night * 0.6));

        // Himmel als Verlauf
        int bands = Math.max(1, (horizon - y) / 4);
        for (int i = 0; i < bands; i++) {
            int y0 = y + i * 4;
            int y1 = Math.min(horizon, y0 + 4);
            double f = i / (double) bands;
            int top = Theme.mix(sky, 0xFF000000, 0.25 * (1.0 - f));
            int col = Theme.mix(top, 0xFFFFFFFF, 0.18 * f * (1.0 - night * 0.7));
            g.fill(x, y0, x + w, y1, col);
        }

        if (overcast < 0.5) {
            drawStars(g, s, x, y, w, horizon - y, night * (1.0 - overcast * 2), t);
            drawShootingStars(g, s, x, y, w, horizon - y, night * (1.0 - overcast * 2), t);
        }
        drawSunAndMoon(g, s, x, y, w, horizon - y, horizon, st.hour, sky, overcast);

        drawLandscape(g, x, y, w, h, horizon, st, night, t);
        drawWeather(g, x, y, w, h, st, t);

        g.disableScissor();
    }

    // ------------------------------------------------------------------ Himmel

    /** 0 am Tag, 1 in der tiefen Nacht, weicher Übergang in der Dämmerung. */
    private static double nightFactor(double hour) {
        if (hour >= 7 && hour <= 17) {
            return 0;
        }
        if (hour <= 4.5 || hour >= 20.5) {
            return 1;
        }
        if (hour < 7) {
            return (7 - hour) / 2.5;
        }
        return (hour - 17) / 3.5;
    }

    private static int skyColor(Settings s, double hour) {
        boolean custom = s.v(SkyOptions.SKY_CUSTOM);
        int night = custom ? s.v(SkyOptions.SKY_NIGHT) : SkyOptions.SKY_NIGHT.defaultValue();
        int dawn = custom ? s.v(SkyOptions.SKY_DAWN) : SkyOptions.SKY_DAWN.defaultValue();
        int day = custom ? s.v(SkyOptions.SKY_DAY) : SkyOptions.SKY_DAY.defaultValue();
        int dusk = custom ? s.v(SkyOptions.SKY_DUSK) : SkyOptions.SKY_DUSK.defaultValue();
        double[] at = {0, 4.5, 6.5, 9, 16, 18, 20.5, 24};
        int[] col = {night, night, dawn, day, day, dusk, night, night};
        for (int i = 0; i < at.length - 1; i++) {
            if (hour <= at[i + 1]) {
                double f = (hour - at[i]) / (at[i + 1] - at[i]);
                return Theme.mix(col[i], col[i + 1], Math.max(0, Math.min(1, f)));
            }
        }
        return night;
    }

    private static void drawStars(GuiGraphics g, Settings s, int x, int y, int w, int skyH, double vis, double t) {
        if (vis <= 0.02) {
            return;
        }
        int count = (int) Math.round(s.v(SkyOptions.STAR_AMOUNT) * 70);
        int size = Math.max(1, (int) Math.round(s.v(SkyOptions.STAR_SIZE)));
        int color = s.v(SkyOptions.STAR_COLOR) & 0xFFFFFF;
        double twinkle = s.v(SkyOptions.STAR_TWINKLE);
        long seed = 12345;
        for (int i = 0; i < count; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            int px = x + (int) (((seed >>> 33) & 0x7FFFFFF) % Math.max(1, w));
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            int py = y + (int) (((seed >>> 33) & 0x7FFFFFF) % Math.max(1, (int) (skyH * 0.9)));
            double flick = 1.0 - twinkle * 0.5 * (1.0 + Math.sin(t * 3.0 + i * 1.7));
            int a = (int) Math.round(255 * vis * flick);
            if (a > 4) {
                g.fill(px, py, px + size, py + size, (a << 24) | color);
            }
        }
    }

    private static void drawShootingStars(GuiGraphics g, Settings s, int x, int y, int w, int skyH, double vis, double t) {
        double freq = s.v(SkyOptions.SHOOTING_FREQUENCY);
        if (freq <= 0.01 || vis < 0.5) {
            return;
        }
        double period = 60.0 / freq;
        long slot = (long) Math.floor(t / period);
        double local = t - slot * period;
        double life = 0.9;
        if (local > life) {
            return;
        }
        long seed = slot * 2654435761L + 17;
        double sx = x + ((seed >>> 3) % 1000) / 1000.0 * w * 0.7;
        double sy = y + ((seed >>> 13) % 1000) / 1000.0 * skyH * 0.4;
        double p = local / life;
        int trail = s.v(SkyOptions.SHOOTING_TRAIL);
        int color = s.v(SkyOptions.SHOOTING_COLOR) & 0xFFFFFF;
        double dx = 1.0;
        double dy = 0.55;
        double headX = sx + p * w * 0.35;
        double headY = sy + p * w * 0.35 * dy;
        for (int i = 0; i < trail; i++) {
            double f = 1.0 - i / (double) trail;
            int a = (int) (255 * f * (1.0 - p * 0.6));
            int px = (int) Math.round(headX - i * dx * 2);
            int py = (int) Math.round(headY - i * dy * 2);
            g.fill(px, py, px + 2, py + 2, (Math.max(0, a) << 24) | color);
        }
    }

    private static void drawSunAndMoon(GuiGraphics g, Settings s, int x, int y, int w, int skyH, int horizon,
                                       double hour, int skyColor, double overcast) {
        double alpha = 1.0 - overcast * 0.8;
        if (hour >= 5.5 && hour <= 18.5) {
            double p = (hour - 5.5) / 13.0;
            int cx = x + (int) (p * w);
            int cy = horizon - (int) (Math.sin(p * Math.PI) * skyH * 0.78) - 4;
            int r = (int) Math.round(7 * s.v(SkyOptions.SUN_SIZE));
            drawBody(g, cx, cy, r, s.v(SkyOptions.SUN_COLOR), s.v(SkyOptions.SUN_SHAPE), alpha);
        }
        double mh = hour < 12 ? hour + 24 : hour;
        if (mh >= 17.5 && mh <= 30.5) {
            double p = (mh - 17.5) / 13.0;
            int cx = x + (int) (p * w);
            int cy = horizon - (int) (Math.sin(p * Math.PI) * skyH * 0.78) - 4;
            int r = (int) Math.round(6 * s.v(SkyOptions.MOON_SIZE));
            int color = s.v(SkyOptions.MOON_COLOR);
            drawBody(g, cx, cy, r, color, "square", alpha);
            if (s.v(SkyOptions.MOON_PHASES)) {
                // Sichel: Himmelsfarbe über einen Teil des Mondes legen
                g.fill(cx + r / 3, cy - r, cx + r + 1, cy + r, 0xFF000000 | Theme.mix(skyColor, 0xFF000000, 0.25));
            }
        }
    }

    private static void drawBody(GuiGraphics g, int cx, int cy, int r, int argb, String shape, double alpha) {
        int a = (int) Math.round(255 * Math.max(0, Math.min(1, alpha)));
        int col = (a << 24) | (argb & 0xFFFFFF);
        switch (shape) {
            case "round" -> {
                for (int dy = -r; dy <= r; dy++) {
                    int dx = (int) Math.round(Math.sqrt(Math.max(0, (double) r * r - (double) dy * dy)));
                    g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, col);
                }
            }
            case "star" -> {
                g.fill(cx - r, cy - 1, cx + r + 1, cy + 2, col);
                g.fill(cx - 1, cy - r, cx + 2, cy + r + 1, col);
                int q = Math.max(1, r / 2);
                g.fill(cx - q, cy - q, cx + q + 1, cy + q + 1, col);
            }
            default -> g.fill(cx - r, cy - r, cx + r + 1, cy + r + 1, col);
        }
    }

    // ------------------------------------------------------------------ Boden

    private static void drawLandscape(GuiGraphics g, int x, int y, int w, int h, int horizon, PreviewState st,
                                      double night, double t) {
        int[] hills = switch (st.season) {
            case PreviewState.SPRING -> HILL_SPRING;
            case PreviewState.AUTUMN -> HILL_AUTUMN;
            case PreviewState.WINTER -> HILL_WINTER;
            default -> HILL_SUMMER;
        };
        int[] leaves = switch (st.season) {
            case PreviewState.SPRING -> LEAF_SPRING;
            case PreviewState.AUTUMN -> LEAF_AUTUMN;
            case PreviewState.WINTER -> LEAF_WINTER;
            default -> LEAF_SUMMER;
        };
        double dim = 1.0 - 0.62 * night;
        int bottom = y + h;

        // hintere und vordere Hügel
        for (int layer = 0; layer < 2; layer++) {
            int base = horizon - (layer == 0 ? 10 : 0);
            int col = dim(hills[layer], dim);
            for (int cx = x; cx < x + w; cx += 2) {
                double hh = Math.sin((cx - x) * (layer == 0 ? 0.03 : 0.05) + layer * 2.0) * (layer == 0 ? 9 : 5)
                        + Math.sin((cx - x) * 0.013 + layer) * 6;
                g.fill(cx, base - (int) hh, Math.min(cx + 2, x + w), bottom, col);
            }
        }

        // See
        int lakeY = horizon + (int) ((bottom - horizon) * 0.45);
        int lakeH = Math.max(8, (int) ((bottom - lakeY) * 0.55));
        int lakeX = x + (int) (w * 0.12);
        int lakeW = (int) (w * 0.5);
        int water = Theme.mix(dim(0xFF3C6FA3, dim), 0xFFFFFFFF, st.season == PreviewState.WINTER ? 0.55 : 0.0);
        g.fill(lakeX, lakeY, lakeX + lakeW, lakeY + lakeH, water);
        for (int i = 0; i < 4; i++) {
            int wx = lakeX + 6 + (int) ((Math.sin(t * 0.8 + i * 2.1) * 0.5 + 0.5) * (lakeW - 24));
            g.fill(wx, lakeY + 2 + i * 3, wx + 10, lakeY + 3 + i * 3, 0x30FFFFFF);
        }

        // Bäume
        double sway = st.wind * 3.0;
        int[] treeX = {(int) (w * 0.72), (int) (w * 0.82), (int) (w * 0.9), (int) (w * 0.66), (int) (w * 0.05)};
        int[] treeS = {22, 28, 20, 16, 24};
        for (int i = 0; i < treeX.length; i++) {
            int tx = x + treeX[i];
            int ts = treeS[i];
            int ty = horizon + 6 + (i % 2) * 8;
            int off = (int) Math.round(Math.sin(t * 2.0 + i) * sway);
            g.fill(tx - 2, ty - ts / 2, tx + 3, ty + 4, dim(0xFF5B3F26, dim));
            int leaf = dim(leaves[i % 2], dim);
            g.fill(tx - ts / 2 + off, ty - ts, tx + ts / 2 + off, ty - ts / 2 + 2, leaf);
            g.fill(tx - ts / 3 + off, ty - ts - 5, tx + ts / 3 + off, ty - ts, leaf);
        }

        // Gras-Halme, die sich im Wind biegen
        for (int cx = x + 4; cx < x + w - 4; cx += 7) {
            double bend = Math.sin(t * 2.4 + cx * 0.15) * st.wind * 3.0;
            int gy = bottom - 2 - (cx % 5);
            g.fill(cx + (int) Math.round(bend), gy - 6, cx + 1 + (int) Math.round(bend), gy, dim(hills[1], dim * 0.9));
        }
    }

    private static int dim(int argb, double f) {
        int r = (int) (((argb >> 16) & 255) * f);
        int gr = (int) (((argb >> 8) & 255) * f);
        int b = (int) ((argb & 255) * f);
        return 0xFF000000 | (r << 16) | (gr << 8) | b;
    }

    // ------------------------------------------------------------------ Wetter

    private static void drawWeather(GuiGraphics g, int x, int y, int w, int h, PreviewState st, double t) {
        if (st.weather == PreviewState.CLEAR) {
            return;
        }
        boolean snow = st.weather == PreviewState.SNOW;
        int drops = snow ? 70 : 110;
        double slant = st.wind * (snow ? 14 : 10);
        for (int i = 0; i < drops; i++) {
            double fx = (i * 0.61803398875) % 1.0;
            double speed = snow ? 0.14 + (i % 5) * 0.02 : 0.9 + (i % 4) * 0.12;
            double fy = (t * speed + (i * 0.37)) % 1.0;
            int px = x + (int) ((fx * w + fy * slant * 4 + (snow ? Math.sin(t + i) * 4 : 0)) % w);
            int py = y + (int) (fy * h);
            if (snow) {
                g.fill(px, py, px + 2, py + 2, 0xE0FFFFFF);
            } else {
                int dx = (int) Math.round(slant * 0.3);
                g.fill(px, py, px + 1, py + 5, 0x907FA8D8);
                if (dx != 0) {
                    g.fill(px + dx, py + 5, px + dx + 1, py + 7, 0x607FA8D8);
                }
            }
        }
        if (st.weather == PreviewState.STORM) {
            double c = t % 6.0;
            if (c < 0.12 || (c > 0.3 && c < 0.36)) {
                g.fill(x, y, x + w, y + h, 0x55FFFFFF);
            }
        }
    }
}
