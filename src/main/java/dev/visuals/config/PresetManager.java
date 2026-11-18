package dev.visuals.config;

import dev.visuals.config.def.BuiltinPresets;
import dev.visuals.config.def.GeneralOptions;
import dev.visuals.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Eingebaute und eigene Presets, Anwenden mit sanfter Überblendung (ca. 400 ms) und Speichern eigener Presets
 * als Datei unter {@code config/visuals/presets/}.
 */
public final class PresetManager {
    private static final long FADE_MS = 400;
    /** Diese Optionen gehören nie in ein Preset. */
    private static final List<String> EXCLUDED = List.of(ConfigManager.MOD_ENABLED_ID, "general.language");

    private final OptionRegistry reg;
    private final ConfigManager cfg;
    private final UndoStack undo;
    private final Path dir;
    private final List<Preset> builtins;
    private final List<Preset> custom = new ArrayList<>();
    private String activeName = "";
    private boolean activeBuiltin;

    // laufende Überblendung
    private Map<String, Object> from;
    private Map<String, Object> to;
    private long start;

    public PresetManager(OptionRegistry reg, ConfigManager cfg, UndoStack undo, Path dir) {
        this.reg = reg;
        this.cfg = cfg;
        this.undo = undo;
        this.dir = dir;
        this.builtins = BuiltinPresets.all(reg);
        reload();
    }

    public List<Preset> all() {
        List<Preset> list = new ArrayList<>(builtins);
        list.addAll(custom);
        return list;
    }

    public String activeName() {
        return activeName;
    }

    public boolean activeIsBuiltin() {
        return activeBuiltin;
    }

    public Path presetDir() {
        return dir;
    }

