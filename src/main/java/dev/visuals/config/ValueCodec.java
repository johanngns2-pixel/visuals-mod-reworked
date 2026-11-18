package dev.visuals.config;

import com.google.gson.JsonElement;

/** Wandelt einen Optionswert von und nach JSON. */
public interface ValueCodec<T> {

    JsonElement encode(T value);

    /** Liefert {@code null}, wenn das JSON nicht zum Typ passt (der Aufrufer nimmt dann den Standardwert). */
    T decode(JsonElement json);
}
