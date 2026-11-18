package dev.visuals.config.def;

import dev.visuals.config.ConfigManager;
import dev.visuals.config.OptionDef;
import dev.visuals.config.OptionRegistry;

/**
 * Optionen des Reiters "Allgemein".
 *
 * <p>Noch offen für spätere Abschnitte: aktives Preset (mit den Presets) und Reiter-Reihenfolge
 * (mit der Reiter-Leiste). Beide brauchen eigene Typen und werden dort ergänzt.
 */
public final class GeneralOptions {
    public static final OptionDef<Boolean> MOD_ENABLED =
            OptionDef.toggle(ConfigManager.MOD_ENABLED_ID, true).group("main").build();

    public static final OptionDef<String> LANGUAGE =
            OptionDef.dropdown("general.language", "auto", "auto", "en_us", "de_de").group("main").build();

    public static final OptionDef<Integer> ACCENT_COLOR =
            OptionDef.color("general.accentColor", 0xFFE8892B).group("appearance").build();

    public static final OptionDef<Boolean> GUI_ANIMATIONS =
            OptionDef.toggle("general.guiAnimations", true).group("appearance").build();

    public static final OptionDef<Boolean> PERFORMANCE_MODE =
            OptionDef.toggle("general.performanceMode", false).group("performance").build();

    public static final OptionDef<Integer> PERFORMANCE_TARGET_FPS =
            OptionDef.intSlider("general.performanceTargetFps", 40, 15, 120)
                    .group("performance")
                    .unit("FPS")
                    .enabledWhen("general.performanceMode")
                    .build();

    private GeneralOptions() {
    }

    public static void register(OptionRegistry registry) {
        registry.register(MOD_ENABLED);
        registry.register(LANGUAGE);
        registry.register(ACCENT_COLOR);
        registry.register(GUI_ANIMATIONS);
        registry.register(PERFORMANCE_MODE);
        registry.register(PERFORMANCE_TARGET_FPS);
    }
}
