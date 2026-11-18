package dev.visuals.config;

import java.util.regex.Pattern;

/**
 * Ein Reiter im GUI.
 *
 * <p>Sprachschlüssel: {@code visuals.tab.<id>.name} und {@code visuals.tab.<id>.desc}.
 * Hat der Reiter einen Master-Schalter, liegt er als Option {@code <id>.enabled} in der Registry.
 * Das Icon wird später aus {@code assets/visuals/textures/gui/tab/<id>.png} geladen.
 */
public record TabDef(String id, boolean hasMaster) {
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z][a-z0-9]*");

    public TabDef {
        if (!ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("Ungültige Reiter-ID: '" + id + "' (nur a-z und 0-9)");
        }
    }

    public String nameKey() {
        return "visuals.tab." + id + ".name";
    }

    public String descKey() {
        return "visuals.tab." + id + ".desc";
    }

    public String masterId() {
        return id + ".enabled";
    }
}
