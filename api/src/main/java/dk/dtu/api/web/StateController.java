package dk.dtu.api.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dk.dtu.api.auth.AuthFilter;
import dk.dtu.api.domain.ItemRow;
import dk.dtu.api.domain.ListRow;
import dk.dtu.api.domain.TodoService;
import dk.dtu.api.domain.UserRow;

import io.javalin.http.Context;

/**
 * GET /api/todo/state. Returns the current user plus the whole shared space:
 * every list (ordered by sort, then created_at) with its items nested inside
 * (same ordering), and the full closed set of users as assignee options. Shape
 * matches the website's state route, with the additive superset fields and a
 * derived per-list completionPercentage.
 */
public final class StateController {

    private final Backend backend;

    public StateController(Backend backend) {
        this.backend = backend;
    }

    public void get(Context ctx) {
        if (!backend.databaseConfigured()) {
            throw HttpError.backendNotConfigured();
        }
        String uid = ctx.attribute(AuthFilter.UID_ATTRIBUTE);
        TodoService todo = backend.todo();

        Optional<UserRow> user = todo.findUserById(uid);
        if (user.isEmpty()) {
            throw HttpError.unauthorized();
        }

        List<ListRow> lists = todo.allListsOrdered();
        List<ItemRow> items = todo.allItemsOrdered();
        List<UserRow> users = todo.allUsersByName();

        Map<String, String> assigneeNames = new LinkedHashMap<>();
        for (UserRow u : users) {
            assigneeNames.put(u.id(), u.name());
        }

        Map<String, List<ItemRow>> itemsByList = new LinkedHashMap<>();
        for (ItemRow it : items) {
            itemsByList.computeIfAbsent(it.listId(), k -> new ArrayList<>()).add(it);
        }

        List<Map<String, Object>> usersOut = new ArrayList<>(users.size());
        for (UserRow u : users) {
            usersOut.add(Views.userIdName(u));
        }

        List<Map<String, Object>> listsOut = new ArrayList<>(lists.size());
        for (ListRow l : lists) {
            List<ItemRow> listItems = itemsByList.getOrDefault(l.id(), List.of());
            listsOut.add(Views.listWithItems(l, listItems, assigneeNames));
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("user", Views.user(user.get()));
        out.put("users", usersOut);
        out.put("lists", listsOut);

        ctx.header("Cache-Control", "no-store");
        ctx.json(out);
    }
}
