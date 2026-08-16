package dk.dtu.api.domain;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.Update;

/**
 * All {@code tinder_decks} / {@code tinder_entries} / {@code tinder_swipes}
 * database access (issue #56), plus the one place a right swipe turns into a
 * todo item.
 *
 * <p>Deliberately its own service rather than methods on {@link TodoService},
 * for exactly the reason {@link CountersService} and {@link SharesService} are:
 * {@code TodoService} mirrors the website's queries and is the hottest file in
 * the repo, so a feature that is not part of the todo model does not get to
 * grow inside it. TodoTinder is the strongest case yet, because it is a whole
 * resource family with its own read path, its own write path and its own
 * derived query.
 *
 * <p>What this service DOES borrow from {@code TodoService} is item creation:
 * {@link #recordSwipe} calls the existing public
 * {@link TodoService#insertItem(NewItem)}, so a swiped card becomes an ordinary
 * item and appears in the web edition for free, with no second code path that
 * could disagree about how an item is built. The dedupe probe next to it is
 * this service's own SQL against {@code items}, the same way
 * {@code SharesService.itemsForList} owns its read: a few duplicated lines are
 * cheaper than coupling a new feature to the file every other work stream
 * edits.
 *
 * <p>Follows the same fluent-{@code Handle}, raw-SQL, manual-row-mapper style
 * as {@link TodoService}, including reusing its package-private
 * {@link TodoService#isUuid(String)} for id validation.
 */
public final class TinderService {

    private final Jdbi jdbi;
    private final TodoService todo;

    public TinderService(Jdbi jdbi, TodoService todo) {
        this.jdbi = jdbi;
        this.todo = todo;
    }

    /**
     * How many cards a deck holds and how many are still to be seen by ONE
     * caller. For a recycling deck those are always equal, which is not a bug:
     * the grocery deck never runs out, by design.
     */
    public record DeckCounts(int total, int remaining) {
    }

    /** A validated card to import, already normalised by the controller. */
    public record NewTinderEntry(String text, String metadata, String source) {
    }

    /**
     * The outcome of one batch import (issue #59). {@code skipped} is
     * {@code received - inserted}, and it is a normal outcome rather than an
     * error: a mostly duplicate batch is exactly what a second refill of the
     * same deck looks like.
     */
    public record ImportResult(int received, int inserted, int skipped, int total) {
    }

    /**
     * The outcome of one swipe. {@code item} is null whenever nothing was
     * created, and {@code created} says whether that was because the swipe was
     * a left, because the deck has no target list, or because an open item with
     * that text was already there.
     */
    public record SwipeResult(TinderSwipeRow swipe, ItemRow item, boolean created, boolean match) {
    }

    // -- decks -----------------------------------------------------------------

    /** Every active deck, oldest first (the order the decks were configured in). */
    public List<TinderDeckRow> activeDecks() {
        return jdbi.withHandle(h -> h
                .createQuery("SELECT * FROM tinder_decks WHERE active ORDER BY created_at ASC, key ASC")
                .map((rs, ctx) -> mapDeck(rs))
                .list());
    }

