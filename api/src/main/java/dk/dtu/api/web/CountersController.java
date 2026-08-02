package dk.dtu.api.web;

import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dk.dtu.api.auth.AuthFilter;
import dk.dtu.api.domain.ColumnValue;
import dk.dtu.api.domain.CounterRow;
import dk.dtu.api.domain.CountersService;

import io.javalin.http.Context;

/**
 * GET /api/todo/counters, POST /api/todo/counters, and PATCH|DELETE
 * /api/todo/counters/{id}: the shared, manually maintained "fun counters"
 * (Total Flights, Total Ships, Tour de Brede walks, ...). Auto-protected by
 * the global {@link AuthFilter} (it exempts only paths ending {@code /login}
 * and {@code /logout}).
 *
 * <p>PATCH also accepts a relative {@code delta} bump alongside any of the
 * normal fields (but not together with {@code value}): {@code SET value =
 * value + :value} so two concurrent +1s both land instead of racing on a
 * read-modify-write.
 */
public final class CountersController {

    private static final int MAX_LABEL_LENGTH = 200;
    private static final int MAX_DESCRIPTION_LENGTH = 4000;
    private static final int MAX_ICON_LENGTH = 64;

    private final Backend backend;

    public CountersController(Backend backend) {
        this.backend = backend;
    }

    public void list(Context ctx) {
        CountersService counters = requireBackend();
        List<Map<String, Object>> out = new ArrayList<>();
        for (CounterRow c : counters.allOrdered()) {
            out.add(CounterViews.counter(c));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("counters", out);
        ctx.json(body);
    }

    public void create(Context ctx) {
        CountersService counters = requireBackend();
        Body body = Body.parse(ctx.body());

        if (!body.isString("label")) {
            throw HttpError.badBody();
        }
        String label = body.asString("label");
        if (label.trim().isEmpty() || label.length() > MAX_LABEL_LENGTH) {
            throw HttpError.badBody();
        }

        String description = body.has("description")
                ? readNullableText(body, "description", MAX_DESCRIPTION_LENGTH) : null;

        int value = 0;
        if (body.has("value")) {
            if (!body.isInteger("value")) {
                throw HttpError.badBody();
            }
            value = body.asInt("value");
        }

        String icon = body.has("icon") ? readNullableText(body, "icon", MAX_ICON_LENGTH) : null;

        // "sort" is deliberately NOT part of the create contract: it is not read
        // from the body even if present (an explicit choice, not an oversight),
        // so a client cannot make a new counter collide with an existing sort.
        // CountersService.insert always computes it as max(sort)+1. Reordering
        // is done exclusively via PATCH .../{id}, one row at a time.
        String uid = ctx.attribute(AuthFilter.UID_ATTRIBUTE);
        CounterRow created = counters.insert(label.trim(), description, value, icon, uid);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("counter", CounterViews.counter(created));
        ctx.json(out);
    }

    public void update(Context ctx) {
        CountersService counters = requireBackend();
        String id = ctx.pathParam("id");
        Body body = Body.parse(ctx.body());

        List<ColumnValue> sets = new ArrayList<>();

        if (body.has("label")) {
            if (!body.isString("label")) {
                throw HttpError.badBody();
            }
            String raw = body.asString("label");
            if (raw.trim().isEmpty() || raw.length() > MAX_LABEL_LENGTH) {
                throw HttpError.badBody();
            }
            sets.add(new ColumnValue("label", ":label", raw.trim(), Types.VARCHAR));
        }
        if (body.has("description")) {
            String description = readNullableText(body, "description", MAX_DESCRIPTION_LENGTH);
            sets.add(new ColumnValue("description", ":description", description, Types.VARCHAR));
        }
        if (body.has("icon")) {
            String icon = readNullableText(body, "icon", MAX_ICON_LENGTH);
            sets.add(new ColumnValue("icon", ":icon", icon, Types.VARCHAR));
        }
        if (body.has("sort")) {
            if (!body.isInteger("sort")) {
                throw HttpError.badBody();
            }
            sets.add(new ColumnValue("sort", ":sort", body.asInt("sort"), Types.INTEGER));
        }

        // "value" (an absolute set) and "delta" (a relative bump) are mutually
        // exclusive: sending both is ambiguous, so it's a 400.
        boolean hasValue = body.has("value");
        boolean hasDelta = body.has("delta");
        if (hasValue && hasDelta) {
            throw HttpError.badBody();
        }
        if (hasValue) {
            if (!body.isInteger("value")) {
                throw HttpError.badBody();
            }
            sets.add(new ColumnValue("value", ":value", body.asInt("value"), Types.INTEGER));
        }
        if (hasDelta) {
            if (!body.isInteger("delta")) {
                throw HttpError.badBody();
            }
            int delta = body.asInt("delta");
            if (delta == 0) {
                throw HttpError.badBody();
            }
            // Relative bump in SQL (not a read-modify-write) so two concurrent
            // +1s both land: SET value = value + :value.
            sets.add(new ColumnValue("value", "value + :value", delta, Types.INTEGER));
        }

        if (sets.isEmpty()) {
            throw HttpError.badBody();
        }

        Optional<CounterRow> updated = counters.update(id, sets);
        if (updated.isEmpty()) {
            throw HttpError.notFound();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("counter", CounterViews.counter(updated.get()));
        ctx.json(out);
    }

    public void delete(Context ctx) {
        CountersService counters = requireBackend();
        String id = ctx.pathParam("id");
        if (!counters.delete(id)) {
            throw HttpError.notFound();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        ctx.json(out);
    }

    // -- field readers (mirror ListsController/ItemsController's read* helpers) -

    /** null on JSON null, trimmed string (empty -> null) up to max, else 400. */
    private static String readNullableText(Body body, String key, int max) {
        if (body.isNull(key)) {
            return null;
        }
        if (!body.isString(key)) {
            throw HttpError.badBody();
        }
        String value = body.asString(key);
        if (value.length() > max) {
            throw HttpError.badBody();
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private CountersService requireBackend() {
        if (!backend.databaseConfigured()) {
            throw HttpError.backendNotConfigured();
        }
        return backend.counters();
    }
}
