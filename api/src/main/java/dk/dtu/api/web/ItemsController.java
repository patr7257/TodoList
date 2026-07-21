package dk.dtu.api.web;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dk.dtu.api.auth.AuthFilter;
import dk.dtu.api.domain.ColumnValue;
import dk.dtu.api.domain.ItemRow;
import dk.dtu.api.domain.NewItem;
import dk.dtu.api.domain.TodoService;
import dk.dtu.api.domain.UserRow;

import dk.dtu.shared.TaskStatus;
import io.javalin.http.Context;

/**
 * POST /api/todo/items and PATCH|DELETE /api/todo/items/{id}, mirroring the
 * website exactly, including its validation quirks:
 *
 * <ul>
 *   <li>On create, {@code listId} and {@code text} are required; {@code priority},
 *       {@code dueAt} and {@code status} default to null/null/NOT_STARTED when
 *       absent; but {@code description}, {@code location} and {@code assigneeId}
 *       must be PRESENT (they may be null) or the request is 400. This matches
 *       the website route, where those three go through readNullableText/
 *       readAssignee without an "absent -> default" guard.</li>
 *   <li>status and done are kept consistent: an explicit status wins and derives
 *       done; otherwise a legacy done toggle derives the status.</li>
 * </ul>
 *
 * Limits: text 1000, description 4000, location 500.
 */
public final class ItemsController {

    private static final int MAX_TEXT_LENGTH = 1000;
    private static final int MAX_DESCRIPTION_LENGTH = 4000;
    private static final int MAX_LOCATION_LENGTH = 500;

    private final Backend backend;

    public ItemsController(Backend backend) {
        this.backend = backend;
    }

    // -- create ----------------------------------------------------------------

    public void create(Context ctx) {
        TodoService todo = requireBackend();
        Body body = Body.parse(ctx.body());

        if (!body.isString("listId") || body.asString("listId").isEmpty()) {
            throw HttpError.badBody();
        }
        String listId = body.asString("listId");

        if (!body.isString("text")) {
            throw HttpError.badBody();
        }
        String text = body.asString("text");
        if (text.trim().isEmpty() || text.length() > MAX_TEXT_LENGTH) {
            throw HttpError.badBody();
        }

        // priority / dueAt / status default when absent.
        Integer priority = body.has("priority") ? readPriority(body, "priority") : null;
        Instant dueAt = body.has("dueAt") ? readDueAt(body, "dueAt") : null;
        String status = body.has("status") ? readStatus(body, "status") : "NOT_STARTED";

        // description / location / assigneeId must be present (may be null).
        if (!body.has("description")) {
            throw HttpError.badBody();
        }
        String description = readNullableText(body, "description", MAX_DESCRIPTION_LENGTH);
        if (!body.has("location")) {
            throw HttpError.badBody();
        }
        String location = readNullableText(body, "location", MAX_LOCATION_LENGTH);
        if (!body.has("assigneeId")) {
            throw HttpError.badBody();
        }
        String assigneeId = readAssignee(body, "assigneeId");

        if (!todo.listExists(listId)) {
            throw HttpError.notFound();
        }

        String uid = ctx.attribute(AuthFilter.UID_ATTRIBUTE);
        ItemRow created = todo.insertItem(new NewItem(
                listId, text.trim(), description, status, priority, dueAt, location, assigneeId, uid));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("item", Views.item(created, assigneeNameMap(todo)));
        ctx.json(out);
    }

    // -- update ----------------------------------------------------------------

