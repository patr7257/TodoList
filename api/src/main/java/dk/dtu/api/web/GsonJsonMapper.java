package dk.dtu.api.web;

import java.lang.reflect.Type;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.javalin.json.JsonMapper;
import org.jetbrains.annotations.NotNull;

/**
 * Javalin JSON mapper backed by Gson with serializeNulls enabled, so response
 * objects that contain null fields (for example {@code dueAt: null}) are
 * rendered exactly like the website's NextResponse.json output, where null
 * fields are present rather than dropped. Timestamps are pre-formatted to ISO
 * strings by the view layer, so no custom adapters are needed here.
 */
public final class GsonJsonMapper implements JsonMapper {

    private final Gson gson = new GsonBuilder().serializeNulls().create();

    @NotNull
    @Override
    public String toJsonString(@NotNull Object obj, @NotNull Type type) {
        return gson.toJson(obj, type);
    }

    @NotNull
    @Override
    public <T> T fromJsonString(@NotNull String json, @NotNull Type targetType) {
        return gson.fromJson(json, targetType);
    }
}
