package dk.dtu.api.web;

import java.util.LinkedHashMap;
import java.util.Map;

import dk.dtu.api.domain.ItemRow;
import dk.dtu.api.domain.TinderDeckRow;
import dk.dtu.api.domain.TinderEntryRow;
import dk.dtu.api.domain.TinderMatchRow;
import dk.dtu.api.domain.TinderService;
import dk.dtu.api.domain.TinderSwipeRow;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * JSON shapes for TodoTinder (issue #56), as ordered maps (mirrors
 * {@link Views} and {@link CounterViews}).
 *
 * <p>Every field is written out by hand here, and in particular the item shape
 * returned after a right swipe does NOT delegate to {@code Views.item}. That is
 * the same discipline {@link ShareViews} applies, for a weaker but real reason:
 * {@code Views} feeds {@code GET /api/todo/state}, which is append-only and
 * consumed verbatim by the website, so its shape is governed by a contract that
 * has nothing to do with this feature. Borrowing it would make every field
 * appended there show up in a swipe response too, and would make
 * {@code ViewsTest} the gate on a change to a tinder response. The swipe reply
 * only has to say "here is the item I just made", which is four fields.
 * Timestamps go through the package-private {@link Views#iso(java.time.Instant)}
 * so every surface agrees on the ISO-8601 format.
 *
 * <p>{@code metadata} is re-parsed from the raw jsonb text on the way out, so
 * clients receive a real JSON object rather than a string holding JSON. Gson
 * serializes a {@link JsonElement} straight through, which is why the map value
 * is an element and not a {@code Map}: it avoids a parse-into-map-then-serialize
 * round trip that would reorder and retype the dataset's own values.
 */
public final class TinderViews {

    private TinderViews() {
    }

    /**
     * A deck plus the caller's own counts:
     * {key, displayName, recycleMode, datasetKey, targetListId, total, remaining,
     * needsRefill, refillPrompt}.
     *
     * <p>The deck's uuid is deliberately absent. Every route is keyed on the
     * deck KEY, so publishing the id would offer clients a second handle that no
     * route accepts, and the first thing anyone would do with it is build a URL
     * that 404s. {@code active} is absent for the opposite reason: only active
     * decks are ever returned, so the field could only ever be true.
     *
     * <p>{@code refillPrompt} is null unless {@code needsRefill}, and computing
     * it costs a query for the deck's metadata keys, so the caller passes it in
     * already resolved rather than this view reaching for a service.
     */
    public static Map<String, Object> deck(TinderDeckRow d, TinderService.DeckCounts counts,
                                           boolean needsRefill, String refillPrompt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", d.key());
        m.put("displayName", d.displayName());
        m.put("recycleMode", d.recycleMode());
        m.put("datasetKey", d.datasetKey());
        m.put("targetListId", d.targetListId());
        m.put("total", counts.total());
        m.put("remaining", counts.remaining());
        m.put("needsRefill", needsRefill);
        m.put("refillPrompt", refillPrompt);
        return m;
    }

    /** One card: {id, text, metadata, source}. */
    public static Map<String, Object> card(TinderEntryRow e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.id());
        m.put("text", e.text());
        m.put("metadata", metadata(e.metadata()));
        m.put("source", e.source());
        return m;
    }

    /** One recorded swipe: {entryId, direction, createdAt}. */
    public static Map<String, Object> swipe(TinderSwipeRow s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("entryId", s.entryId());
        m.put("direction", s.direction());
        m.put("createdAt", Views.iso(s.createdAt()));
        return m;
    }

    /**
     * The item a right swipe created: {id, listId, text, status}.
     *
     * <p>Just enough for the swipe app to confirm what happened and link to it.
     * Anything more would be re-deriving {@code Views.item}, which this class
     * exists not to do.
     */
    public static Map<String, Object> createdItem(ItemRow item) {
        if (item == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", item.id());
        m.put("listId", item.listId());
        m.put("text", item.text());
        m.put("status", item.status());
        return m;
    }

    /** One couple match: {entryId, deckKey, deckDisplayName, text, metadata, matchedAt}. */
    public static Map<String, Object> match(TinderMatchRow m0) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("entryId", m0.entryId());
        m.put("deckKey", m0.deckKey());
        m.put("deckDisplayName", m0.deckDisplayName());
        m.put("text", m0.text());
        m.put("metadata", metadata(m0.metadata()));
        m.put("matchedAt", Views.iso(m0.matchedAt()));
        return m;
    }

    /**
     * Raw jsonb text to a JSON element, falling back to an empty object.
     *
     * <p>The fallback is not defensive noise: the column is {@code NOT NULL
     * DEFAULT '{}'}, but a row written before this feature settled, or one
     * holding a scalar rather than an object, must not make an entire deck
     * unreadable. A card with odd metadata should render with no metadata, not
     * take the response down with it.
     */
    static JsonElement metadata(String raw) {
        if (raw == null || raw.isBlank()) {
            return new JsonObject();
        }
        try {
            JsonElement parsed = JsonParser.parseString(raw);
            return parsed != null && parsed.isJsonObject() ? parsed : new JsonObject();
        } catch (JsonParseException e) {
            return new JsonObject();
        }
    }
}
