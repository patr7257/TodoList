package dk.dtu.api.web;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;

/**
 * Thin wrapper over a parsed JSON request body that reproduces the website's
 * TypeScript type checks precisely: the difference between a key being absent
 * ({@code undefined}), explicitly null, or a value of a given type.
 *
 * <p>Parsing an unparseable body raises {@link HttpError#badJson()} (matching
 * {@code await req.json()} throwing). A body that is valid JSON but not an
 * object is treated as an empty object, so absent-key checks fire the same way
 * the website's {@code (body as Record | null) ?? {}} does.
 */
public final class Body {

    private final JsonObject obj;

    private Body(JsonObject obj) {
        this.obj = obj;
    }

    public static Body parse(String raw) {
        if (raw == null || raw.isBlank()) {
            // Matches await req.json() throwing on an empty/absent body.
            throw HttpError.badJson();
        }
        JsonElement el;
        try {
            el = com.google.gson.JsonParser.parseString(raw);
        } catch (JsonParseException e) {
            throw HttpError.badJson();
        }
        return new Body(el != null && el.isJsonObject() ? el.getAsJsonObject() : new JsonObject());
    }

    /** True when the key is present (even if its value is null). */
    public boolean has(String key) {
        return obj.has(key);
    }

    /** True when the key is present and its value is JSON null. */
    public boolean isNull(String key) {
        return obj.has(key) && obj.get(key).isJsonNull();
    }

    public boolean isString(String key) {
        JsonElement e = obj.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString();
    }

    public String asString(String key) {
        return obj.get(key).getAsString();
    }

    public boolean isBoolean(String key) {
        JsonElement e = obj.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean();
    }

    public boolean asBoolean(String key) {
        return obj.get(key).getAsBoolean();
    }

    /** True when present and a JSON number (regardless of integrality). */
    public boolean isNumber(String key) {
        JsonElement e = obj.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber();
    }

    /** True when present and an integer-valued JSON number (Number.isInteger). */
    public boolean isInteger(String key) {
        if (!isNumber(key)) {
            return false;
        }
        double d = obj.get(key).getAsDouble();
        return !Double.isNaN(d) && !Double.isInfinite(d) && d == Math.rint(d)
                && d >= Long.MIN_VALUE && d <= Long.MAX_VALUE;
    }

    public int asInt(String key) {
        return obj.get(key).getAsInt();
    }

    public String asStringRaw(String key) {
        JsonPrimitive p = obj.getAsJsonPrimitive(key);
        return p.getAsString();
    }
}
