package dev.visuals.gui;

import dev.visuals.VisualsMod;
import dev.visuals.config.def.GeneralOptions;

/** Farben des Menüs. Die Akzentfarbe kommt aus der Config. */
public final class Theme {
    public static final int OVERLAY = 0x80000000;
    public static final int PANEL = 0xF0151518;
    public static final int PANEL_LIGHT = 0xF01E1E23;
    public static final int ROW = 0xFF202026;
    public static final int ROW_HOVER = 0xFF2A2A32;
    public static final int BORDER = 0xFF33333B;
    public static final int TEXT = 0xFFE8E8EC;
    public static final int TEXT_DIM = 0xFF9A9AA4;
    public static final int TEXT_DISABLED = 0xFF5E5E66;
    public static final int GREEN = 0xFF4CC468;
    public static final int GRAY = 0xFF6B6B73;

    private Theme() {
    }

    public static int accent() {
        if (VisualsMod.config() == null) {
            return GeneralOptions.ACCENT_COLOR.defaultValue();
        }
        return VisualsMod.config().get(GeneralOptions.ACCENT_COLOR) | 0xFF000000;
    }

    /** Mischt zwei ARGB-Farben, t = 0 ergibt a, t = 1 ergibt b. */
    public static int mix(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
