package dev.visuals.gui;

import dev.visuals.VisualsMod;
import dev.visuals.config.def.GeneralOptions;

/** Weicher Wert, der sich in unter 200 ms auf sein Ziel zubewegt. Springt sofort, wenn Animationen aus sind. */
public final class Anim {
    private static final double DURATION_MS = 150.0;

    private double value;
    private long last = System.nanoTime();

    public Anim(double initial) {
        this.value = initial;
    }

    public double value() {
        return value;
    }

    public double step(double target) {
        long now = System.nanoTime();
        double dtMs = (now - last) / 1_000_000.0;
        last = now;
        if (!animationsEnabled() || Math.abs(target - value) < 0.001) {
            value = target;
            return value;
        }
        double t = Math.min(1.0, dtMs / DURATION_MS);
        value += (target - value) * Math.max(t, 0.08);
        return value;
    }

    private static boolean animationsEnabled() {
        return VisualsMod.config() == null || VisualsMod.config().get(GeneralOptions.GUI_ANIMATIONS);
    }
}
