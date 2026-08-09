package dk.dtu.api.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import dk.dtu.api.domain.Completion;
import dk.dtu.api.domain.ItemRow;
import dk.dtu.api.domain.ListRow;
import dk.dtu.api.domain.ShareRow;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;

/**
 * Locks down the public share payload (issue #52): the exact key set and order,
 * and above all the keys and values that must NEVER appear in it. The public
 * payload is the only unauthenticated output of this API, so "what is missing"
 * is the contract that matters here, not "what is present".
 */
class ShareViewsTest {

    /** Same settings as the production mapper, so a null-valued leak is still caught. */
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private static final List<String> EXPECTED_LIST_KEY_ORDER = List.of(
            "name", "description", "sharedBy", "itemCount", "doneCount",
            "completionPercentage", "expiresAt");

    private static final List<String> EXPECTED_ITEM_KEY_ORDER = List.of(
            "id", "text", "description", "done", "status");

    private static final List<String> EXPECTED_SHARE_KEY_ORDER = List.of(
            "id", "label", "url", "token", "createdAt", "expiresAt", "lastViewedAt", "viewCount");

    /** Every internal field populated, so an accidental leak has something to leak. */
    private static ListRow fullList() {
        return new ListRow(
                "11111111-1111-1111-1111-111111111111", "Christmas wishlist", 7,
                Instant.parse("2026-01-02T03:04:05Z"),
                "Patrick Owner Text", 1, 2026, "Brede Alle 12, Kongens Lyngby",
                "Things I would like", "[{\"col\":\"x\"}]",
                "22222222-2222-2222-2222-222222222222");
    }

    private static ItemRow fullItem(String id, String status, boolean done) {
        return new ItemRow(
                id, "11111111-1111-1111-1111-111111111111", "A very specific gift",
                "in the blue box", done, status,
                1, Instant.parse("2026-12-24T17:00:00Z"), "Illum, Copenhagen",
                "33333333-3333-3333-3333-333333333333", 4,
                "44444444-4444-4444-4444-444444444444",
                Instant.parse("2026-02-02T02:02:02Z"), Instant.parse("2026-03-03T03:03:03Z"),
                2026);
    }

    // -- key sets and order -----------------------------------------------------

    @Test
    void publicListHasExactKeySetAndOrder() {
        Map<String, Object> m = ShareViews.publicList(fullList(),
                List.of(fullItem("i1", "DONE", true)), "Patrick", null);
        assertEquals(EXPECTED_LIST_KEY_ORDER, List.copyOf(m.keySet()),
                "the public list object's keys are a hard contract: nothing may be added silently");
    }

    @Test
    void publicItemHasExactKeySetAndOrder() {
        Map<String, Object> m = ShareViews.publicItem(fullItem("i1", "IN_PROGRESS", false));
        assertEquals(EXPECTED_ITEM_KEY_ORDER, List.copyOf(m.keySet()));
    }

    @Test
    void publicPayloadIsListThenItems() {
        Map<String, Object> m = ShareViews.publicPayload(fullList(),
                List.of(fullItem("i1", "DONE", true)), "Patrick", null);
        assertEquals(List.of("list", "items"), List.copyOf(m.keySet()));
    }

    @Test
    void managementShareViewHasExactKeySetAndOrder() {
        Map<String, Object> m = ShareViews.share(shareRow(), "https://patrickrobel.dk");
        assertEquals(EXPECTED_SHARE_KEY_ORDER, List.copyOf(m.keySet()));
    }

    // -- what must never leak ---------------------------------------------------

    @Test
    void publicPayloadContainsNoneOfTheForbiddenKeyNames() {
        String json = GSON.toJson(ShareViews.publicPayload(fullList(),
                List.of(fullItem("i1", "DONE", true)), "Patrick", null));

        // List-level. "description" is legitimately present, so it is not here.
        for (String forbidden : List.of("ownerId", "ownerName", "owner", "taskColumnsJson",
                "location", "priority", "year", "sort", "createdAt")) {
            assertFalse(json.contains("\"" + forbidden + "\""),
                    "forbidden key '" + forbidden + "' leaked into the public payload: " + json);
        }
        // Item-level.
        for (String forbidden : List.of("listId", "assigneeId", "assigneeName", "createdBy",
                "dueAt", "updatedAt")) {
            assertFalse(json.contains("\"" + forbidden + "\""),
                    "forbidden key '" + forbidden + "' leaked into the public payload: " + json);
        }
    }

