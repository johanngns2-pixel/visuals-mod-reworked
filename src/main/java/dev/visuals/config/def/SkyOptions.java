package dev.visuals.config.def;

import dev.visuals.config.OptionDef;
import dev.visuals.config.OptionRegistry;

/**
 * Optionen des Reiters "Himmel". Die Tageszeit-Kurve für die Himmelsfarben kommt später als eigener Typ
 * (Kurveneditor); bis dahin gibt es vier feste Farben für Morgen, Tag, Abend und Nacht.
 */
public final class SkyOptions {
    // Sterne
    public static final OptionDef<Double> STAR_AMOUNT =
            OptionDef.slider("sky.starAmount", 1.0, 0.0, 3.0, 0.05).group("stars").build();
    public static final OptionDef<Double> STAR_SIZE =
            OptionDef.slider("sky.starSize", 1.0, 0.5, 3.0, 0.05).group("stars").build();
    public static final OptionDef<Integer> STAR_COLOR =
            OptionDef.color("sky.starColor", 0xFFFFFFFF).group("stars").build();
    public static final OptionDef<Double> STAR_TWINKLE =
            OptionDef.slider("sky.starTwinkle", 0.4, 0.0, 1.0, 0.05).group("stars").build();

    // Sonne und Mond
    public static final OptionDef<Double> SUN_SIZE =
            OptionDef.slider("sky.sunSize", 1.0, 0.25, 3.0, 0.05).group("sunmoon").build();
    public static final OptionDef<Integer> SUN_COLOR =
            OptionDef.color("sky.sunColor", 0xFFFFF2C0).group("sunmoon").build();
    public static final OptionDef<String> SUN_SHAPE =
            OptionDef.dropdown("sky.sunShape", "square", "square", "round", "star").group("sunmoon").build();
    public static final OptionDef<Double> MOON_SIZE =
            OptionDef.slider("sky.moonSize", 1.0, 0.25, 3.0, 0.05).group("sunmoon").build();
    public static final OptionDef<Integer> MOON_COLOR =
            OptionDef.color("sky.moonColor", 0xFFE6ECFF).group("sunmoon").build();
    public static final OptionDef<Boolean> MOON_PHASES =
            OptionDef.toggle("sky.moonPhases", true).group("sunmoon").build();

    // Sternschnuppen
    public static final OptionDef<Double> SHOOTING_FREQUENCY =
            OptionDef.slider("sky.shootingFrequency", 1.0, 0.0, 10.0, 0.1).group("shooting").unit("/min").build();
    public static final OptionDef<Integer> SHOOTING_COLOR =
            OptionDef.color("sky.shootingColor", 0xFFFFFFFF).group("shooting").build();
    public static final OptionDef<Integer> SHOOTING_TRAIL =
            OptionDef.intSlider("sky.shootingTrail", 12, 2, 60).group("shooting").build();

    // Himmelsfarben
    public static final OptionDef<Boolean> SKY_CUSTOM =
            OptionDef.toggle("sky.skyCustom", false).group("skycolors").build();
    public static final OptionDef<Integer> SKY_DAWN =
            OptionDef.color("sky.skyDawn", 0xFFE8946A).group("skycolors").enabledWhen("sky.skyCustom").build();
    public static final OptionDef<Integer> SKY_DAY =
            OptionDef.color("sky.skyDay", 0xFF78A7FF).group("skycolors").enabledWhen("sky.skyCustom").build();
    public static final OptionDef<Integer> SKY_DUSK =
            OptionDef.color("sky.skyDusk", 0xFFC96B4F).group("skycolors").enabledWhen("sky.skyCustom").build();
    public static final OptionDef<Integer> SKY_NIGHT =
            OptionDef.color("sky.skyNight", 0xFF0B1030).group("skycolors").enabledWhen("sky.skyCustom").build();

    private SkyOptions() {
    }

    public static void register(OptionRegistry r) {
        r.register(STAR_AMOUNT);
        r.register(STAR_SIZE);
        r.register(STAR_COLOR);
        r.register(STAR_TWINKLE);
        r.register(SUN_SIZE);
        r.register(SUN_COLOR);
        r.register(SUN_SHAPE);
        r.register(MOON_SIZE);
        r.register(MOON_COLOR);
        r.register(MOON_PHASES);
        r.register(SHOOTING_FREQUENCY);
        r.register(SHOOTING_COLOR);
        r.register(SHOOTING_TRAIL);
        r.register(SKY_CUSTOM);
        r.register(SKY_DAWN);
        r.register(SKY_DAY);
        r.register(SKY_DUSK);
        r.register(SKY_NIGHT);
    }
}
