package dev.visuals.config;

/**
 * Welche Art von Widget das GUI für eine Option baut.
 * Weitere Typen (Keybind, Liste, Matrix, Kurve, Verlauf) kommen mit den jeweiligen Abschnitten dazu.
 */
public enum OptionType {
    /** Boolean */
    TOGGLE,
    /** Double mit Minimum, Maximum und Schrittweite */
    SLIDER,
    /** Integer mit Minimum und Maximum */
    INT_SLIDER,
    /** String aus einer festen Auswahl */
    DROPDOWN,
    /** Integer im Format ARGB */
    COLOR,
    /** Freier String mit maximaler Länge */
    TEXT
}