    /**
     * Resolves the public deck handle. Every route takes the deck KEY, never a
     * uuid, and an inactive deck is indistinguishable from an unknown one on
     * purpose: both are a plain 404, so deactivating a deck is a complete way to
     * take it out of service rather than something the client has to check.
     */
    public Optional<TinderDeckRow> findActiveDeckByKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return jdbi.withHandle(h -> h
                .createQuery("SELECT * FROM tinder_decks WHERE key = :key AND active LIMIT 1")
                .bind("key", key)
                .map((rs, ctx) -> mapDeck(rs))
                .findFirst());
    }

    /**
     * Card counts for one deck and one caller, in a single statement so the two
     * numbers can never be read from different states of the table.
     *
     * <p>The mode difference is the {@code recycle OR ...} disjunction: for a
     * recycling deck the caller's swipe history is not consulted at all, so
     * remaining collapses to total.
     */
    public DeckCounts counts(TinderDeckRow deck, String userId) {
        if (deck == null) {
            return new DeckCounts(0, 0);
        }
        return jdbi.withHandle(h -> h
                .createQuery("""
                        SELECT COUNT(*) AS total_count,
                               COUNT(*) FILTER (WHERE CAST(:recycle AS boolean) OR NOT EXISTS (
                                   SELECT 1 FROM tinder_swipes s
                                   WHERE s.entry_id = e.id AND s.user_id = CAST(:userId AS uuid)
                               )) AS remaining_count
                        FROM tinder_entries e
                        WHERE e.deck_id = CAST(:deckId AS uuid) AND e.active
                        """)
                .bind("deckId", deck.id())
                .bind("recycle", deck.recycles())
                .bind("userId", TodoService.isUuid(userId) ? userId : null)
                .map((rs, ctx) -> new DeckCounts(rs.getInt("total_count"), rs.getInt("remaining_count")))
                .one());
    }

    /**
     * The next cards for one caller.
     *
     * <p>This is where depletion and recycling actually differ. An idea deck
     * filters out every entry the caller has ALREADY swiped, in either
     * direction, so a card seen once never comes back for that person while
     * still being offered to the other one (the filter is per user, so one
     * person emptying a deck does not empty it for anybody else). The grocery
     * deck applies no filter at all: a left swipe there means "not this run",
     * with no cooldown, so every card is offered again next time.
     *
     * <p>The order is deterministic ({@code created_at}, then id as a
     * tiebreak), not {@code ORDER BY random()}. Shuffling is a presentation
     * choice that belongs to the swipe app (#58); doing it here would make the
     * endpoint untestable and would make "the next cards" mean something
     * different on every call.
     */
    public List<TinderEntryRow> nextCards(TinderDeckRow deck, String userId, int limit) {
        if (deck == null || limit <= 0) {
            return List.of();
        }
        return jdbi.withHandle(h -> h
                .createQuery("""
                        SELECT e.* FROM tinder_entries e
                        WHERE e.deck_id = CAST(:deckId AS uuid)
                          AND e.active
                          AND (CAST(:recycle AS boolean) OR NOT EXISTS (
                                SELECT 1 FROM tinder_swipes s
                                WHERE s.entry_id = e.id AND s.user_id = CAST(:userId AS uuid)))
                        ORDER BY e.created_at ASC, e.id ASC
                        LIMIT :limit
                        """)
                .bind("deckId", deck.id())
                .bind("recycle", deck.recycles())
                .bind("userId", TodoService.isUuid(userId) ? userId : null)
                .bind("limit", limit)
                .map((rs, ctx) -> mapEntry(rs))
                .list());
    }

    /**
     * The distinct metadata keys a deck's cards actually use, alphabetically.
     *
     * <p>Derived rather than declared, because the metadata schema is set by the
     * dataset (#57) and differs per deck. The refill prompt (#59) names these
     * keys so a generated batch matches the cards already there instead of
     * inventing a parallel vocabulary. An empty deck legitimately answers an
     * empty list, and the prompt says so rather than guessing.
     */
    public List<String> metadataKeys(String deckId) {
        if (!TodoService.isUuid(deckId)) {
            return List.of();
        }
        return jdbi.withHandle(h -> h
                .createQuery("""
                        SELECT DISTINCT k AS metadata_key
                        FROM tinder_entries e, LATERAL jsonb_object_keys(e.metadata) AS k
                        WHERE e.deck_id = CAST(:deckId AS uuid)
                          AND e.active
                          AND jsonb_typeof(e.metadata) = 'object'
                        ORDER BY metadata_key ASC
                        """)
                .bind("deckId", deckId)
                .mapTo(String.class)
                .list());
    }

    // -- entries ---------------------------------------------------------------

    /**
     * One active card, scoped to its deck. Scoping the lookup by {@code deck_id}
     * as well as by id is deliberate, the same way
     * {@code SharesService.revoke} scopes by list: it makes a mismatched
     * (deck, entry) pair impossible to act on, so a swipe cannot be recorded
     * against a card from another deck by guessing its id.
     */
    public Optional<TinderEntryRow> findActiveEntryInDeck(String deckId, String entryId) {
        if (!TodoService.isUuid(deckId) || !TodoService.isUuid(entryId)) {
            return Optional.empty();
        }
        return jdbi.withHandle(h -> h
                .createQuery("SELECT * FROM tinder_entries WHERE id = CAST(:entryId AS uuid) "
                        + "AND deck_id = CAST(:deckId AS uuid) AND active LIMIT 1")
                .bind("entryId", entryId)
                .bind("deckId", deckId)
                .map((rs, ctx) -> mapEntry(rs))
                .findFirst());
    }

    /**
     * Imports a batch of cards into a deck (issue #59), inserting only the ones
     * that are new.
     *
     * <p>The dedupe is {@code ON CONFLICT (deck_id, text) DO NOTHING} against
     * the unique index from V8, NOT a read-then-write. That is what makes a
     * partially duplicate batch insert its new rows instead of failing whole,
     * and it stays correct when two refills of the same deck race, which a
     * "select what exists, then insert the rest" version would not.
     *
     * <p>The whole batch runs in ONE transaction, so a failure halfway through
     * leaves the deck exactly as it was rather than half refilled.
     */
    public Optional<ImportResult> importEntries(String deckId, List<NewTinderEntry> entries) {
        if (!TodoService.isUuid(deckId)) {
            return Optional.empty();
        }
        List<NewTinderEntry> batch = entries == null ? List.of() : entries;

        return Optional.of(jdbi.inTransaction(h -> {
            int inserted = 0;
            for (NewTinderEntry e : batch) {
                Update u = h.createUpdate(
                        "INSERT INTO tinder_entries (deck_id, text, metadata, source) "
                        + "VALUES (CAST(:deckId AS uuid), :text, CAST(:metadata AS jsonb), :source) "
                        + "ON CONFLICT (deck_id, text) DO NOTHING");
                u.bind("deckId", deckId);
                u.bind("text", e.text());
                u.bind("metadata", e.metadata() == null ? "{}" : e.metadata());
                bindNullable(u, "source", e.source(), Types.VARCHAR);
                inserted += u.execute();
            }
            int total = h.createQuery("SELECT COUNT(*) FROM tinder_entries "
                            + "WHERE deck_id = CAST(:deckId AS uuid) AND active")
                    .bind("deckId", deckId)
                    .mapTo(Integer.class)
                    .one();
            return new ImportResult(batch.size(), inserted, batch.size() - inserted, total);
        }));
    }

    // -- swipes ----------------------------------------------------------------

    /**
     * Records one swipe and, for a right swipe, syncs it to the deck's target
     * list.
     *
     * <p>The write is an UPSERT, never a plain insert. The recycling grocery
     * deck offers the same card every week, so the same (user, entry) pair
     * arrives over and over and a plain insert would throw on the unique index
     * the second time. See the long comment in {@code V8__tinder.sql} for why
     * the index cannot be made partial on the deck's mode instead. The
     * consequence is that the row is the LATEST swipe, moved forward in time,
     * not one row per swipe.
     *
     * <p>A right swipe then creates an item through
     * {@link TodoService#insertItem(NewItem)}, guarded by
     * {@link #hasOpenItemWithText}. Three ways nothing is created, all of them
     * ordinary rather than errors: the swipe was a left, the deck has no target
     * list (the column is {@code ON DELETE SET NULL}, so this is reachable in
     * production), or the list already has an OPEN item with that text. The last
     * one is the case that matters: the grocery deck re-offers milk every week
     * and must not stack up five "Mælk" items, while a milk item that was ticked
     * off (status DONE) correctly lets a new one be created.
     */
    public SwipeResult recordSwipe(TinderDeckRow deck, TinderEntryRow entry, String userId,
                                   String direction) {
        TinderSwipeRow swipe = jdbi.withHandle(h -> h
                .createUpdate("""
                        INSERT INTO tinder_swipes (user_id, entry_id, direction)
                        VALUES (CAST(:userId AS uuid), CAST(:entryId AS uuid), :direction)
                        ON CONFLICT (user_id, entry_id)
                        DO UPDATE SET direction = EXCLUDED.direction, created_at = now()
                        RETURNING *
                        """)
                .bind("userId", userId)
                .bind("entryId", entry.id())
                .bind("direction", direction)
                .executeAndReturnGeneratedKeys()
                .map((rs, ctx) -> mapSwipe(rs))
                .one());

        ItemRow item = null;
        boolean created = false;
        if (TinderSwipeRow.DIRECTION_RIGHT.equals(direction)
                && deck.targetListId() != null
                && !hasOpenItemWithText(deck.targetListId(), entry.text())) {
            item = todo.insertItem(new NewItem(
                    deck.targetListId(),
                    entry.text(),
                    null,
                    "NOT_STARTED",
                    null,
                    null,
                    null,
                    null,
                    userId));
            created = true;
        }

        return new SwipeResult(swipe, item, created, isMatch(entry.id()));
    }

    /**
     * True when the list already holds an OPEN item (status not {@code DONE})
     * whose text is the same card.
     *
     * <p>Compared case-insensitively and trimmed, because the two sides come
     * from different places: the card text is curated in a dataset and the item
     * text may have been typed by hand or edited in the web edition. "Mælk" and
     * "mælk " are the same shopping-list line to a human, and treating them as
     * different is exactly the duplicate this guard exists to prevent.
     *
     * <p>"Not DONE" is the shared overdue/open rule from CLAUDE.md, so a ticked
     * off item deliberately does NOT suppress a new one: that is what makes the
     * grocery deck usable week after week.
     */
    public boolean hasOpenItemWithText(String listId, String text) {
        if (!TodoService.isUuid(listId) || text == null) {
            return false;
        }
        return jdbi.withHandle(h -> h
                .createQuery("SELECT 1 FROM items i WHERE i.list_id = CAST(:listId AS uuid) "
                        + "AND i.status <> 'DONE' "
                        + "AND lower(btrim(i.text)) = lower(btrim(:text)) LIMIT 1")
                .bind("listId", listId)
                .bind("text", text)
                .mapTo(Integer.class)
                .findFirst()
                .isPresent());
    }

    // -- matches ---------------------------------------------------------------

    /**
     * Every card at least TWO different people have swiped right on, newest
     * match first.
     *
     * <p>A query, never a stored flag: see {@link TinderMatchRow} for why.
     *
     * <p><b>The threshold is two, deliberately, and NOT "every row in
     * {@code users}".</b> The all-users form looks more general and is in fact
     * the fragile one. {@code users} is shared with the website and
     * {@code SeedUser} can add a row to it for reasons that have nothing to do
     * with this app, and the moment a third row exists every established match
     * silently disappears until that third person also swipes right on the same
     * card. There is no error, no log line, just an empty Matches screen. A
     * quorum of two cannot fail that way, and for the two account holders this
     * is built for the two rules are identical anyway.
     *
     * <p>Two is also the smallest number that means anything: with a threshold
     * of one, every right swipe would instantly be a match with yourself.
     * {@link #isMatch(String)} applies the same rule in one statement, so the
     * flag returned with a swipe and the Matches list a moment later cannot
     * disagree.
     *
     * <p>Every selected column is explicitly aliased. Three tables are joined
     * here and all three have {@code id} and {@code created_at}; a bare
     * {@code getString("id")} over that join reads whichever one the driver saw
     * first, which has silently produced the wrong id in this repo before.
     */
    public List<TinderMatchRow> matches() {
        return jdbi.withHandle(h -> h
                .createQuery("""
                        SELECT e.id             AS entry_id,
                               e.text           AS entry_text,
                               e.metadata       AS entry_metadata,
                               d.key            AS deck_key,
                               d.display_name   AS deck_display_name,
                               MAX(s.created_at) AS matched_at
                        FROM tinder_entries e
                        JOIN tinder_decks d ON d.id = e.deck_id
                        JOIN tinder_swipes s ON s.entry_id = e.id AND s.direction = 'right'
                        WHERE e.active AND d.active
                        GROUP BY e.id, d.id
                        HAVING COUNT(DISTINCT s.user_id) >= 2
                        ORDER BY MAX(s.created_at) DESC, e.id ASC
                        """)
                .map((rs, ctx) -> new TinderMatchRow(
                        rs.getString("entry_id"),
                        rs.getString("deck_key"),
                        rs.getString("deck_display_name"),
                        rs.getString("entry_text"),
                        rs.getString("entry_metadata"),
                        instant(rs.getTimestamp("matched_at"))))
                .list());
    }

    /**
     * Whether one card is currently a match. Same two-person quorum as
     * {@link #matches()}, in one statement so the flag returned with a swipe
     * agrees with the Matches list a moment later.
     */
    public boolean isMatch(String entryId) {
        if (!TodoService.isUuid(entryId)) {
            return false;
        }
        return jdbi.withHandle(h -> h
                .createQuery("""
                        SELECT (SELECT COUNT(DISTINCT s.user_id) FROM tinder_swipes s
                                WHERE s.entry_id = CAST(:entryId AS uuid)
                                  AND s.direction = 'right') >= 2 AS is_match
                        """)
                .bind("entryId", entryId)
                .mapTo(Boolean.class)
                .one());
    }

    // -- row mappers -----------------------------------------------------------

    private static void bindNullable(Update u, String name, Object value, int sqlType) {
        if (value == null) {
            u.bindNull(name, sqlType);
        } else {
            u.bind(name, value);
        }
    }

    private TinderDeckRow mapDeck(ResultSet rs) throws SQLException {
        return new TinderDeckRow(
                rs.getString("id"),
                rs.getString("key"),
                rs.getString("display_name"),
                rs.getString("target_list_id"),
                rs.getString("recycle_mode"),
                rs.getString("dataset_key"),
                rs.getBoolean("active"),
                instant(rs.getTimestamp("created_at")));
    }

    private TinderEntryRow mapEntry(ResultSet rs) throws SQLException {
        return new TinderEntryRow(
                rs.getString("id"),
                rs.getString("deck_id"),
                rs.getString("text"),
                rs.getString("metadata"),
                rs.getString("source"),
                rs.getBoolean("active"),
                instant(rs.getTimestamp("created_at")));
    }

    private TinderSwipeRow mapSwipe(ResultSet rs) throws SQLException {
        return new TinderSwipeRow(
                rs.getString("id"),
                rs.getString("user_id"),
                rs.getString("entry_id"),
                rs.getString("direction"),
                instant(rs.getTimestamp("created_at")));
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
