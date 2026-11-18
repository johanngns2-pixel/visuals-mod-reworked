package dev.visuals.config;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * Rückgängig und Wiederholen für Änderungen im GUI (mindestens die letzten 50 Schritte, hier 100).
 * Hört auf die {@link ConfigManager}-Änderungen. Aufeinanderfolgende Änderungen derselben Option innerhalb
 * kurzer Zeit (Regler ziehen) werden zu einem Schritt zusammengefasst.
 */
public final class UndoStack {
    public record Change(String id, Object oldValue, Object newValue) {
    }

    private static final int MAX = 100;
    private static final long MERGE_MS = 600;

    private final ConfigManager cfg;
    private final Deque<List<Change>> undo = new ArrayDeque<>();
    private final Deque<List<Change>> redo = new ArrayDeque<>();
    private boolean suppressed;
    private List<Change> group;
    private String lastId;
    private long lastTime;

    public UndoStack(ConfigManager cfg) {
        this.cfg = cfg;
        cfg.addListener(this::onChanged);
    }

    private void onChanged(OptionDef<?> def, Object oldValue, Object newValue) {
        if (suppressed || Objects.equals(oldValue, newValue)) {
            return;
        }
        if (group != null) {
            group.add(new Change(def.id(), oldValue, newValue));
            return;
        }
        long now = System.currentTimeMillis();
        List<Change> top = undo.peekFirst();
        if (top != null && top.size() == 1 && def.id().equals(lastId) && now - lastTime < MERGE_MS) {
            Change first = top.get(0);
            top.set(0, new Change(first.id(), first.oldValue(), newValue));
        } else {
            push(new ArrayList<>(List.of(new Change(def.id(), oldValue, newValue))));
        }
        lastId = def.id();
        lastTime = now;
        redo.clear();
    }

    /** Führt eine Aktion aus und fasst alle dabei entstehenden Änderungen zu einem Schritt zusammen. */
    public void group(Runnable action) {
        group = new ArrayList<>();
        try {
            action.run();
        } finally {
            List<Change> done = group;
            group = null;
            if (!done.isEmpty()) {
                push(done);
                redo.clear();
            }
            lastId = null;
        }
    }

    /** Für Abläufe, die ihre Änderungen selbst sammeln (Preset-Überblendung). */
    public void pushGroup(List<Change> changes) {
        if (!changes.isEmpty()) {
            push(new ArrayList<>(changes));
            redo.clear();
            lastId = null;
        }
    }

    public void setSuppressed(boolean suppressed) {
        this.suppressed = suppressed;
    }

    private void push(List<Change> changes) {
        undo.addFirst(changes);
        while (undo.size() > MAX) {
            undo.removeLast();
        }
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    public void undo() {
        List<Change> entry = undo.pollFirst();
        if (entry == null) {
            return;
        }
        apply(entry, true);
        redo.addFirst(entry);
        lastId = null;
    }

    public void redo() {
        List<Change> entry = redo.pollFirst();
        if (entry == null) {
            return;
        }
        apply(entry, false);
        undo.addFirst(entry);
        lastId = null;
    }

    private void apply(List<Change> entry, boolean backwards) {
        suppressed = true;
        try {
            if (backwards) {
                for (int i = entry.size() - 1; i >= 0; i--) {
                    cfg.set(entry.get(i).id(), entry.get(i).oldValue());
                }
            } else {
                for (Change c : entry) {
                    cfg.set(c.id(), c.newValue());
                }
            }
        } finally {
            suppressed = false;
        }
    }
}
