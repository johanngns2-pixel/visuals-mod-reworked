package dev.visuals.gui.preview;

import dev.visuals.gui.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Rechte Spalte: Szene plus Regler für Tageszeit, Jahreszeit, Wetter und Wind (nur für die Vorschau),
 * dazu "Vollbild-Vorschau" und "Vorher/Nachher" (gedrückt halten).
 */
public final class PreviewPanel {
    private static final int CONTROLS_H = 104;

    @FunctionalInterface
    private interface Drag {
        void on(double mx);
    }

    private record Hit(int x, int y, int w, int h, Runnable click, Drag drag) {
    }

    private final PreviewState state = new PreviewState();
    private final List<Hit> hits = new ArrayList<>();
    private Drag activeDrag;
    private boolean fullscreenRequested;

    public PreviewState state() {
        return state;
    }

    /** Wurde "Vollbild-Vorschau" geklickt? Der Aufruf setzt den Wunsch zurück. */
    public boolean consumeFullscreenRequest() {
        boolean r = fullscreenRequested;
        fullscreenRequested = false;
        return r;
    }

    /** Zeichnet die Szene in den ganzen Bereich (Vollbild-Modus, ohne Regler). */
    public void renderSceneOnly(GuiGraphics g, int x, int y, int w, int h) {
        ScenePreview.render(g, x, y, w, h, state);
    }

    public void render(GuiGraphics g, Font font, int mx, int my, int x, int y, int w, int h) {
        hits.clear();
        int sceneH = Math.max(60, h - CONTROLS_H);
        ScenePreview.render(g, x + 6, y + 6, w - 12, sceneH - 6, state);
        if (state.compare) {
            String label = I18n.get("visuals.gui.preview.vanilla");
            g.fill(x + 10, y + 10, x + 14 + font.width(label) + 6, y + 24, 0xB0000000);
            g.drawString(font, label, x + 14, y + 13, Theme.TEXT);
        }

        int cy = y + sceneH + 6;
        int lx = x + 8;
        int cw = w - 16;

        // Tageszeit
        slider(g, font, mx, my, lx, cy, cw, I18n.get("visuals.gui.preview.time"), state.hour / 24.0,
                String.format(Locale.ROOT, "%02d:%02d", (int) state.hour, (int) ((state.hour % 1.0) * 60)),
                f -> state.hour = Math.round(f * 96.0) / 4.0);
        cy += 18;
        // Jahreszeit
        segmented(g, font, mx, my, lx, cy, cw, state.season,
                new String[]{"visuals.gui.season.spring", "visuals.gui.season.summer", "visuals.gui.season.autumn",
                        "visuals.gui.season.winter"}, i -> state.season = i);
        cy += 20;
        // Wetter
        segmented(g, font, mx, my, lx, cy, cw, state.weather,
                new String[]{"visuals.gui.weather.clear", "visuals.gui.weather.rain", "visuals.gui.weather.snow",
                        "visuals.gui.weather.storm"}, i -> state.weather = i);
        cy += 20;
        // Wind
        slider(g, font, mx, my, lx, cy, cw, I18n.get("visuals.gui.preview.wind"), state.wind,
                String.format(Locale.ROOT, "%d %%", (int) Math.round(state.wind * 100)),
                f -> state.wind = Math.round(f * 20.0) / 20.0);
        cy += 20;

        // Knöpfe
        int bw = (cw - 6) / 2;
        button(g, font, mx, my, lx, cy, bw, 16, I18n.get("visuals.gui.preview.fullscreen"), false,
                () -> fullscreenRequested = true);
        button(g, font, mx, my, lx + bw + 6, cy, bw, 16, I18n.get("visuals.gui.preview.compare"), state.compare, null);
        // "Vorher/Nachher" gilt nur, solange die Maustaste gehalten wird
        hits.add(new Hit(lx + bw + 6, cy, bw, 16, () -> {
            state.compare = true;
            activeDrag = m -> {
            };
        }, null));
    }

    private void slider(GuiGraphics g, Font font, int mx, int my, int x, int y, int w, String label, double frac,
                        String valueText, java.util.function.DoubleConsumer set) {
        g.drawString(font, label, x, y + 3, Theme.TEXT_DIM);
        int tx = x + 62;
        int tw = w - 62 - 40;
        g.fill(tx, y + 7, tx + tw, y + 9, Theme.BORDER);
        g.fill(tx, y + 7, tx + (int) Math.round(frac * tw), y + 9, Theme.accent());
        int kx = tx + (int) Math.round(frac * (tw - 4));
        g.fill(kx, y + 3, kx + 4, y + 13, 0xFFF4F4F6);
        g.drawString(font, valueText, x + w - font.width(valueText), y + 3, Theme.TEXT);
        Drag drag = m -> set.accept(Math.max(0, Math.min(1, (m - tx - 2) / (tw - 4.0))));
        hits.add(new Hit(tx - 2, y, tw + 4, 14, () -> {
        }, drag));
    }

    private void segmented(GuiGraphics g, Font font, int mx, int my, int x, int y, int w, int selected, String[] keys,
                           java.util.function.IntConsumer set) {
        int n = keys.length;
        int sw = w / n;
        for (int i = 0; i < n; i++) {
            int bx = x + i * sw;
            boolean sel = i == selected;
            boolean hov = Theme.inside(mx, my, bx, y, sw - 2, 16);
            int bg = sel ? Theme.accent() : Theme.mix(Theme.ROW, Theme.accent(), hov ? 0.3 : 0.0);
            g.fill(bx, y, bx + sw - 2, y + 16, bg);
            String text = font.plainSubstrByWidth(I18n.get(keys[i]), sw - 8);
            g.drawString(font, text, bx + (sw - 2 - font.width(text)) / 2, y + 4, sel ? 0xFF111111 : Theme.TEXT);
            final int idx = i;
            hits.add(new Hit(bx, y, sw - 2, 16, () -> set.accept(idx), null));
        }
    }

    private void button(GuiGraphics g, Font font, int mx, int my, int x, int y, int w, int h, String label,
                        boolean pressed, Runnable click) {
        boolean hov = Theme.inside(mx, my, x, y, w, h);
        g.fill(x, y, x + w, y + h, Theme.mix(Theme.ROW, Theme.accent(), pressed ? 0.7 : hov ? 0.4 : 0.15));
        String text = font.plainSubstrByWidth(label, w - 6);
        g.drawString(font, text, x + (w - font.width(text)) / 2, y + (h - 8) / 2, Theme.TEXT);
        if (click != null) {
            hits.add(new Hit(x, y, w, h, click, null));
        }
    }

    // ------------------------------------------------------------------ Eingaben

    public boolean mouseClicked(double mx, double my) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (Theme.inside(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) {
                hit.click().run();
                if (hit.drag() != null) {
                    activeDrag = hit.drag();
                    activeDrag.on(mx);
                }
                return true;
            }
        }
        return false;
    }

    public boolean mouseDragged(double mx) {
        if (activeDrag == null) {
            return false;
        }
        activeDrag.on(mx);
        return true;
    }

    public void mouseReleased() {
        activeDrag = null;
        state.compare = false;
    }
}