    @Test
    void publicPayloadContainsNoInternalIdentifierOrLocationValues() {
        String json = GSON.toJson(ShareViews.publicPayload(fullList(),
                List.of(fullItem("i1", "DONE", true)), "Patrick", null));

        // No users.id value may ever cross an unauthenticated boundary, and the
        // list id is the key every authenticated route is addressed by.
        assertFalse(json.contains("11111111-1111-1111-1111-111111111111"), "list id leaked: " + json);
        assertFalse(json.contains("22222222-2222-2222-2222-222222222222"), "owner_id leaked: " + json);
        assertFalse(json.contains("33333333-3333-3333-3333-333333333333"), "assignee_id leaked: " + json);
        assertFalse(json.contains("44444444-4444-4444-4444-444444444444"), "created_by leaked: " + json);
        // location can be a home address.
        assertFalse(json.contains("Brede Alle"), "list location leaked: " + json);
        assertFalse(json.contains("Illum"), "item location leaked: " + json);
        // The free-text owner column is a display value the V4 backfill left
        // unresolved in the ambiguous cases; sharedBy is the resolved name only.
        assertFalse(json.contains("Patrick Owner Text"), "lists.owner free text leaked: " + json);

        // ...and the things that are supposed to be there, are.
        assertTrue(json.contains("Christmas wishlist"));
        assertTrue(json.contains("A very specific gift"));
    }

    // -- values -----------------------------------------------------------------

    @Test
    void completionPercentageAlwaysMatchesTheSharedCompletionDerivation() {
        List<ItemRow> items = List.of(
                fullItem("i1", "NOT_STARTED", false),
                fullItem("i2", "IN_PROGRESS", false),
                fullItem("i3", "DONE", true));
        Map<String, Object> m = ShareViews.publicList(fullList(), items, "Patrick", null);
        assertEquals(Completion.forItems(items), m.get("completionPercentage"),
                "the shared view must never compute completion its own way");
        assertEquals(50, m.get("completionPercentage"), "0 + 50 + 100 = 150 / 3 = 50");
        assertEquals(3, m.get("itemCount"));
        assertEquals(1, m.get("doneCount"));
    }

    @Test
    void emptyAndNullItemListsAreZeroNotACrash() {
        Map<String, Object> empty = ShareViews.publicList(fullList(), List.of(), "Patrick", null);
        assertEquals(0, empty.get("itemCount"));
        assertEquals(0, empty.get("doneCount"));
        assertEquals(0, empty.get("completionPercentage"));

        Map<String, Object> nullItems = ShareViews.publicPayload(fullList(), null, "Patrick", null);
        assertEquals(List.of(), nullItems.get("items"));
    }

    @Test
    void nullSharedByAndNullDescriptionStillEmitTheirKeys() {
        ListRow noDescription = new ListRow(
                "11111111-1111-1111-1111-111111111111", "Plain list", 0,
                Instant.parse("2026-01-02T03:04:05Z"),
                null, null, null, null, null, null, null);

        Map<String, Object> m = ShareViews.publicList(noDescription, List.of(), null, null);
        assertEquals(EXPECTED_LIST_KEY_ORDER, List.copyOf(m.keySet()),
                "a null value must still emit its key, so clients can rely on the shape");
        assertTrue(m.containsKey("sharedBy"));
        assertTrue(m.containsKey("description"));
        assertTrue(m.containsKey("expiresAt"));
        assertNull(m.get("sharedBy"));
        assertNull(m.get("description"));
        assertNull(m.get("expiresAt"));
    }

    @Test
    void expiresAtIsIsoFormattedTheSameWayEveryOtherViewFormatsInstants() {
        Instant expiry = Instant.parse("2026-12-24T17:00:00Z");
        Map<String, Object> m = ShareViews.publicList(fullList(), List.of(), "Patrick", expiry);
        assertEquals(Views.iso(expiry), m.get("expiresAt"));
        assertEquals("2026-12-24T17:00:00Z", m.get("expiresAt"));
    }

    // -- the management view's composed url -------------------------------------

    @Test
    void shareUrlIsComposedFromTheConfiguredBaseAndTheToken() {
        Map<String, Object> m = ShareViews.share(shareRow(), "https://example.test");
        assertEquals("https://example.test/s/tok_abcdefghijklmnopqrstuvwxyz01234", m.get("url"));
        assertEquals("tok_abcdefghijklmnopqrstuvwxyz01234", m.get("token"));
        assertEquals(11, m.get("viewCount"));
        assertEquals("phone", m.get("label"));
    }

    private static ShareRow shareRow() {
        return new ShareRow(
                "55555555-5555-5555-5555-555555555555",
                "11111111-1111-1111-1111-111111111111",
                "tok_abcdefghijklmnopqrstuvwxyz01234",
                "phone",
                "44444444-4444-4444-4444-444444444444",
                Instant.parse("2026-05-05T05:05:05Z"),
                null,
                null,
                Instant.parse("2026-06-06T06:06:06Z"),
                11);
    }
}
