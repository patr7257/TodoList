package dk.dtu.api.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dk.dtu.api.auth.AuthFilter;
import dk.dtu.api.domain.TinderDeckRow;
import dk.dtu.api.domain.TinderEntryRow;
import dk.dtu.api.domain.TinderMatchRow;
import dk.dtu.api.domain.TinderService;
import dk.dtu.api.domain.TinderSwipeRow;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.javalin.http.Context;

/**
 * The TodoTinder routes (issues #56 and #59), all under
 * {@code /api/todo/tinder/} and all authenticated:
 *
 * <ul>
 *   <li>{@code GET  /api/todo/tinder/decks} the active decks plus, per deck,
 *       how many cards are left FOR THE CALLER</li>
 *   <li>{@code GET  /api/todo/tinder/decks/{deck}/cards} the next cards</li>
 *   <li>{@code POST /api/todo/tinder/decks/{deck}/swipes} record one swipe</li>
 *   <li>{@code GET  /api/todo/tinder/matches} cards every user liked</li>
 *   <li>{@code POST /api/todo/tinder/decks/{deck}/entries} the refill import</li>
 * </ul>
 *
 * <p>Nothing here touches {@link AuthFilter}, and that is the point: every route
 * above is protected automatically, because authenticated is the DEFAULT and the
 * filter's allowlist is a closed set of three entries (login, logout, and the
 * one public share reader). The API has exactly one unauthenticated route and
 * this feature does not add a second. The import endpoint in particular is
 * authenticated even though it is driven by a pasted prompt: a batch import that
 * anyone could call is a content-injection endpoint.
 *
 * <p>{@code {deck}} is always the deck KEY, never its uuid, so the URLs read as
 * {@code /decks/indkoeb/cards}. An unknown key and an inactive deck are the same
 * plain 404: deactivating a deck should take it out of service completely rather
 * than leave clients to check a flag.
 */
public final class TinderController {

    /** Cards per page when the caller does not say. A swipe session is short. */
    static final int DEFAULT_CARD_LIMIT = 20;

    /**
     * Hard cap on {@code ?limit=}. A larger value is clamped rather than
     * rejected, because asking for "everything" is a reasonable thing for the
     * swipe app to do when it wants to preload a deck, and failing that request
     * would teach it to page in a loop instead, which is worse for the database.
     */
    static final int MAX_CARD_LIMIT = 100;

    /**
     * Hard cap on one import batch. A refill asks for 50; 200 leaves room for a
     * generous session without letting one request write an unbounded number of
     * rows inside a single transaction.
     */
    static final int MAX_IMPORT_BATCH = 200;

    static final int MAX_TEXT_LENGTH = 500;
    static final int MAX_SOURCE_LENGTH = 200;

    /**
     * Cap on one card's serialized metadata. The API deliberately does not
     * interpret metadata, so the only thing it can meaningfully police is size:
     * without this, "free-form JSON" is an invitation to store a payload.
     */
    static final int MAX_METADATA_LENGTH = 4000;

    private final Backend backend;

    public TinderController(Backend backend) {
        this.backend = backend;
    }

    // -- decks -----------------------------------------------------------------

