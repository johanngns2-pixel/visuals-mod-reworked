package dev.visuals.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wandelt Werte in das Austauschformat um und zurück: JSON-Datei oder kurzer Code zum Kopieren ({@code VIS1:...}).
 * Beim Lesen wird alles geprüft: unbekannte Optionen und unpassende Werte werden übersprungen und gezählt,
 * Werte außerhalb der Grenzen werden korrigiert.
 */
public final class PresetIO {
    public static final int FORMAT_VERSION = 1;
    private static final String CODE_PREFIX = "VIS1:";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Gson COMPACT = new GsonBuilder().disableHtmlEscaping().create();

    /** Ergebnis des Lesens. */
    public record Parsed(String name, Map<String, Object> values, int skipped, int corrected) {
    }

    private PresetIO() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static JsonObject toJson(String name, Map<String, Object> values, OptionRegistry reg) {
        JsonObject root = new JsonObject();
        root.addProperty("visualsFormat", FORMAT_VERSION);
        root.addProperty("name", name);
        JsonObject vals = new JsonObject();
        for (Map.Entry<String, Object> e : values.entrySet()) {
            OptionDef def = reg.get(e.getKey());
            if (def != null && e.getValue() != null) {
                vals.add(e.getKey(), def.codec().encode(e.getValue()));
            }
        }
        root.add("values", vals);
        return root;
    }

    public static String toPrettyJson(JsonObject json) {
        return GSON.toJson(json);
    }

    public static String toCode(JsonObject json) {
        byte[] bytes = COMPACT.toJson(json).getBytes(StandardCharsets.UTF_8);
        return CODE_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Liest Text, der entweder ein Code oder rohes JSON ist. */
    public static JsonObject parseText(String text) {
        String t = text == null ? "" : text.trim();
        try {
            if (t.startsWith(CODE_PREFIX)) {
                byte[] bytes = Base64.getUrlDecoder().decode(t.substring(CODE_PREFIX.length()));
                t = new String(bytes, StandardCharsets.UTF_8);
            }
            JsonElement el = JsonParser.parseString(t);
            if (!el.isJsonObject()) {
                throw new IllegalArgumentException("kein Objekt");
            }
            return el.getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Ungültiger Code oder ungültige Datei", e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Parsed fromJson(JsonObject root, OptionRegistry reg) {
        if (!root.has("visualsFormat") || !root.get("visualsFormat").isJsonPrimitive()
                || root.get("visualsFormat").getAsInt() > FORMAT_VERSION) {
            throw new IllegalArgumentException("Unbekanntes oder zu neues Format");
        }
        String name = root.has("name") && root.get("name").isJsonPrimitive() ? root.get("name").getAsString() : "";
        Map<String, Object> values = new LinkedHashMap<>();
        int skipped = 0;
        int corrected = 0;
        if (root.has("values") && root.get("values").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("values").entrySet()) {
                OptionDef def = reg.get(e.getKey());
                if (def == null) {
                    skipped++;
                    continue;
                }
                Object decoded = def.codec().decode(e.getValue());
                if (decoded == null) {
                    skipped++;
                    continue;
                }
                Object clean = def.sanitize(decoded);
                if (!clean.equals(decoded)) {
                    corrected++;
                }
                values.put(e.getKey(), clean);
            }
        }
        return new Parsed(name, values, skipped, corrected);
    }
}
