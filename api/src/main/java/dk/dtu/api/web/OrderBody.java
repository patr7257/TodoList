package dk.dtu.api.web;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dk.dtu.api.domain.TodoService;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Reads the body of the two bulk reorder routes (issue #77),
 * {@code PUT /api/todo/lists/order} and {@code PUT /api/todo/items/order}:
 *
 * <pre>{@code { "order": [ { "id": "<uuid>", "sort": 0 }, ... ] } }</pre>
 *
 * <p>It lives on its own, and not twice inside the two controllers, because the
 * shape is byte-identical on both routes and a validator that drifts between
 * two copies is how one of them ends up accepting something the other rejects.
 * Being a pure function of the body, it is also unit testable without a
 * database or a port (see {@code OrderBodyTest}).
 *
 * <p>Anything malformed is a 400 and nothing is written: an absent or non-array
 * {@code order}, an element that is not an object, a missing / non-string / non
 * uuid {@code id}, a missing / non-integer {@code sort}, or a batch over
 * {@link #MAX_ENTRIES}. Ids are checked for uuid SYNTAX only; whether they
 * exist is a database question, answered atomically in
 * {@link TodoService#saveListOrder(String, java.util.List)}.
 */
final class OrderBody {

    /**
     * Hard cap on one reorder. It is far above any real arrangement (the whole
     * product is two people's todo lists) and exists only so a runaway client
     * cannot hand the database an unbounded batch inside one transaction.
     */
    static final int MAX_ENTRIES = 1000;

    private OrderBody() {
    }

    /**
     * The parsed entries, in the order given. An empty array is accepted and
     * means "nothing to move": it writes nothing, which is exactly what a
     * reorder that changed nothing should do, and rejecting it would only give
     * the client an error to special-case.
     */
    static List<TodoService.SortEntry> parse(Body body) {
        if (!body.isArray("order")) {
            throw HttpError.badBody();
        }
        JsonArray raw = body.asArray("order");
        if (raw.size() > MAX_ENTRIES) {
            throw HttpError.badBody();
        }

        List<TodoService.SortEntry> entries = new ArrayList<>(raw.size());
        for (JsonElement element : raw) {
            entries.add(readEntry(element));
        }
        return entries;
    }

    /** One element: {@code {id, sort}}, or a 400. */
    private static TodoService.SortEntry readEntry(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            throw HttpError.badBody();
        }
        JsonObject obj = element.getAsJsonObject();

        JsonElement idElement = obj.get("id");
        if (idElement == null || !idElement.isJsonPrimitive()
                || !idElement.getAsJsonPrimitive().isString()) {
            throw HttpError.badBody();
        }
        String id = idElement.getAsString();
        try {
            UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw HttpError.badBody();
        }

        JsonElement sortElement = obj.get("sort");
        if (sortElement == null || !sortElement.isJsonPrimitive()
                || !sortElement.getAsJsonPrimitive().isNumber()) {
            throw HttpError.badBody();
        }
        double sort = sortElement.getAsDouble();
        if (Double.isNaN(sort) || Double.isInfinite(sort) || sort != Math.rint(sort)
                || sort < Integer.MIN_VALUE || sort > Integer.MAX_VALUE) {
            // Integer-valued and in range for the integer column, the same check
            // Body.isInteger makes for the single-row PATCH's "sort" field.
            throw HttpError.badBody();
        }
        return new TodoService.SortEntry(id, (int) sort);
    }
}
