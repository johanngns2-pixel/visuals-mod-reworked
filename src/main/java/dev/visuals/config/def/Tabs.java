package dev.visuals.config.def;

import dev.visuals.config.OptionRegistry;
import dev.visuals.config.TabDef;

/**
 * Alle Reiter in der Standardreihenfolge. Ein neuer Reiter braucht genau eine Zeile hier
 * plus seine Texte in den Sprachdateien (visuals.tab.&lt;id&gt;.name und .desc).
 */
public final class Tabs {
    public static final String GENERAL = "general";
    public static final String SKY = "sky";
    public static final String GLINT = "glint";
    public static final String SEASONS = "seasons";
    public static final String WEATHER = "weather";
    public static final String LIGHT = "light";
    public static final String FOLIAGE = "foliage";
    public static final String FIREFLIES = "fireflies";
    public static final String PUDDLES = "puddles";
    public static final String WILDLIFE = "wildlife";
    public static final String BIOMES = "biomes";
    public static final String EVENTS = "events";
    public static final String TOUCH = "touch";
    public static final String SOUND = "sound";
    public static final String AFK = "afk";
    public static final String SPOTIFY = "spotify";

    private Tabs() {
    }

    public static void registerAll(OptionRegistry registry) {
        // "Allgemein" hat keinen Master-Schalter, dort sitzt der globale Notausschalter.
        registry.registerTab(new TabDef(GENERAL, false));
        registry.registerTab(new TabDef(SKY, true));
        registry.registerTab(new TabDef(GLINT, true));
        registry.registerTab(new TabDef(SEASONS, true));
        registry.registerTab(new TabDef(WEATHER, true));
        registry.registerTab(new TabDef(LIGHT, true));
        registry.registerTab(new TabDef(FOLIAGE, true));
        registry.registerTab(new TabDef(FIREFLIES, true));
        registry.registerTab(new TabDef(PUDDLES, true));
        registry.registerTab(new TabDef(WILDLIFE, true));
        registry.registerTab(new TabDef(BIOMES, true));
        registry.registerTab(new TabDef(EVENTS, true));
        registry.registerTab(new TabDef(TOUCH, true));
        registry.registerTab(new TabDef(SOUND, true));
        registry.registerTab(new TabDef(AFK, true));
        registry.registerTab(new TabDef(SPOTIFY, true));
    }
}