    public void reload() {
        custom.clear();
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().forEach(p -> {
                try {
                    PresetIO.Parsed parsed = PresetIO.fromJson(
                            PresetIO.parseText(Files.readString(p, StandardCharsets.UTF_8)), reg);
                    String name = parsed.name().isBlank() ? p.getFileName().toString().replace(".json", "") : parsed.name();
                    custom.add(new Preset(name, false, parsed.values()));
                } catch (IOException | RuntimeException e) {
                    Log.warn("Preset {} konnte nicht gelesen werden: {}", p.getFileName(), e.getMessage());
                }
            });
        } catch (IOException e) {
            Log.warn("Preset-Ordner konnte nicht gelesen werden: {}", e.getMessage());
        }
    }

    /** Speichert den aktuellen Stand (nur Abweichungen vom Standard) unter einem Namen. */
    public boolean saveCurrent(String name) {
        String clean = name.trim();
        if (clean.isEmpty()) {
            return false;
        }
        Map<String, Object> values = new LinkedHashMap<>();
        for (OptionDef<?> def : reg.options()) {
            if (EXCLUDED.contains(def.id()) || !cfg.isModified(def.id())) {
                continue;
            }
            values.put(def.id(), cfg.getRaw(def.id()));
        }
        try {
            Files.createDirectories(dir);
            String file = clean.replaceAll("[^A-Za-z0-9 _.-]", "_") + ".json";
            Files.writeString(dir.resolve(file),
                    PresetIO.toPrettyJson(PresetIO.toJson(clean, values, reg)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Log.error("Preset konnte nicht gespeichert werden", e);
            return false;
        }
        reload();
        activeName = clean;
        activeBuiltin = false;
        return true;
    }

    // ------------------------------------------------------------------ Anwenden

    public void apply(Preset preset) {
        finishNow();
        applyValues(preset.values());
        activeName = preset.name();
        activeBuiltin = preset.builtin();
    }

    /** Wendet Werte an (Preset oder Import): sanft überblendet, als ein Schritt rückgängig machbar. */
    public void applyValues(Map<String, Object> values) {
        finishNow();
        Map<String, Object> f = new LinkedHashMap<>();
        Map<String, Object> t = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : values.entrySet()) {
            OptionDef<?> def = reg.get(e.getKey());
            if (def == null || EXCLUDED.contains(e.getKey())) {
                continue;
            }
            Object target = clean(def, e.getValue());
            Object now = cfg.getRaw(e.getKey());
            if (target != null && !target.equals(now)) {
                f.put(e.getKey(), now);
                t.put(e.getKey(), target);
            }
        }
        if (t.isEmpty()) {
            return;
        }
        if (!cfg.get(GeneralOptions.GUI_ANIMATIONS)) {
            from = f;
            to = t;
            finishNow();
            return;
        }
        from = f;
        to = t;
        start = System.currentTimeMillis();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object clean(OptionDef def, Object value) {
        return def.sanitize(def.cast(value));
    }

    public boolean transitioning() {
        return to != null;
    }

    /** Pro Frame aufrufen, solange das GUI offen ist. */
    public void tick() {
        if (to == null) {
            return;
        }
        double t = (System.currentTimeMillis() - start) / (double) FADE_MS;
        if (t >= 1.0) {
            finishNow();
            return;
        }
        double e = t * t * (3 - 2 * t);
        undo.setSuppressed(true);
        try {
            for (Map.Entry<String, Object> en : to.entrySet()) {
                OptionDef<?> def = reg.get(en.getKey());
                Object a = from.get(en.getKey());
                Object b = en.getValue();
                if (def == null) {
                    continue;
                }
                switch (def.type()) {
                    case SLIDER -> cfg.set(def.id(), lerp(((Number) a).doubleValue(), ((Number) b).doubleValue(), e));
                    case INT_SLIDER -> cfg.set(def.id(),
                            (int) Math.round(lerp(((Number) a).doubleValue(), ((Number) b).doubleValue(), e)));
                    case COLOR -> cfg.set(def.id(), lerpColor((Integer) a, (Integer) b, e));
                    default -> {
                        if (t >= 0.5) {
                            cfg.set(def.id(), b);
                        }
                    }
                }
            }
        } finally {
            undo.setSuppressed(false);
        }
    }

    /** Beendet eine laufende Überblendung sofort mit den Zielwerten. */
    public void finishNow() {
        if (to == null) {
            return;
        }
        Map<String, Object> f = from;
        Map<String, Object> t = to;
        from = null;
        to = null;
        List<UndoStack.Change> changes = new ArrayList<>();
        undo.setSuppressed(true);
        try {
            for (Map.Entry<String, Object> en : t.entrySet()) {
                cfg.set(en.getKey(), en.getValue());
                changes.add(new UndoStack.Change(en.getKey(), f.get(en.getKey()), en.getValue()));
            }
        } finally {
            undo.setSuppressed(false);
        }
        undo.pushGroup(changes);
    }

    /** Welche Werte würden sich ändern? Für die Vorschau vor dem Import. */
    public List<UndoStack.Change> diff(Map<String, Object> values) {
        List<UndoStack.Change> list = new ArrayList<>();
        for (Map.Entry<String, Object> e : values.entrySet()) {
            OptionDef<?> def = reg.get(e.getKey());
            if (def == null || EXCLUDED.contains(e.getKey())) {
                continue;
            }
            Object target = clean(def, e.getValue());
            Object now = cfg.getRaw(e.getKey());
            if (target != null && !target.equals(now)) {
                list.add(new UndoStack.Change(e.getKey(), now, target));
            }
        }
        return list;
    }

    /** Alle abweichenden Werte des aktuellen Stands, für den Export. */
    public Map<String, Object> currentModified() {
        Map<String, Object> values = new LinkedHashMap<>();
        for (OptionDef<?> def : reg.options()) {
            if (!EXCLUDED.contains(def.id()) && cfg.isModified(def.id())) {
                values.put(def.id(), cfg.getRaw(def.id()));
            }
        }
        return values;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static int lerpColor(int a, int b, double t) {
        int out = 0;
        for (int shift = 24; shift >= 0; shift -= 8) {
            int ca = (a >>> shift) & 255;
            int cb = (b >>> shift) & 255;
            out |= ((int) Math.round(lerp(ca, cb, t)) & 255) << shift;
        }
        return out;
    }
}
