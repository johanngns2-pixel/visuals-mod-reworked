package dev.visuals.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Zentrale Registrierung aller Reiter und Optionen, in der Reihenfolge, in der sie angezeigt werden.
 *
 * <p>Ablauf: Reiter und Optionen registrieren, dann {@link #freeze()} aufrufen. Danach ist die Registry
 * unveränderlich und damit ohne Sperren aus beliebigen Threads lesbar.
 */
public final class OptionRegistry {
    private final Map<String, TabDef> tabs = new LinkedHashMap<>();
    private final Map<String, OptionDef<?>> options = new LinkedHashMap<>();
    private volatile boolean frozen;

    /** Registriert einen Reiter. Hat er einen Master-Schalter, wird dieser automatisch mit angelegt. */
    public TabDef registerTab(TabDef tab) {
        checkOpen();
        if (tabs.putIfAbsent(tab.id(), tab) != null) {
            throw new IllegalStateException("Reiter doppelt registriert: " + tab.id());
        }
        if (tab.hasMaster()) {
            register(OptionDef.toggle(tab.masterId(), true).group("master").master().build());
        }
        return tab;
    }

    public <T> OptionDef<T> register(OptionDef<T> def) {
        checkOpen();
        if (!tabs.containsKey(def.tabId())) {
            throw new IllegalStateException("Unbekannter Reiter '" + def.tabId() + "' für Option " + def.id());
        }
        if (options.putIfAbsent(def.id(), def) != null) {
            throw new IllegalStateException("Option doppelt registriert: " + def.id());
        }
        return def;
    }

    /** Prüft die Abhängigkeiten und sperrt die Registry. */
    public void freeze() {
        for (OptionDef<?> def : options.values()) {
            String dep = def.enabledWhen();
            if (dep == null) {
                continue;
            }
            OptionDef<?> target = options.get(dep);
            if (target == null || target.type() != OptionType.TOGGLE) {
                throw new IllegalStateException("Option " + def.id() + " hängt von '" + dep
                        + "' ab, das ist aber keine registrierte Schalter-Option");
            }
        }
        frozen = true;
    }

    private void checkOpen() {
        if (frozen) {
            throw new IllegalStateException("Die Registry ist bereits gesperrt");
        }
    }

    public boolean isFrozen() {
        return frozen;
    }

    // ---------------------------------------------------------------- Abfragen

    public Collection<TabDef> tabs() {
        return Collections.unmodifiableCollection(tabs.values());
    }

    public TabDef tab(String id) {
        return tabs.get(id);
    }

    /** Alle Optionen, auch die Master-Schalter. */
    public Collection<OptionDef<?>> options() {
        return Collections.unmodifiableCollection(options.values());
    }

    /** Liefert {@code null}, wenn es die Option nicht gibt. */
    public OptionDef<?> get(String id) {
        return options.get(id);
    }

    /** Alle Optionen eines Reiters ohne den Master-Schalter, in Registrierungsreihenfolge. */
    public List<OptionDef<?>> optionsOf(String tabId) {
        List<OptionDef<?>> result = new ArrayList<>();
        for (OptionDef<?> def : options.values()) {
            if (def.tabId().equals(tabId) && !def.isMaster()) {
                result.add(def);
            }
        }
        return result;
    }

    /** Alle Gruppen eines Reiters in der Reihenfolge ihres ersten Auftretens. */
    public Set<String> groupsOf(String tabId) {
        Set<String> groups = new LinkedHashSet<>();
        for (OptionDef<?> def : optionsOf(tabId)) {
            groups.add(def.groupId());
        }
        return groups;
    }

    @SuppressWarnings("unchecked")
    public Optional<OptionDef<Boolean>> masterOf(String tabId) {
        TabDef tab = tabs.get(tabId);
        if (tab == null || !tab.hasMaster()) {
            return Optional.empty();
        }
        return Optional.of((OptionDef<Boolean>) options.get(tab.masterId()));
    }
}