    public void decks(Context ctx) {
        TinderService tinder = requireBackend();
        String uid = ctx.attribute(AuthFilter.UID_ATTRIBUTE);

        List<Map<String, Object>> out = new ArrayList<>();
        for (TinderDeckRow deck : tinder.activeDecks()) {
            out.add(deckView(tinder, deck, uid));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("decks", out);
        ctx.json(body);
    }

    /**
     * The next cards, together with the deck's status, so the swipe app gets the
     * count and the cards from ONE consistent read instead of two calls that can
     * disagree.
     */
    public void cards(Context ctx) {
        TinderService tinder = requireBackend();
        String uid = ctx.attribute(AuthFilter.UID_ATTRIBUTE);
        TinderDeckRow deck = requireDeck(tinder, ctx);

        int limit = readLimit(ctx);
        List<Map<String, Object>> cards = new ArrayList<>();
        for (TinderEntryRow entry : tinder.nextCards(deck, uid, limit)) {
            cards.add(TinderViews.card(entry));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deck", deckView(tinder, deck, uid));
        body.put("cards", cards);
        ctx.json(body);
    }

    // -- swipes ----------------------------------------------------------------

    public void swipe(Context ctx) {
        TinderService tinder = requireBackend();
        String uid = ctx.attribute(AuthFilter.UID_ATTRIBUTE);
        TinderDeckRow deck = requireDeck(tinder, ctx);
        Body body = Body.parse(ctx.body());

        if (!body.isString("entryId") || !body.isString("direction")) {
            throw HttpError.badBody();
        }
        String direction = body.asString("direction");
        if (!TinderSwipeRow.isDirection(direction)) {
            throw HttpError.badBody();
        }

        // Scoped to the deck in the path, so a card id from another deck is a
        // 404 rather than a swipe recorded against the wrong deck's rules.
        TinderEntryRow entry = tinder.findActiveEntryInDeck(deck.id(), body.asString("entryId"))
                .orElseThrow(HttpError::notFound);

        TinderService.SwipeResult result = tinder.recordSwipe(deck, entry, uid, direction);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("swipe", TinderViews.swipe(result.swipe()));
        out.put("created", result.created());
        out.put("item", TinderViews.createdItem(result.item()));
        out.put("match", result.match());
        ctx.json(out);
    }

    // -- matches ---------------------------------------------------------------

    public void matches(Context ctx) {
        TinderService tinder = requireBackend();

        List<Map<String, Object>> out = new ArrayList<>();
        for (TinderMatchRow m : tinder.matches()) {
            out.add(TinderViews.match(m));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("matches", out);
        ctx.json(body);
    }

    // -- import (issue #59) ----------------------------------------------------

    /**
     * Batch import for a deck refill.
     *
     * <p>Everything is validated BEFORE a single row is written, so a malformed
     * element is a clean 400 with the deck untouched rather than a half applied
     * batch or a 500 out of the driver. Duplicates are not a validation failure:
     * they are skipped by the database and counted, because a second refill of
     * the same deck legitimately overlaps with the first.
     */
    public void importEntries(Context ctx) {
        TinderService tinder = requireBackend();
        TinderDeckRow deck = requireDeck(tinder, ctx);
        Body body = Body.parse(ctx.body());

        if (!body.isArray("entries")) {
            throw HttpError.badBody();
        }
        JsonArray raw = body.asArray("entries");
        if (raw.isEmpty() || raw.size() > MAX_IMPORT_BATCH) {
            throw HttpError.badBody();
        }

        // A batch-level source ("claude-refill") labels every card in it, and a
        // per-entry source overrides it, so a mixed batch can still say where
        // each card came from.
        String batchSource = null;
        if (body.has("source") && !body.isNull("source")) {
            if (!body.isString("source")) {
                throw HttpError.badBody();
            }
            batchSource = readText(body.asString("source"), MAX_SOURCE_LENGTH);
        }

        List<TinderService.NewTinderEntry> entries = new ArrayList<>(raw.size());
        for (JsonElement element : raw) {
            entries.add(readEntry(element, batchSource));
        }

        TinderService.ImportResult result = tinder.importEntries(deck.id(), entries)
                .orElseThrow(HttpError::notFound);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("deck", deck.key());
        out.put("received", result.received());
        out.put("inserted", result.inserted());
        out.put("skipped", result.skipped());
        out.put("total", result.total());
        ctx.json(out);
    }

    // -- readers ---------------------------------------------------------------

    /** One batch element: {@code {text, metadata?, source?}}, or a 400. */
    private static TinderService.NewTinderEntry readEntry(JsonElement element, String batchSource) {
        if (element == null || !element.isJsonObject()) {
            throw HttpError.badBody();
        }
        JsonObject obj = element.getAsJsonObject();

        JsonElement textElement = obj.get("text");
        if (textElement == null || !textElement.isJsonPrimitive()
                || !textElement.getAsJsonPrimitive().isString()) {
            throw HttpError.badBody();
        }
        String text = readText(textElement.getAsString(), MAX_TEXT_LENGTH);
        if (text == null) {
            throw HttpError.badBody();
        }

        // Absent, null and {} all mean "no metadata". Anything that is not an
        // object (a string, a number, an array) is rejected rather than coerced:
        // the deck's metadata vocabulary is what the refill prompt advertises,
        // and silently storing a scalar there would put a card in the deck that
        // no client can read.
        String metadata = "{}";
        JsonElement metaElement = obj.get("metadata");
        if (metaElement != null && !metaElement.isJsonNull()) {
            if (!metaElement.isJsonObject()) {
                throw HttpError.badBody();
            }
            metadata = metaElement.toString();
            if (metadata.length() > MAX_METADATA_LENGTH) {
                throw HttpError.badBody();
            }
        }

        String source = batchSource;
        JsonElement sourceElement = obj.get("source");
        if (sourceElement != null && !sourceElement.isJsonNull()) {
            if (!sourceElement.isJsonPrimitive() || !sourceElement.getAsJsonPrimitive().isString()) {
                throw HttpError.badBody();
            }
            source = readText(sourceElement.getAsString(), MAX_SOURCE_LENGTH);
        }

        return new TinderService.NewTinderEntry(text, metadata, source);
    }

    /** Trimmed text (empty to null) up to max, else 400. */
    private static String readText(String value, int max) {
        if (value == null) {
            throw HttpError.badBody();
        }
        if (value.length() > max) {
            throw HttpError.badBody();
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * {@code ?limit=}: absent gives {@link #DEFAULT_CARD_LIMIT}, anything above
     * {@link #MAX_CARD_LIMIT} is clamped, and a non-numeric or non-positive
     * value is a 400 (a typo should be told, not silently reinterpreted).
     */
    private static int readLimit(Context ctx) {
        String raw = ctx.queryParam("limit");
        if (raw == null || raw.isBlank()) {
            return DEFAULT_CARD_LIMIT;
        }
        int limit;
        try {
            limit = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw HttpError.badBody();
        }
        if (limit < 1) {
            throw HttpError.badBody();
        }
        return Math.min(limit, MAX_CARD_LIMIT);
    }

    // -- helpers ---------------------------------------------------------------

    /**
     * The deck named in the path, or a 404. Resolving it up front on every route
     * is what keeps "unknown deck" and "inactive deck" indistinguishable.
     */
    private static TinderDeckRow requireDeck(TinderService tinder, Context ctx) {
        return tinder.findActiveDeckByKey(ctx.pathParam("deck")).orElseThrow(HttpError::notFound);
    }

    /**
     * A deck's status for one caller, with the refill prompt attached only when
     * the deck has actually drained for them. The metadata-key lookup behind the
     * prompt is a second query, so it is skipped entirely in the common case.
     */
    private Map<String, Object> deckView(TinderService tinder, TinderDeckRow deck, String uid) {
        TinderService.DeckCounts counts = tinder.counts(deck, uid);
        boolean needsRefill = TinderPrompts.needsRefill(deck.recycles(), counts.remaining());
        String prompt = null;
        if (needsRefill) {
            prompt = TinderPrompts.refill(
                    deck.key(),
                    deck.displayName(),
                    tinder.metadataKeys(deck.id()),
                    TinderPrompts.DEFAULT_REFILL_COUNT,
                    backend.config().publicBaseUrl());
        }
        return TinderViews.deck(deck, counts, needsRefill, prompt);
    }

    private TinderService requireBackend() {
        if (!backend.databaseConfigured()) {
            throw HttpError.backendNotConfigured();
        }
        return backend.tinder();
    }
}
