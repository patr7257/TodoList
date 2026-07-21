package dk.dtu;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ViewPrefs}: JSON round-trip, per-user keying, and
 * graceful handling of stale/unknown/malformed state. None of these need a
 * running JavaFX stage.
 */
public class ViewPrefsTest {

    private static ViewPrefs.ViewState sample() {
        ViewPrefs.ViewState s = new ViewPrefs.ViewState();
        s.columns = List.of("reorder", "title", "status", "delete");
        s.widths.put("title", 180.0);
        s.widths.put("status", 90.5);
        s.sortColumn = "status";
        s.sortAscending = false;
        s.filters.put("owner", "alice");
        s.filters.put("status", "DONE");
        return s;
    }

    // -- serialization ---------------------------------------------------------

    @Test
    public void testJsonRoundTripPreservesAllFields() {
        ViewPrefs.ViewState original = sample();
        String json = ViewPrefs.toJson(original);
        ViewPrefs.ViewState back = ViewPrefs.fromJson(json);

        assertEquals(original.columns, back.columns);
        assertEquals(180.0, back.widths.get("title"));
        assertEquals(90.5, back.widths.get("status"));
        assertEquals("status", back.sortColumn);
        assertFalse(back.sortAscending);
        assertEquals("alice", back.filters.get("owner"));
        assertEquals("DONE", back.filters.get("status"));
    }

    @Test
    public void testFromJsonHandlesNullBlankAndMalformed() {
        // Never throws, always returns a usable (empty) state.
        assertTrue(ViewPrefs.fromJson(null).columns.isEmpty());
        assertTrue(ViewPrefs.fromJson("").columns.isEmpty());
        assertTrue(ViewPrefs.fromJson("   ").widths.isEmpty());
        assertTrue(ViewPrefs.fromJson("not-json-at-all").columns.isEmpty());
        assertTrue(ViewPrefs.fromJson("{bad json").filters.isEmpty());

        // Partial JSON: missing collections come back non-null.
        ViewPrefs.ViewState partial = ViewPrefs.fromJson("{\"sortColumn\":\"x\"}");
        assertNotNull(partial.columns);
        assertNotNull(partial.widths);
        assertNotNull(partial.filters);
        assertEquals("x", partial.sortColumn);
    }

    // -- sanitize (stale / unknown columns) ------------------------------------

    @Test
    public void testSanitizedDropsUnknownColumnsWidthsAndSort() {
        ViewPrefs.ViewState s = new ViewPrefs.ViewState();
        s.columns = new java.util.ArrayList<>(List.of("title", "ghost", "status"));
        s.widths.put("title", 100.0);
        s.widths.put("ghost", 50.0);   // unknown column
        s.widths.put("status", -5.0);  // invalid width
        s.sortColumn = "ghost";        // unknown sort column
        s.filters.put("owner", "bob");

        ViewPrefs.ViewState clean = s.sanitized(List.of("title", "status", "delete"));

        assertEquals(List.of("title", "status"), clean.columns, "unknown column dropped");
        assertTrue(clean.widths.containsKey("title"));
        assertFalse(clean.widths.containsKey("ghost"), "unknown width dropped");
        assertFalse(clean.widths.containsKey("status"), "non-positive width dropped");
        assertNull(clean.sortColumn, "unknown sort column cleared");
        assertEquals("bob", clean.filters.get("owner"), "filters are preserved");
    }

    @Test
    public void testSanitizedKeepsKnownSortAndToleratesNullKnownIds() {
        ViewPrefs.ViewState s = new ViewPrefs.ViewState();
        s.sortColumn = "title";
        ViewPrefs.ViewState clean = s.sanitized(List.of("title"));
        assertEquals("title", clean.sortColumn);

        // Null known-ids must not throw; everything column-bound drops out.
        ViewPrefs.ViewState clean2 = sample().sanitized(null);
        assertTrue(clean2.columns.isEmpty());
        assertTrue(clean2.widths.isEmpty());
        assertNull(clean2.sortColumn);
    }

    // -- per-user keying -------------------------------------------------------

    @Test
    public void testPerUserKeyingIsolatesState() throws Exception {
        String userA = "userA-" + UUID.randomUUID();
        String userB = "userB-" + UUID.randomUUID();
        String view = "lists";
        try {
            ViewPrefs.ViewState a = new ViewPrefs.ViewState();
            a.columns = List.of("reorder", "name", "delete");
            a.filters.put("owner", "alice");

            ViewPrefs.ViewState b = new ViewPrefs.ViewState();
            b.columns = List.of("reorder", "name", "owner", "delete");
            b.filters.put("owner", "bob");

            ViewPrefs.save(userA, view, a);
            ViewPrefs.save(userB, view, b);

            ViewPrefs.ViewState loadedA = ViewPrefs.load(userA, view);
            ViewPrefs.ViewState loadedB = ViewPrefs.load(userB, view);

            assertEquals(a.columns, loadedA.columns);
            assertEquals("alice", loadedA.filters.get("owner"));
            assertEquals(b.columns, loadedB.columns);
            assertEquals("bob", loadedB.filters.get("owner"));
            assertNotEquals(loadedA.columns, loadedB.columns, "two users keep separate layouts");
        } finally {
            removeNode(userA);
            removeNode(userB);
        }
    }

    @Test
    public void testMissingViewLoadsEmptyDefault() {
        String user = "userNone-" + UUID.randomUUID();
        ViewPrefs.ViewState loaded = ViewPrefs.load(user, "tasks");
        assertNotNull(loaded);
        assertTrue(loaded.columns.isEmpty());
        assertTrue(loaded.widths.isEmpty());
        assertNull(loaded.sortColumn);
    }

    @Test
    public void testCurrentUserKeyFallsBackToAnonWithoutLogin() {
        // No session is configured in a plain unit test, so the key is anonymous.
        assertEquals("anon", ViewPrefs.currentUserKey());
    }

    @Test
    public void testSanitizeSegmentBoundsAndCharacters() {
        assertEquals("anon", ViewPrefs.sanitizeSegment(null));
        assertEquals("anon", ViewPrefs.sanitizeSegment("   "));
        assertEquals("a_b_c", ViewPrefs.sanitizeSegment("a/b\\c"));
        // Over-long keys are hashed to stay within the Preferences node limit.
        String longKey = "x".repeat(200);
        assertTrue(ViewPrefs.sanitizeSegment(longKey).length() <= 80);
    }

    private static void removeNode(String userKey) throws Exception {
        String path = "dk/dtu/viewstate/" + ViewPrefs.sanitizeSegment(userKey);
        if (Preferences.userRoot().nodeExists(path)) {
            Preferences.userRoot().node(path).removeNode();
        }
    }
}
