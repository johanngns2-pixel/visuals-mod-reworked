package dev.visuals.gui.preview;

/** Werte, die nur für die Vorschau gelten (nicht in der Config): Tageszeit, Jahreszeit, Wetter, Wind. */
public final class PreviewState {
    public static final int SPRING = 0;
    public static final int SUMMER = 1;
    public static final int AUTUMN = 2;
    public static final int WINTER = 3;

    public static final int CLEAR = 0;
    public static final int RAIN = 1;
    public static final int SNOW = 2;
    public static final int STORM = 3;

    /** Stunde, 0 bis 24. */
    public double hour = 21.5;
    public int season = SUMMER;
    public int weather = CLEAR;
    /** 0 bis 1 */
    public double wind = 0.4;
    /** Solange true, zeigt die Vorschau die Vanilla-Optik ohne Effekte ("Vorher/Nachher"). */
    public boolean compare;
}
