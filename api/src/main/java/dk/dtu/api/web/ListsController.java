package dk.dtu.api.web;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import dk.dtu.api.domain.ListRow;
import dk.dtu.api.domain.TodoService;

import io.javalin.http.Context;

/**
 * POST /api/todo/lists and PATCH|DELETE /api/todo/lists/{id}, mirroring the
 * website. Name is trimmed and limited to 200 chars; sort must be an integer.
 * Responses: {@code {list: <row>}} on create/update, {@code {ok:true}} on
 * delete, 404 when the id is unknown.
 */
public final class ListsController {

    private static final int MAX_NAME_LENGTH = 200;

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

        ListRow created = todo.insertList(name.trim());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("list", Views.list(created));
        ctx.json(out);
    }

    public void update(Context ctx) {
        TodoService todo = requireBackend();
        String id = ctx.pathParam("id");
        Body body = Body.parse(ctx.body());

        String name = null;
        Integer sort = null;

        if (body.has("name")) {
            if (!body.isString("name")) {
                throw HttpError.badBody();
            }
            String raw = body.asString("name");
            if (raw.trim().isEmpty() || raw.length() > MAX_NAME_LENGTH) {
                throw HttpError.badBody();
            }
            name = raw.trim();
        }
        if (body.has("sort")) {
            if (!body.isInteger("sort")) {
                throw HttpError.badBody();
            }
            sort = body.asInt("sort");
        }
        if (name == null && sort == null) {
            throw HttpError.badBody();
        }

        Optional<ListRow> updated = todo.updateList(id, name, sort);
        if (updated.isEmpty()) {
            throw HttpError.notFound();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("list", Views.list(updated.get()));
        ctx.json(out);
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
