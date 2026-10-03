package dk.dtu.api.web;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dk.dtu.api.auth.AuthFilter;
import dk.dtu.api.domain.IntegrationService;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.javalin.http.Context;

/**
 * The integration API, authenticated by an integration key only (AuthFilter):
 *
 * <ul>
 *   <li>{@code PUT /api/todo/integrations/lists/{listName}/items} get-or-create
 *       the caller's list by exact name and upsert a batch by external id</li>
 *   <li>{@code GET /api/todo/integrations/lists/{listName}/items?source=} read
 *       back the caller's external items and their done state</li>
 * </ul>
 *
 * <p>Every check runs before the service is called, so a 400 writes nothing.
 * {@code listId} is the list's uuid as a string.
 */
public final class IntegrationsController {

    static final int MAX_ITEMS = 200;
    static final int MAX_SOURCE_LENGTH = 64;
    static final int MAX_EXTERNAL_ID_LENGTH = 200;
    static final int MAX_TEXT_LENGTH = 1000;
    static final int MAX_DESCRIPTION_LENGTH = 4000;
    static final int MAX_LIST_NAME_LENGTH = 200;

    private final Backend backend;

    public IntegrationsController(Backend backend) {
        this.backend = backend;
    }

    public void putItems(Context ctx) {
        IntegrationService integrations = requireBackend();
        String uid = requireUid(ctx);
        String listName = readListName(ctx);
        Body body = Body.parse(ctx.body());

        if (!body.isString("source")) {
            throw HttpError.badBody();
        }
        String source = readSource(body.asString("source"));
        if (!body.isArray("items")) {
            throw HttpError.badBody();
        }
        JsonArray raw = body.asArray("items");
        if (raw.size() > MAX_ITEMS) {
            throw HttpError.badBody();
        }
        List<IntegrationService.SyncItem> items = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonElement el : raw) {
            IntegrationService.SyncItem item = readItem(el);
            if (!seen.add(item.externalId())) {
                throw HttpError.badBody();
            }
            items.add(item);
        }

        IntegrationService.SyncResult r = integrations.sync(uid, listName, source, items);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("listId", r.listId());
        out.put("created", r.created());
        out.put("updated", r.updated());
        out.put("closed", r.closed());
        ctx.json(out);
    }

    public void getItems(Context ctx) {
        IntegrationService integrations = requireBackend();
        String uid = requireUid(ctx);
        String listName = readListName(ctx);
        String rawSource = ctx.queryParam("source");
        if (rawSource == null) {
            throw HttpError.badBody();
        }
        String source = readSource(rawSource);

        IntegrationService.ListItems found = integrations.read(uid, listName, source);
        List<Map<String, Object>> items = new ArrayList<>();
        for (IntegrationService.ExternalItem i : found.items()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("externalId", i.externalId());
            m.put("text", i.text());
            m.put("done", i.done());
            items.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("listId", found.listId());
        out.put("items", items);
        ctx.json(out);
    }

    private static IntegrationService.SyncItem readItem(JsonElement el) {
        if (el == null || !el.isJsonObject()) {
            throw HttpError.badBody();
        }
        JsonObject o = el.getAsJsonObject();
        String externalId = string(o, "externalId");
        if (externalId == null || externalId.trim().isEmpty() || externalId.length() > MAX_EXTERNAL_ID_LENGTH) {
            throw HttpError.badBody();
        }
        String text = string(o, "text");
        if (text == null || text.trim().isEmpty() || text.length() > MAX_TEXT_LENGTH) {
            throw HttpError.badBody();
        }
        String description = null;
        JsonElement d = o.get("description");
        if (d != null && !d.isJsonNull()) {
            if (!d.isJsonPrimitive() || !d.getAsJsonPrimitive().isString()) {
                throw HttpError.badBody();
            }
            String value = d.getAsString();
            if (value.length() > MAX_DESCRIPTION_LENGTH) {
                throw HttpError.badBody();
            }
            String trimmed = value.trim();
            description = trimmed.isEmpty() ? null : trimmed;
        }
        JsonElement done = o.get("done");
        if (done == null || !done.isJsonPrimitive() || !done.getAsJsonPrimitive().isBoolean()) {
            throw HttpError.badBody();
        }
        return new IntegrationService.SyncItem(externalId, text.trim(), description, done.getAsBoolean());
    }

    private static String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : null;
    }

    private static String readSource(String source) {
        if (source.trim().isEmpty() || source.length() > MAX_SOURCE_LENGTH) {
            throw HttpError.badBody();
        }
        return source;
    }

    /** Exact name: no trimming, so surrounding whitespace is refused rather than guessed at. */
    private static String readListName(Context ctx) {
        String name = ctx.pathParam("listName");
        if (name.trim().isEmpty() || name.length() > MAX_LIST_NAME_LENGTH || !name.equals(name.trim())) {
            throw HttpError.badBody();
        }
        return name;
    }

    private static String requireUid(Context ctx) {
        String uid = ctx.attribute(AuthFilter.UID_ATTRIBUTE);
        if (uid == null) {
            throw HttpError.unauthorized();
        }
        return uid;
    }

    private IntegrationService requireBackend() {
        if (!backend.databaseConfigured() || backend.integrations() == null) {
            throw HttpError.backendNotConfigured();
        }
        return backend.integrations();
    }
}
