package dev.visuals.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.visuals.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Hält die Werte aller Optionen, liest und schreibt die JSON-Datei.
 *
 * <p><b>Zwei Stände:</b> {@code live} sind die Werte, die gerade wirken (auch ungespeicherte GUI-Änderungen),
 * {@code saved} ist der Stand der letzten {@link #commit()}. {@link #isDirty()} vergleicht beide,
 * {@link #revert()} geht zurück auf {@code saved}.
 *
 * <p><b>Speichern:</b> {@code commit()} nimmt den Stand sofort als JSON-Schnappschuss auf und schreibt ihn
 * verzögert (Entprellung, {@value #SAVE_DELAY_MS} ms) auf einem eigenen Thread. Geschrieben wird atomar
 * über eine temporäre Datei. {@link #flush()} schreibt sofort und gehört ins Beenden des Clients.
 *
 * <p><b>Threads:</b> Alle Methoden außer dem Schreiben selbst sind für den Client-Thread gedacht.
 */
public final class ConfigManager {
    public static final int CURRENT_VERSION = 1;
    /** ID des globalen Notausschalters (allgemein). Aus: alle Effekte sind sofort abgeschaltet. */
    public static final String MOD_ENABLED_ID = "general.modEnabled";
    private static final long SAVE_DELAY_MS = 400;

    /** Wird nach jeder Änderung eines Werts aufgerufen (Eingabe, Zurücksetzen, Verwerfen). */
    @FunctionalInterface
    public interface ChangeListener {
        void onChanged(OptionDef<?> def, Object oldValue, Object newValue);
    }

    private final Path file;
    private final OptionRegistry registry;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Map<String, Object> live = new LinkedHashMap<>();
    private Map<String, Object> saved = new HashMap<>();
    /** Werte aus der Datei, die diese Version nicht kennt. Bleiben erhalten, damit ein Downgrade nichts löscht. */
    private JsonObject unknownValues = new JsonObject();
    private final List<ChangeListener> listeners = new CopyOnWriteArrayList<>();

    private final ScheduledExecutorService io = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "visuals-config-io");
        t.setDaemon(true);
        return t;
    });
    private final AtomicReference<String> pendingJson = new AtomicReference<>();
    private volatile ScheduledFuture<?> pendingTask;

    public ConfigManager(Path file, OptionRegistry registry) {
        this.file = file.toAbsolutePath();
        this.registry = registry;
        fillDefaults();
        saved = new HashMap<>(live);
    }

    // ---------------------------------------------------------------- Laden

    /** Liest die Datei. Fehlt sie, wird sie mit den Standardwerten angelegt. Eine kaputte Datei wird beiseitegelegt. */
    public void load() {
        fillDefaults();
        unknownValues = new JsonObject();

        if (!Files.isRegularFile(file)) {
            saved = new HashMap<>(live);
            scheduleWrite(toJson(live));
            return;
        }
        try {
            readFile();
        } catch (IOException | RuntimeException e) {
            Log.error("Config-Datei ist beschädigt und wird durch die Standardwerte ersetzt: {}", file, e);
            quarantine();
            fillDefaults();
            unknownValues = new JsonObject();
            saved = new HashMap<>(live);
            scheduleWrite(toJson(live));
            return;
        }
        saved = new HashMap<>(live);
    }

    private void readFile() throws IOException {
        JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
        if (!parsed.isJsonObject()) {
            throw new JsonParseException("Wurzel ist kein JSON-Objekt");
        }
        JsonObject root = parsed.getAsJsonObject();
        int version = readVersion(root);

        if (version > CURRENT_VERSION) {
            Log.warn("Config stammt aus einer neueren Version ({}), lade tolerant und lege eine Sicherung an", version);
            copySibling(".v" + version + ".bak");
        } else {
            root = ConfigMigrations.migrate(root, version, CURRENT_VERSION);
        }

        JsonElement valuesElement = root.get("values");
        if (valuesElement == null || !valuesElement.isJsonObject()) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : valuesElement.getAsJsonObject().entrySet()) {
            OptionDef<?> def = registry.get(entry.getKey());
            if (def == null) {
                unknownValues.add(entry.getKey(), entry.getValue());
            } else {
                loadValue(def, entry.getValue());
            }
        }
    }

    private static int readVersion(JsonObject root) {
        JsonElement element = root.get("configVersion");
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return Math.max(1, element.getAsInt());
        }
        return 1;
    }

    private <T> void loadValue(OptionDef<T> def, JsonElement json) {
        T value = def.codec().decode(json);
        if (value == null) {
            Log.warn("Ungültiger Wert für {}, der Standardwert wird benutzt", def.id());
            return;
        }
        live.put(def.id(), def.sanitize(value));
    }

    private void fillDefaults() {
        live.clear();
        for (OptionDef<?> def : registry.options()) {
            live.put(def.id(), def.defaultValue());
        }
    }

    // ---------------------------------------------------------------- Lesen und Schreiben von Werten

    public <T> T get(OptionDef<T> def) {
        T value = def.cast(live.get(def.id()));
        return value != null ? value : def.defaultValue();
    }

    /** Aktueller Wert einer Option nach ID, oder {@code null}, wenn es sie nicht gibt. */
    public Object getRaw(String id) {
        return live.get(id);
    }

    /**
     * Setzt einen Wert. Ungültiges wird korrigiert (Bereich, Auswahl, Typ).
     *
     * @return {@code true}, wenn der Wert unverändert übernommen wurde; {@code false}, wenn er korrigiert werden
     * musste oder die Option unbekannt ist. Das GUI markiert den Fall {@code false}.
     */
    public boolean set(String id, Object value) {
        OptionDef<?> def = registry.get(id);
        if (def == null) {
            Log.warn("Unbekannte Option '{}' wird ignoriert", id);
            return false;
        }
        return setInternal(def, value);
    }

    public <T> boolean set(OptionDef<T> def, T value) {
        return setInternal(def, value);
    }

    private <T> boolean setInternal(OptionDef<T> def, Object raw) {
        T requested = def.cast(raw);
        T value = def.sanitize(requested);
        boolean unchanged = requested != null && requested.equals(value);
        T old = def.cast(live.get(def.id()));
        if (!Objects.equals(old, value)) {
            live.put(def.id(), value);
            fire(def, old, value);
        }
        return unchanged;
    }

    private void fire(OptionDef<?> def, Object oldValue, Object newValue) {
        for (ChangeListener listener : listeners) {
            try {
                listener.onChanged(def, oldValue, newValue);
            } catch (RuntimeException e) {
                Log.error("Fehler in einem Config-Listener bei {}", def.id(), e);
            }
        }
    }

    public void addListener(ChangeListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ChangeListener listener) {
        listeners.remove(listener);
    }

    // ---------------------------------------------------------------- Zurücksetzen

    public void reset(String id) {
        OptionDef<?> def = registry.get(id);
        if (def != null) {
            setInternal(def, def.defaultValue());
        }
    }

    /** Setzt alle Optionen eines Reiters inklusive Master-Schalter zurück. */
    public void resetTab(String tabId) {
        for (OptionDef<?> def : registry.options()) {
            if (def.tabId().equals(tabId)) {
                setInternal(def, def.defaultValue());
            }
        }
    }

    public void resetAll() {
        for (OptionDef<?> def : registry.options()) {
            setInternal(def, def.defaultValue());
        }
    }

    /** Weicht der aktuelle Wert vom Standard ab? (für die dezente Markierung im GUI) */
    public boolean isModified(String id) {
        OptionDef<?> def = registry.get(id);
        return def != null && !Objects.equals(live.get(id), def.defaultValue());
    }

    // ---------------------------------------------------------------- Aktiv-Status

    /**
     * Ist die Option bedienbar? Aus, wenn der Master-Schalter des Reiters aus ist oder ihre Abhängigkeit nicht
     * erfüllt ist. Das GUI graut sie dann aus. Der globale Notausschalter graut nichts aus.
     */
    public boolean isOptionEnabled(String id) {
        OptionDef<?> def = registry.get(id);
        if (def == null) {
            return false;
        }
        if (!def.isMaster()) {
            TabDef tab = registry.tab(def.tabId());
            if (tab != null && tab.hasMaster() && !Boolean.TRUE.equals(live.get(tab.masterId()))) {
                return false;
            }
        }
        return def.enabledWhen() == null || Boolean.TRUE.equals(live.get(def.enabledWhen()));
    }

    /** Sollen die Effekte dieses Reiters gerade wirken? Berücksichtigt Notausschalter und Master-Schalter. */
    public boolean isEffectActive(String tabId) {
        if (!Boolean.TRUE.equals(live.get(MOD_ENABLED_ID))) {
            return false;
        }
        TabDef tab = registry.tab(tabId);
        return tab != null && (!tab.hasMaster() || Boolean.TRUE.equals(live.get(tab.masterId())));
    }

    // ---------------------------------------------------------------- Speichern

    public boolean isDirty() {
        return !live.equals(saved);
    }

    /** Übernimmt den aktuellen Stand als gespeichert und schreibt ihn verzögert in die Datei. */
    public void commit() {
        saved = new HashMap<>(live);
        scheduleWrite(toJson(live));
    }

    /** Verwirft ungespeicherte Änderungen. */
    public void revert() {
        for (OptionDef<?> def : registry.options()) {
            setInternal(def, saved.get(def.id()));
        }
    }

    /** Schreibt eine ausstehende Änderung sofort. Beim Beenden des Clients aufrufen. */
    public void flush() {
        ScheduledFuture<?> task = pendingTask;
        if (task != null) {
            task.cancel(false);
        }
        writePending();
    }

    private String toJson(Map<String, Object> values) {
        JsonObject root = new JsonObject();
        root.addProperty("configVersion", CURRENT_VERSION);
        JsonObject out = new JsonObject();
        for (OptionDef<?> def : registry.options()) {
            out.add(def.id(), encodeValue(def, values.get(def.id())));
        }
        for (Map.Entry<String, JsonElement> entry : unknownValues.entrySet()) {
            if (!out.has(entry.getKey())) {
                out.add(entry.getKey(), entry.getValue().deepCopy());
            }
        }
        root.add("values", out);
        return gson.toJson(root);
    }

    private <T> JsonElement encodeValue(OptionDef<T> def, Object raw) {
        T value = def.cast(raw);
        return def.codec().encode(value != null ? value : def.defaultValue());
    }

    private void scheduleWrite(String json) {
        pendingJson.set(json);
        ScheduledFuture<?> previous = pendingTask;
        if (previous != null) {
            previous.cancel(false);
        }
        pendingTask = io.schedule(this::writePending, SAVE_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private synchronized void writePending() {
        String json = pendingJson.getAndSet(null);
        if (json == null) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            Log.error("Config konnte nicht gespeichert werden: {}", file, e);
        }
    }

    /** Beendet den Schreib-Thread. Nach {@link #flush()} aufrufen. */
    public void close() {
        flush();
        io.shutdown();
    }

    // ---------------------------------------------------------------- Dateihilfen

    private void quarantine() {
        try {
            Path target = file.resolveSibling(file.getFileName() + ".broken-" + System.currentTimeMillis());
            Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
            Log.warn("Die beschädigte Datei wurde gesichert als {}", target);
        } catch (IOException e) {
            Log.error("Die beschädigte Config konnte nicht beiseitegelegt werden", e);
        }
    }

    private void copySibling(String suffix) {
        try {
            Files.copy(file, file.resolveSibling(file.getFileName() + suffix), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Log.warn("Sicherung {} konnte nicht angelegt werden", suffix, e);
        }
    }
}