    public void update(Context ctx) {
        TodoService todo = requireBackend();
        String id = ctx.pathParam("id");
        Body body = Body.parse(ctx.body());

        List<ColumnValue> sets = new ArrayList<>();

        if (body.has("text")) {
            if (!body.isString("text")) {
                throw HttpError.badBody();
            }
            String text = body.asString("text");
            if (text.trim().isEmpty() || text.length() > MAX_TEXT_LENGTH) {
                throw HttpError.badBody();
            }
            sets.add(new ColumnValue("text", ":text", text.trim(), Types.VARCHAR));
        }

        // status wins over done; otherwise a legacy done toggle derives status.
        if (body.has("status")) {
            String status = readStatus(body, "status");
            sets.add(new ColumnValue("status", "CAST(:status AS todo_status)", status, Types.VARCHAR));
            sets.add(new ColumnValue("done", ":done", "DONE".equals(status), Types.BOOLEAN));
        } else if (body.has("done")) {
            if (!body.isBoolean("done")) {
                throw HttpError.badBody();
            }
            boolean done = body.asBoolean("done");
            sets.add(new ColumnValue("done", ":done", done, Types.BOOLEAN));
            sets.add(new ColumnValue("status", "CAST(:status AS todo_status)",
                    done ? "DONE" : "NOT_STARTED", Types.VARCHAR));
        }

        if (body.has("description")) {
            String description = readNullableText(body, "description", MAX_DESCRIPTION_LENGTH);
            sets.add(new ColumnValue("description", ":description", description, Types.VARCHAR));
        }
        if (body.has("location")) {
            String location = readNullableText(body, "location", MAX_LOCATION_LENGTH);
            sets.add(new ColumnValue("location", ":location", location, Types.VARCHAR));
        }
        if (body.has("assigneeId")) {
            String assigneeId = readAssignee(body, "assigneeId");
            sets.add(new ColumnValue("assignee_id", "CAST(:assignee_id AS uuid)", assigneeId, Types.VARCHAR));
        }
        if (body.has("priority")) {
            Integer priority = readPriority(body, "priority");
            sets.add(new ColumnValue("priority", ":priority", priority, Types.SMALLINT));
        }
        if (body.has("dueAt")) {
            Instant dueAt = readDueAt(body, "dueAt");
            Timestamp ts = dueAt == null ? null : Timestamp.from(dueAt);
            sets.add(new ColumnValue("due_at", ":due_at", ts, Types.TIMESTAMP));
        }
        if (body.has("sort")) {
            if (!body.isInteger("sort")) {
                throw HttpError.badBody();
            }
            sets.add(new ColumnValue("sort", ":sort", body.asInt("sort"), Types.INTEGER));
        }

        Optional<ItemRow> updated = todo.updateItem(id, sets);
        if (updated.isEmpty()) {
            throw HttpError.notFound();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("item", Views.item(updated.get(), assigneeNameMap(todo)));
        ctx.json(out);
    }

    public void delete(Context ctx) {
        TodoService todo = requireBackend();
        String id = ctx.pathParam("id");
        if (!todo.deleteItem(id)) {
            throw HttpError.notFound();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        ctx.json(out);
    }

    // -- field readers (mirror the website's read* helpers) --------------------

    /** null on JSON null, integer value on integer, else 400. */
    private static Integer readPriority(Body body, String key) {
        if (body.isNull(key)) {
            return null;
        }
        if (!body.isInteger(key)) {
            throw HttpError.badBody();
        }
        return body.asInt(key);
    }

    /** null on JSON null, parsed instant on an ISO string, else 400. */
    private static Instant readDueAt(Body body, String key) {
        if (body.isNull(key)) {
            return null;
        }
        if (!body.isString(key)) {
            throw HttpError.badBody();
        }
        return parseDate(body.asString(key));
    }

    /** One of the known statuses, else 400. */
    private static String readStatus(Body body, String key) {
        if (!body.isString(key)) {
            throw HttpError.badBody();
        }
        String value = body.asString(key);
        for (TaskStatus s : TaskStatus.values()) {
            if (s.name().equals(value)) {
                return value;
            }
        }
        throw HttpError.badBody();
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

    /** null on JSON null, non-empty string id, else 400. */
    private static String readAssignee(Body body, String key) {
        if (body.isNull(key)) {
            return null;
        }
        if (!body.isString(key) || body.asString(key).isEmpty()) {
            throw HttpError.badBody();
        }
        return body.asString(key);
    }

    private static Instant parseDate(String value) {
        // Accept the common ISO-8601 forms JS Date would accept from the app.
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            throw HttpError.badBody();
        }
    }

    private static Map<String, String> assigneeNameMap(TodoService todo) {
        Map<String, String> names = new LinkedHashMap<>();
        for (UserRow u : todo.allUsersByName()) {
            names.put(u.id(), u.name());
        }
        return names;
    }

    private TodoService requireBackend() {
        if (!backend.databaseConfigured()) {
            throw HttpError.backendNotConfigured();
        }
        return backend.todo();
    }
}
