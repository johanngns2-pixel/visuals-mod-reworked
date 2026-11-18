package dev.visuals.config;

import java.util.List;
import java.util.regex.Pattern;
import java.util.function.UnaryOperator;

/**
 * Die einzige Stelle, an der eine Option definiert wird: ID, Typ, Standardwert, Grenzen, Gruppe, Abhängigkeit.
 * Das GUI und die Config bauen sich vollständig daraus auf.
 *
 * <p>Die ID hat die Form {@code <reiter>.<name>}, zum Beispiel {@code sky.starAmount}. Der Reiter wird aus der
 * ID abgeleitet, damit er nicht doppelt gepflegt werden muss.
 *
 * <p>Sprachschlüssel (werden aus der ID abgeleitet):
 * <ul>
 *   <li>Name: {@code visuals.option.<id>.name}</li>
 *   <li>Tooltip: {@code visuals.option.<id>.tooltip}</li>
 *   <li>Gruppe: {@code visuals.group.<reiter>.<gruppe>}</li>
 * </ul>
 */
public final class OptionDef<T> {
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z][a-z0-9]*\\.[a-zA-Z][a-zA-Z0-9]*");

    private final String id;
    private final String tabId;
    private final String groupId;
    private final String unit;
    private final String enabledWhen;
    private final OptionType type;
    private final Class<T> valueClass;
    private final ValueCodec<T> codec;
    private final T defaultValue;
    private final UnaryOperator<T> sanitizer;
    private final double min;
    private final double max;
    private final double step;
    private final List<String> choices;
    private final boolean master;

    private OptionDef(Builder<T> b) {
        if (!ID_PATTERN.matcher(b.id).matches()) {
            throw new IllegalArgumentException("Ungültige Options-ID: '" + b.id
                    + "' (erwartet: <reiter>.<name>, Reiter nur a-z und 0-9)");
        }
        this.id = b.id;
        this.tabId = b.id.substring(0, b.id.indexOf('.'));
        this.groupId = b.group;
        this.unit = b.unit;
        this.enabledWhen = b.enabledWhen;
        this.type = b.type;
        this.valueClass = b.valueClass;
        this.codec = b.codec;
        this.defaultValue = b.defaultValue;
        this.sanitizer = b.sanitizer;
        this.min = b.min;
        this.max = b.max;
        this.step = b.step;
        this.choices = b.choices;
        this.master = b.master;
    }

    // ---------------------------------------------------------------- Fabriken

    public static Builder<Boolean> toggle(String id, boolean defaultValue) {
        return new Builder<>(id, OptionType.TOGGLE, Boolean.class, Codecs.BOOL, defaultValue, v -> v);
    }

    public static Builder<Double> slider(String id, double defaultValue, double min, double max, double step) {
        if (!(min < max) || step <= 0 || defaultValue < min || defaultValue > max) {
            throw new IllegalArgumentException("Ungültige Regler-Grenzen für " + id);
        }
        Builder<Double> b = new Builder<>(id, OptionType.SLIDER, Double.class, Codecs.DOUBLE, defaultValue,
                v -> Double.isNaN(v) || Double.isInfinite(v) ? defaultValue : Math.max(min, Math.min(max, v)));
        b.min = min;
        b.max = max;
        b.step = step;
        return b;
    }

    public static Builder<Integer> intSlider(String id, int defaultValue, int min, int max) {
        if (min >= max || defaultValue < min || defaultValue > max) {
            throw new IllegalArgumentException("Ungültige Regler-Grenzen für " + id);
        }
        Builder<Integer> b = new Builder<>(id, OptionType.INT_SLIDER, Integer.class, Codecs.INT, defaultValue,
                v -> Math.max(min, Math.min(max, v)));
        b.min = min;
        b.max = max;
        b.step = 1;
        return b;
    }

    public static Builder<String> dropdown(String id, String defaultValue, String... choices) {
        List<String> list = List.of(choices);
        if (!list.contains(defaultValue)) {
            throw new IllegalArgumentException("Standardwert von " + id + " steht nicht in der Auswahl");
        }
        Builder<String> b = new Builder<>(id, OptionType.DROPDOWN, String.class, Codecs.STRING, defaultValue,
                v -> list.contains(v) ? v : defaultValue);
        b.choices = list;
        return b;
    }

    /** Farbe als ARGB-Integer, zum Beispiel {@code 0xFFE8892B}. */
    public static Builder<Integer> color(String id, int defaultArgb) {
        return new Builder<>(id, OptionType.COLOR, Integer.class, Codecs.COLOR, defaultArgb, v -> v);
    }

    public static Builder<String> text(String id, String defaultValue, int maxLength) {
        Builder<String> b = new Builder<>(id, OptionType.TEXT, String.class, Codecs.STRING, defaultValue,
                v -> v.length() > maxLength ? v.substring(0, maxLength) : v);
        b.max = maxLength;
        return b;
    }

    // ---------------------------------------------------------------- Werte

    /** Bringt einen Wert in den gültigen Bereich. {@code null} ergibt den Standardwert. */
    public T sanitize(T value) {
        if (value == null) {
            return defaultValue;
        }
        T result = sanitizer.apply(value);
        return result == null ? defaultValue : result;
    }

    /** Wandelt einen unbekannten Wert in den Typ dieser Option um, oder liefert {@code null}, wenn das nicht geht. */
    public T cast(Object raw) {
        if (raw == null) {
            return null;
        }
        if (valueClass.isInstance(raw)) {
            return valueClass.cast(raw);
        }
        if (raw instanceof Number n) {
            if (valueClass == Double.class) {
                return valueClass.cast(n.doubleValue());
            }
            if (valueClass == Integer.class) {
                long rounded = Math.round(n.doubleValue());
                return valueClass.cast((int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, rounded)));
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- Zugriff

    public String id() {
        return id;
    }

    public String tabId() {
        return tabId;
    }

    public String groupId() {
        return groupId;
    }

    public OptionType type() {
        return type;
    }

    public ValueCodec<T> codec() {
        return codec;
    }

    public T defaultValue() {
        return defaultValue;
    }

    public double min() {
        return min;
    }

    public double max() {
        return max;
    }

    public double step() {
        return step;
    }

    public String unit() {
        return unit;
    }

    public List<String> choices() {
        return choices;
    }

    /** ID einer Schalter-Option, die an sein muss, damit diese Option bedienbar ist (oder {@code null}). */
    public String enabledWhen() {
        return enabledWhen;
    }

    /** Master-Schalter eines Reiters (wird im Kopf des Reiters statt in einer Gruppe angezeigt). */
    public boolean isMaster() {
        return master;
    }

    public String nameKey() {
        return master ? "visuals.master.name" : "visuals.option." + id + ".name";
    }

    public String tooltipKey() {
        return master ? "visuals.master.tooltip" : "visuals.option." + id + ".tooltip";
    }

    /** Sprachschlüssel für einen Eintrag eines Dropdowns: {@code visuals.option.<id>.choice.<wert>}. */
    public String choiceKey(String choice) {
        return "visuals.option." + id + ".choice." + choice;
    }

    public String groupKey() {
        return "visuals.group." + tabId + "." + groupId;
    }

    @Override
    public String toString() {
        return "OptionDef[" + id + "]";
    }

    // ---------------------------------------------------------------- Builder

    public static final class Builder<T> {
        private final String id;
        private final OptionType type;
        private final Class<T> valueClass;
        private final ValueCodec<T> codec;
        private final T defaultValue;
        private final UnaryOperator<T> sanitizer;
        private double min;
        private double max;
        private double step;
        private List<String> choices = List.of();
        private String group = "main";
        private String unit = "";
        private String enabledWhen;
        private boolean master;

        private Builder(String id, OptionType type, Class<T> valueClass, ValueCodec<T> codec, T defaultValue,
                        UnaryOperator<T> sanitizer) {
            this.id = id;
            this.type = type;
            this.valueClass = valueClass;
            this.codec = codec;
            this.defaultValue = defaultValue;
            this.sanitizer = sanitizer;
        }

        /** Gruppe (Zwischenüberschrift) innerhalb des Reiters. Standard: "main". */
        public Builder<T> group(String group) {
            this.group = group;
            return this;
        }

        /** Einheit für die Anzeige, zum Beispiel "%" oder "s". */
        public Builder<T> unit(String unit) {
            this.unit = unit;
            return this;
        }

        /** Option ist nur bedienbar, wenn die angegebene Schalter-Option an ist. */
        public Builder<T> enabledWhen(String toggleOptionId) {
            this.enabledWhen = toggleOptionId;
            return this;
        }

        Builder<T> master() {
            this.master = true;
            return this;
        }

        public OptionDef<T> build() {
            return new OptionDef<>(this);
        }
    }
}
