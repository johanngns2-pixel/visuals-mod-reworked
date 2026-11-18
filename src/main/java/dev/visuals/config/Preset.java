package dev.visuals.config;

import java.util.Map;

/**
 * Ein Preset speichert nur die Werte, die es verändert. Alles andere bleibt beim Anwenden unberührt.
 * Bei eingebauten Presets ist {@code name} eine ID (Anzeigename: {@code visuals.preset.<id>}), bei eigenen der Name.
 */
public record Preset(String name, boolean builtin, Map<String, Object> values) {
}
