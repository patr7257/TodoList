package dk.dtu.api.web;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dk.dtu.api.domain.Completion;
import dk.dtu.api.domain.ItemRow;
import dk.dtu.api.domain.ListRow;
import dk.dtu.api.domain.UserRow;

/**
 * Builds the exact JSON shapes the website returns, as ordered maps.
 *
 * <p>Field names, casing (camelCase), and null-inclusion match the website's
 * Drizzle rows / NextResponse.json output. Timestamps become ISO-8601 UTC
 * strings (for example {@code 2026-07-20T12:34:56Z}). Beyond the website's
 * fields, item and list objects also carry the additive, always-present
 * desktop-superset fields (null when unset), plus, for lists, a derived
 * {@code completionPercentage} and, for items, a resolved {@code assigneeName}.
 */
public final class Views {

    private Views() {
    }

    /** {id, name, email} */
    public static Map<String, Object> user(UserRow u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.id());
        m.put("name", u.name());
        m.put("email", u.email());
        return m;
    }

    /** {id, name} for the assignee dropdown source list. */
    public static Map<String, Object> userIdName(UserRow u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.id());
        m.put("name", u.name());
        return m;
    }

    /** Full list row (matches the website's insert/update returning() shape). */
    public static Map<String, Object> list(ListRow l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", l.id());
        m.put("name", l.name());
        m.put("sort", l.sort());
        m.put("createdAt", iso(l.createdAt()));
        // desktop-superset (additive, null when unset)
        m.put("owner", l.owner());
        m.put("priority", l.priority());
        m.put("year", l.year());
        m.put("location", l.location());
        m.put("description", l.description());
        m.put("taskColumnsJson", l.taskColumnsJson());
        return m;
    }

    /** Full item row, with resolved assignee name (assigneeNames may be null). */
    public static Map<String, Object> item(ItemRow it, Map<String, String> assigneeNames) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", it.id());
        m.put("listId", it.listId());
        m.put("text", it.text());
        m.put("description", it.description());
        m.put("done", it.done());
        m.put("status", it.status());
        m.put("priority", it.priority());
        m.put("dueAt", iso(it.dueAt()));
        m.put("location", it.location());
        m.put("assigneeId", it.assigneeId());
        m.put("sort", it.sort());
        m.put("createdBy", it.createdBy());
        m.put("createdAt", iso(it.createdAt()));
        m.put("updatedAt", iso(it.updatedAt()));
        // desktop-superset + resolved name (additive)
        m.put("year", it.year());
        String name = (assigneeNames == null || it.assigneeId() == null)
                ? null : assigneeNames.get(it.assigneeId());
        m.put("assigneeName", name);
        return m;
    }

    /**
     * A list object as it appears inside GET /state: the list fields plus its
     * nested items and a derived completionPercentage.
     */
    public static Map<String, Object> listWithItems(ListRow l, List<ItemRow> items,
                                                     Map<String, String> assigneeNames) {
        Map<String, Object> m = list(l);
        m.put("completionPercentage", Completion.forItems(items));
        List<Map<String, Object>> itemViews = new ArrayList<>(items.size());
        for (ItemRow it : items) {
            itemViews.add(item(it, assigneeNames));
        }
        m.put("items", itemViews);
        return m;
    }

    static String iso(Instant instant) {
        return instant == null ? null : DateTimeFormatter.ISO_INSTANT.format(instant);
    }
}
