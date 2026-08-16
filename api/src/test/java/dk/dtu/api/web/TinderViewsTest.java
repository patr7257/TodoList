package dk.dtu.api.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import dk.dtu.api.domain.ItemRow;
import dk.dtu.api.domain.TinderDeckRow;
import dk.dtu.api.domain.TinderEntryRow;
import dk.dtu.api.domain.TinderMatchRow;
import dk.dtu.api.domain.TinderService;
import dk.dtu.api.domain.TinderSwipeRow;

import com.google.gson.JsonElement;
import org.junit.jupiter.api.Test;

/**
 * Pins the TodoTinder JSON shapes without a database or a server, the way
 * {@code ViewsTest} and {@code ShareViewsTest} do for their payloads.
 *
 * <p>Two things are worth pinning here rather than only end to end. The deck
 * object deliberately OMITS the deck's uuid and its {@code active} flag, and
 * "we left something out on purpose" is exactly the kind of decision a later
 * reader undoes by accident; a test that fails on an extra key is what stops
 * that. And metadata has to arrive as a JSON object rather than a string
 * holding JSON, which is invisible in a Java-level assertion on the map and
 * obvious in one on the element type.
 */
class TinderViewsTest {

    private static final Instant WHEN = Instant.parse("2026-08-16T10:30:00Z");

    private static TinderDeckRow deck(String mode) {
        return new TinderDeckRow("deck-uuid", "aktiviteter", "AcTindervitivities",
                "list-uuid", mode, "aktiviteter-da", true, WHEN);
    }

    @Test
    void deckHasExactlyTheseKeysInThisOrder() {
        Map<String, Object> m = TinderViews.deck(deck(TinderDeckRow.MODE_DEPLETE),
                new TinderService.DeckCounts(70, 4), true, "the prompt");

        assertEquals(List.of("key", "displayName", "recycleMode", "datasetKey", "targetListId",
                "total", "remaining", "needsRefill", "refillPrompt"),
                List.copyOf(m.keySet()));
        assertEquals("aktiviteter", m.get("key"));
        assertEquals(70, m.get("total"));
        assertEquals(4, m.get("remaining"));
        assertEquals(Boolean.TRUE, m.get("needsRefill"));
        assertEquals("the prompt", m.get("refillPrompt"));
    }

    @Test
    void deckNeverPublishesItsUuidOrItsActiveFlag() {
        // The routes are keyed on the deck KEY. Publishing the uuid would hand
        // clients a second handle no route accepts, and "active" could only ever
        // be true because an inactive deck is a 404.
        Map<String, Object> m = TinderViews.deck(deck(TinderDeckRow.MODE_RECYCLE),
                new TinderService.DeckCounts(0, 0), false, null);
        assertFalse(m.containsKey("id"), "the deck uuid must never appear in the deck view");
        assertFalse(m.containsKey("active"), "only active decks are ever returned");
        assertNull(m.get("refillPrompt"));
    }

    @Test
    void cardHasExactlyTheseKeysAndMetadataIsARealJsonObject() {
        TinderEntryRow e = new TinderEntryRow("entry-uuid", "deck-uuid", "Tur i Dyrehaven",
                "{\"kategori\":\"ude\"}", "seed", true, WHEN);

        Map<String, Object> m = TinderViews.card(e);
        assertEquals(List.of("id", "text", "metadata", "source"), List.copyOf(m.keySet()));

        Object metadata = m.get("metadata");
        assertTrue(metadata instanceof JsonElement && ((JsonElement) metadata).isJsonObject(),
                "metadata must go out as a JSON object, not a string holding JSON");
        assertEquals("ude", ((JsonElement) metadata).getAsJsonObject().get("kategori").getAsString());
    }

    @Test
    void unusableMetadataDegradesToAnEmptyObjectInsteadOfBreakingTheDeck() {
        // A card with odd metadata should render with no metadata. Taking the
        // whole deck's response down over one row would be a much worse failure.
        assertEquals(0, TinderViews.metadata(null).getAsJsonObject().size());
        assertEquals(0, TinderViews.metadata("   ").getAsJsonObject().size());
        assertEquals(0, TinderViews.metadata("not json at all").getAsJsonObject().size());
        assertEquals(0, TinderViews.metadata("[1,2,3]").getAsJsonObject().size());
        assertEquals(0, TinderViews.metadata("\"a string\"").getAsJsonObject().size());
    }

    @Test
    void swipeAndCreatedItemHaveExactlyTheseKeys() {
        Map<String, Object> s = TinderViews.swipe(new TinderSwipeRow("swipe-uuid", "user-uuid",
                "entry-uuid", TinderSwipeRow.DIRECTION_RIGHT, WHEN));
        assertEquals(List.of("entryId", "direction", "createdAt"), List.copyOf(s.keySet()));
        assertEquals("2026-08-16T10:30:00Z", s.get("createdAt"));

        ItemRow item = new ItemRow("item-uuid", "list-uuid", "Mælk", null, false, "NOT_STARTED",
                null, null, null, null, 0, "user-uuid", WHEN, WHEN, null);
        Map<String, Object> i = TinderViews.createdItem(item);
        assertEquals(List.of("id", "listId", "text", "status"), List.copyOf(i.keySet()));
        assertEquals("Mælk", i.get("text"));

        assertNull(TinderViews.createdItem(null), "no item created means a null item, not an empty object");
    }

    @Test
    void matchHasExactlyTheseKeys() {
        Map<String, Object> m = TinderViews.match(new TinderMatchRow("entry-uuid", "rejsemaal",
                "VacayTinderation", "Lissabon", "{\"prisband\":\"mellem\"}", WHEN));
        assertEquals(List.of("entryId", "deckKey", "deckDisplayName", "text", "metadata", "matchedAt"),
                List.copyOf(m.keySet()));
        assertEquals("2026-08-16T10:30:00Z", m.get("matchedAt"));
    }
}
