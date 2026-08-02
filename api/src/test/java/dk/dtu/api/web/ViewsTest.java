package dk.dtu.api.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import dk.dtu.api.domain.ItemRow;
import dk.dtu.api.domain.ListRow;

import org.junit.jupiter.api.Test;

/**
 * Locks down the exact key set and order of {@link Views#list}'s output: GET
 * /state is append-only and consumed verbatim by a separate website, so the
 * existing keys must never be removed, renamed or reordered, and the two new
 * V3 keys ({@code ownerId}, {@code ownerName}) must land at the end.
 */
class ViewsTest {

    private static final List<String> EXPECTED_LIST_KEY_ORDER = List.of(
            "id", "name", "sort", "createdAt", "owner", "priority", "year",
            "location", "description", "taskColumnsJson", "ownerId", "ownerName");

    private static ListRow row(String owner, String ownerId) {
        return new ListRow(
                "l1", "Inbox", 0, Instant.parse("2026-07-20T12:34:56Z"),
                owner, 3, 2027, "Aarhus", "desc", "[]", ownerId);
    }

    @Test
    void listHasExactKeySetAndOrder() {
        Map<String, Object> m = Views.list(row("Alice", null));
        assertEquals(EXPECTED_LIST_KEY_ORDER, List.copyOf(m.keySet()),
                "GET /state's list object key order must never change (append-only contract)");
    }

    @Test
    void listOneArgOverloadLeavesOwnerNameNull() {
        // Mutation responses from ListsController use this overload: no
        // id-to-name map is available there, so ownerName is intentionally
        // null and the client must not depend on it.
        Map<String, Object> m = Views.list(row("Alice", "u1"));
        assertEquals("u1", m.get("ownerId"));
        assertNull(m.get("ownerName"));
    }

    @Test
    void listWithMapResolvesOwnerNameFromOwnerId() {
        Map<String, String> names = Map.of("u1", "Alice", "u2", "Bob");
        Map<String, Object> m = Views.list(row("Alice", "u1"), names);
        assertEquals("u1", m.get("ownerId"));
        assertEquals("Alice", m.get("ownerName"));
    }

    @Test
    void listWithMapLeavesOwnerNameNullWhenOwnerIdIsNull() {
        Map<String, String> names = Map.of("u1", "Alice");
        Map<String, Object> m = Views.list(row(null, null), names);
        assertNull(m.get("ownerId"));
        assertNull(m.get("ownerName"));
    }

    @Test
    void listWithMapLeavesOwnerNameNullWhenIdIsUnknownToTheMap() {
        Map<String, String> names = Map.of("u2", "Bob"); // does not contain u1
        Map<String, Object> m = Views.list(row("Alice", "u1"), names);
        assertEquals("u1", m.get("ownerId"));
        assertNull(m.get("ownerName"), "an id absent from the map resolves to null, not a KeyError");
    }

    @Test
    void listWithMapNullBehavesLikeTheOneArgOverload() {
        Map<String, Object> m = Views.list(row("Alice", "u1"), null);
        assertEquals("u1", m.get("ownerId"));
        assertNull(m.get("ownerName"));
    }

    @Test
    void listWithItemsKeepsListKeyOrderAndAddsCompletionAndItems() {
        ItemRow item = new ItemRow("i1", "l1", "Buy milk", null, false, "NOT_STARTED",
                null, null, null, "u1", 0, "u1",
                Instant.parse("2026-07-20T12:00:00Z"), Instant.parse("2026-07-20T12:00:00Z"), null);
        Map<String, String> names = Map.of("u1", "Alice");

        Map<String, Object> m = Views.listWithItems(row("Alice", "u1"), List.of(item), names);

        // The first 12 keys (in order) are exactly the list's own keys...
        List<String> firstTwelve = List.copyOf(m.keySet()).subList(0, 12);
        assertEquals(EXPECTED_LIST_KEY_ORDER, firstTwelve);
        // ...then completionPercentage and items are appended after.
        assertTrue(m.containsKey("completionPercentage"));
        assertTrue(m.containsKey("items"));
        assertEquals("Alice", m.get("ownerName"), "listWithItems resolves ownerName from the same id-to-name map");
    }
}
