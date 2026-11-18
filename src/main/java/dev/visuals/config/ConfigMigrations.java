package dev.visuals.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Map;
import java.util.TreeMap;
import java.util.function.UnaryOperator;

/**
 * Schrittweise Migration der Config-Datei. Der Schritt unter dem Schlüssel {@code n} wandelt Version
 * {@code n} in Version {@code n + 1} um.
 *
 * <p>Neue Migration anlegen: {@link ConfigManager#CURRENT_VERSION} um eins erhöhen und hier einen Schritt
 * eintragen, zum Beispiel
 * <pre>{@code STEPS.put(1, root -> { renameValue(root, "sky.stars", "sky.starAmount"); return root; });}</pre>
 */
public final class ConfigMigrations {
    private static final Map<Integer, UnaryOperator<JsonObject>> STEPS = new TreeMap<>();

    static {
        // Noch keine Migrationen: Version 1 ist das erste Format.
    }

    private ConfigMigrations() {
    }

    public static JsonObject migrate(JsonObject root, int from, int to) {
        return migrate(root, from, to, STEPS);
    }

    static JsonObject migrate(JsonObject root, int from, int to, Map<Integer, UnaryOperator<JsonObject>> steps) {
        JsonObject current = root;
        for (int version = from; version < to; version++) {
            UnaryOperator<JsonObject> step = steps.get(version);
            if (step != null) {
                current = step.apply(current);
            }
        }
        current.addProperty("configVersion", to);
        return current;
    }

    /** Hilfsfunktion für Migrationen: benennt eine Option in {@code values} um (falls vorhanden). */
    public static void renameValue(JsonObject root, String oldId, String newId) {
        if (!root.has("values") || !root.get("values").isJsonObject()) {
            return;
        }
        JsonObject values = root.getAsJsonObject("values");
        JsonElement old = values.remove(oldId);
        if (old != null && !values.has(newId)) {
            values.add(newId, old);
        }
    }
}
