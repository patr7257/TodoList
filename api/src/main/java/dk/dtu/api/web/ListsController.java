package dk.dtu.api.web;

import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import dk.dtu.api.domain.ColumnValue;
import dk.dtu.api.domain.ListRow;
import dk.dtu.api.domain.TodoService;
import dk.dtu.api.domain.UserRow;

import io.javalin.http.Context;

/**
 * POST /api/todo/lists and PATCH|DELETE /api/todo/lists/{id}, mirroring the
 * website. Name is trimmed and limited to 200 chars; sort must be an integer.
 * Responses: {@code {list: <row>}} on create/update, {@code {ok:true}} on
 * delete, 404 when the id is unknown.
 *
 * <p>Beyond the website's name/sort, this also persists the desktop-superset
 * list columns: {@code owner}, {@code priority}, {@code year}, {@code location}
 * and {@code description} on PATCH (all nullable, so the desktop can clear a
 * field by sending JSON null), and an optional {@code owner} on create.
 *
 * <p>{@code ownerId} (the V3 real user reference) is accepted on both create
 * and PATCH: a non-null value must parse as a UUID and reference an existing
 * user (else 400, never a 500 from a bare FK violation), and also writes the
 * denormalized {@code owner} name in the same statement; a null value clears
 * both. If a request carries both {@code ownerId} and legacy {@code owner},
 * {@code ownerId} wins and {@code owner} is ignored.
 */
public final class ListsController {

    private static final int MAX_NAME_LENGTH = 200;
    private static final int MAX_OWNER_LENGTH = 200;
    private static final int MAX_LOCATION_LENGTH = 500;
    private static final int MAX_DESCRIPTION_LENGTH = 4000;

    private final Backend backend;

    public ListsController(Backend backend) {
        this.backend = backend;
    }

    public void create(Context ctx) {
        TodoService todo = requireBackend();
        Body body = Body.parse(ctx.body());

        if (!body.isString("name")) {
            throw HttpError.badBody();
        }
        String name = body.asString("name");
        if (name.trim().isEmpty() || name.length() > MAX_NAME_LENGTH) {
            throw HttpError.badBody();
        }

        // owner is optional on create (a desktop-superset column, may be null).
        String owner = body.has("owner") ? readNullableText(body, "owner", MAX_OWNER_LENGTH) : null;

        // ownerId, when present, wins over legacy owner: resolve it to a real
        // user (400 on an unknown/malformed id) and denormalize its name.
        String ownerId = null;
        if (body.has("ownerId")) {
            ownerId = readNullableUuid(body, "ownerId");
            if (ownerId == null) {
                owner = null;
            } else {
                UserRow ownerUser = todo.findUserById(ownerId).orElseThrow(HttpError::badBody);
                owner = ownerUser.name();
            }
        }

        ListRow created = todo.insertList(name.trim(), owner, ownerId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("list", Views.list(created));
        ctx.json(out);
    }

    public void update(Context ctx) {
        TodoService todo = requireBackend();
        String id = ctx.pathParam("id");
        Body body = Body.parse(ctx.body());

        List<ColumnValue> sets = new ArrayList<>();

        if (body.has("name")) {
            if (!body.isString("name")) {
                throw HttpError.badBody();
            }
            String raw = body.asString("name");
            if (raw.trim().isEmpty() || raw.length() > MAX_NAME_LENGTH) {
                throw HttpError.badBody();
            }
            sets.add(new ColumnValue("name", ":name", raw.trim(), Types.VARCHAR));
        }
        if (body.has("sort")) {
            if (!body.isInteger("sort")) {
                throw HttpError.badBody();
            }
            sets.add(new ColumnValue("sort", ":sort", body.asInt("sort"), Types.INTEGER));
        }
        if (body.has("ownerId")) {
            // ownerId wins over a legacy "owner" key present in the same body.
            String ownerId = readNullableUuid(body, "ownerId");
            String ownerName;
            if (ownerId == null) {
                ownerName = null;
            } else {
                UserRow ownerUser = todo.findUserById(ownerId).orElseThrow(HttpError::badBody);
                ownerName = ownerUser.name();
            }
            sets.add(new ColumnValue("owner_id", "CAST(:owner_id AS uuid)", ownerId, Types.VARCHAR));
            sets.add(new ColumnValue("owner", ":owner", ownerName, Types.VARCHAR));
        } else if (body.has("owner")) {
            String owner = readNullableText(body, "owner", MAX_OWNER_LENGTH);
            sets.add(new ColumnValue("owner", ":owner", owner, Types.VARCHAR));
        }
        if (body.has("priority")) {
            Integer priority = readNullableInt(body, "priority");
            sets.add(new ColumnValue("priority", ":priority", priority, Types.INTEGER));
        }
        if (body.has("year")) {
            Integer year = readNullableInt(body, "year");
            sets.add(new ColumnValue("year", ":year", year, Types.INTEGER));
        }
        if (body.has("location")) {
            String location = readNullableText(body, "location", MAX_LOCATION_LENGTH);
            sets.add(new ColumnValue("location", ":location", location, Types.VARCHAR));
        }
        if (body.has("description")) {
            String description = readNullableText(body, "description", MAX_DESCRIPTION_LENGTH);
            sets.add(new ColumnValue("description", ":description", description, Types.VARCHAR));
        }
        if (sets.isEmpty()) {
            throw HttpError.badBody();
        }

        Optional<ListRow> updated = todo.updateList(id, sets);
        if (updated.isEmpty()) {
            throw HttpError.notFound();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("list", Views.list(updated.get()));
        ctx.json(out);
    }

    // -- field readers (mirror ItemsController's read* helpers) ----------------

    /** null on JSON null, integer value on integer, else 400. */
    private static Integer readNullableInt(Body body, String key) {
        if (body.isNull(key)) {
            return null;
        }
        if (!body.isInteger(key)) {
            throw HttpError.badBody();
        }
        return body.asInt(key);
    }

    /**
     * null on JSON null, a syntactically valid UUID string on a string that
     * parses as one, else 400. Existence against {@code users} is checked
     * separately by the caller (a bare FK violation would otherwise surface
     * as an unrelated 500).
     */
    private static String readNullableUuid(Body body, String key) {
        if (body.isNull(key)) {
            return null;
        }
        if (!body.isString(key)) {
            throw HttpError.badBody();
        }
        String value = body.asString(key);
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw HttpError.badBody();
        }
        return value;
    }

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

    public void delete(Context ctx) {
        TodoService todo = requireBackend();
        String id = ctx.pathParam("id");
        // Items cascade via the list_id foreign key's ON DELETE CASCADE.
        if (!todo.deleteList(id)) {
            throw HttpError.notFound();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        ctx.json(out);
    }

    private TodoService requireBackend() {
        if (!backend.databaseConfigured()) {
            throw HttpError.backendNotConfigured();
        }
        return backend.todo();
    }
}
