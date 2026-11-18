package dev.visuals.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/** Die eingebauten Codecs. Alle sind tolerant: Unpassendes JSON ergibt {@code null}, nie eine Exception. */
public final class Codecs {
    private Codecs() {
    }

    public static final ValueCodec<Boolean> BOOL = new ValueCodec<>() {
        @Override
        public JsonElement encode(Boolean value) {
            return new JsonPrimitive(value);
        }

        @Override
        public Boolean decode(JsonElement json) {
            if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isBoolean()) {
                return json.getAsBoolean();
            }
            return null;
        }
    };

    public static final ValueCodec<Double> DOUBLE = new ValueCodec<>() {
        @Override
        public JsonElement encode(Double value) {
            return new JsonPrimitive(value);
        }

        @Override
        public Double decode(JsonElement json) {
            if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
                double d = json.getAsDouble();
                if (!Double.isNaN(d) && !Double.isInfinite(d)) {
                    return d;
                }
            }
            return null;
        }
    };

    public static final ValueCodec<Integer> INT = new ValueCodec<>() {
        @Override
        public JsonElement encode(Integer value) {
            return new JsonPrimitive(value);
        }

        @Override
        public Integer decode(JsonElement json) {
            if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
                BigDecimal rounded = json.getAsBigDecimal().setScale(0, RoundingMode.HALF_UP);
                if (rounded.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) {
                    return Integer.MAX_VALUE;
                }
                if (rounded.compareTo(BigDecimal.valueOf(Integer.MIN_VALUE)) < 0) {
                    return Integer.MIN_VALUE;
                }
                return rounded.intValue();
            }
            return null;
        }
    };

    public static final ValueCodec<String> STRING = new ValueCodec<>() {
        @Override
        public JsonElement encode(String value) {
            return new JsonPrimitive(value);
        }

        @Override
        public String decode(JsonElement json) {
            if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
                return json.getAsString();
            }
            return null;
        }
    };

    /**
     * Farbe als ARGB-Integer im Speicher, in der Datei als lesbarer Hex-String
     * ("#RRGGBB" bei voller Deckkraft, sonst "#AARRGGBB"). Beim Lesen sind auch "RRGGBB" ohne "#" erlaubt.
     */
    public static final ValueCodec<Integer> COLOR = new ValueCodec<>() {
        @Override
        public JsonElement encode(Integer value) {
            int argb = value;
            if ((argb >>> 24) == 0xFF) {
                return new JsonPrimitive(String.format(Locale.ROOT, "#%06X", argb & 0xFFFFFF));
            }
            return new JsonPrimitive(String.format(Locale.ROOT, "#%08X", argb));
        }

        @Override
        public Integer decode(JsonElement json) {
            if (json == null || !json.isJsonPrimitive() || !json.getAsJsonPrimitive().isString()) {
                return null;
            }
            String text = json.getAsString().trim();
            if (text.startsWith("#")) {
                text = text.substring(1);
            }
            if (text.length() != 6 && text.length() != 8) {
                return null;
            }
            try {
                long parsed = Long.parseLong(text, 16);
                if (text.length() == 6) {
                    parsed |= 0xFF000000L;
                }
                return (int) parsed;
            } catch (NumberFormatException e) {
                return null;
            }
        }
    };
}
