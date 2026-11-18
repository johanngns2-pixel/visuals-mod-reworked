package dev.visuals.config.def;

import dev.visuals.config.OptionRegistry;
import dev.visuals.config.Preset;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Die mitgelieferten Presets. Einträge für Optionen, die es (noch) nicht gibt, werden ausgelassen. */
public final class BuiltinPresets {
    private BuiltinPresets() {
    }

    public static List<Preset> all(OptionRegistry reg) {
        List<Preset> list = new ArrayList<>();
        list.add(make(reg, "autumn",
                "sky.skyCustom", true, "sky.skyDawn", 0xFFD9805A, "sky.skyDay", 0xFFA9B8C8, "sky.skyDusk", 0xFFB5552F,
                "sky.skyNight", 0xFF14102A, "sky.starAmount", 0.8, "sky.sunColor", 0xFFFFD9A0, "sky.sunShape", "round",
                "sky.moonColor", 0xFFFFE9C4, "sky.shootingFrequency", 0.5));
        list.add(make(reg, "winternight",
                "sky.skyCustom", true, "sky.skyNight", 0xFF050B22, "sky.skyDay", 0xFF9DB7D6, "sky.starAmount", 2.2,
                "sky.starTwinkle", 0.7, "sky.starColor", 0xFFD8ECFF, "sky.moonSize", 1.5, "sky.moonColor", 0xFFDDEBFF,
                "sky.shootingFrequency", 2.0));
        list.add(make(reg, "summerevening",
                "sky.skyCustom", true, "sky.skyDusk", 0xFFFF7A4A, "sky.skyDawn", 0xFFFFA070, "sky.sunColor", 0xFFFFC060,
                "sky.sunSize", 1.4, "sky.sunShape", "round", "sky.starAmount", 1.2));
        list.add(make(reg, "minimal",
                "sky.starAmount", 0.3, "sky.starTwinkle", 0.0, "sky.shootingFrequency", 0.0, "sky.moonPhases", false,
                "sky.sunShape", "square"));
        list.add(make(reg, "performance",
                "sky.starAmount", 0.3, "sky.starTwinkle", 0.0, "sky.shootingFrequency", 0.0,
                "general.performanceMode", true, "general.performanceTargetFps", 60));
        return list;
    }

    private static Preset make(OptionRegistry reg, String id, Object... kv) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            String key = (String) kv[i];
            if (reg.get(key) != null) {
                values.put(key, kv[i + 1]);
            }
        }
        return new Preset(id, true, values);
    }
}
