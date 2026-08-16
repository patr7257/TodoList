package dk.dtu.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Types;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import dk.dtu.api.auth.Token;
import dk.dtu.api.db.Migrations;
import dk.dtu.api.domain.TinderDeckRow;
import dk.dtu.api.domain.TinderService;
import dk.dtu.api.domain.TodoService;
import dk.dtu.api.web.ApiServer;
import dk.dtu.api.web.Backend;
import dk.dtu.api.web.RateLimiter;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.javalin.Javalin;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.Update;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * End-to-end HTTP tests for the TodoTinder routes (issues #56 and #59), against
 * a real (embedded) Postgres and a real Javalin app on an OS-assigned ephemeral
 * port, following {@code CountersIntegrationTest}.
 *
 * <p>Almost every test builds its OWN deck with a unique key, so the behavioural
 * tests do not depend on each other's leftovers and could run in any order. The
 * ORDER annotations exist so the match tests can walk the population up one
 * account at a time: one user, then two, then three. The rule under test is a
 * quorum of TWO distinct right swipes, so the third account is there to prove a
 * property rather than to satisfy the rule: adding a person must not disturb a
 * match that two people already made. An "every row in {@code users}" rule
 * would fail that, silently, which is exactly why it is not the rule.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TinderIntegrationTest {

    private static final String SECRET = "tinder-integration-secret";
    private static final String PUBLIC_BASE = "https://tinder-test.example";

    private EmbeddedPostgres pg;
    private Jdbi jdbi;
    private TodoService todo;
    private TinderService tinder;
    private Token token;
    private Javalin app;
    private String baseUrl;

    private String userA;
    private String tokenA;
    private String userB;
    private String tokenB;

    private String ideasListId;
    private String groceriesListId;

    /** Carried from the single-user match test into the two-user one. */
    private String matchDeckKey;
    private String matchEntryId;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeAll
    void startServer() throws IOException {
        pg = EmbeddedPostgres.builder().start();
        DataSource ds = pg.getPostgresDatabase();
        Migrations.migrate(ds);

        jdbi = Jdbi.create(ds);
        todo = new TodoService(jdbi);
        tinder = new TinderService(jdbi, todo);
        token = new Token(SECRET);
        Backend backend = new Backend(
                ApiConfig.of(0, null, SECRET,
                        ApiConfig.DEFAULT_SHARE_BASE_URL, 60, 60, PUBLIC_BASE),
                todo, token, null, null, null, tinder);

        app = ApiServer.create(backend);
        app.start(0);
        baseUrl = "http://localhost:" + app.port() + "/api/todo/tinder";

        // Only ONE user to start with: @Order(10) proves a lone user cannot
        // match with themselves, and creates the second one on its way out.
        userA = createUser("tinder-a@example.com", "Tinder A");
        tokenA = token.issue(userA);

        ideasListId = todo.insertList("Aktiviteter").id();
        groceriesListId = todo.insertList("Indkøb").id();
    }

    @AfterAll
    void stopServer() throws IOException {
        if (app != null) {
            app.stop();
        }
        if (pg != null) {
            pg.close();
        }
    }

    // -- V8 schema -------------------------------------------------------------

    @Test
    @Order(1)
    void v8CreatedTheThreeTablesWithTheExpectedColumns() {
        assertColumn("tinder_decks", "id", "uuid", "NO");
        assertColumn("tinder_decks", "key", "text", "NO");
        assertColumn("tinder_decks", "display_name", "text", "NO");
        assertColumn("tinder_decks", "target_list_id", "uuid", "YES");
        assertColumn("tinder_decks", "recycle_mode", "text", "NO");
        assertColumn("tinder_decks", "dataset_key", "text", "YES");
        assertColumn("tinder_decks", "active", "boolean", "NO");
        assertColumn("tinder_decks", "created_at", "timestamp without time zone", "NO");

        assertColumn("tinder_entries", "id", "uuid", "NO");
        assertColumn("tinder_entries", "deck_id", "uuid", "NO");
        assertColumn("tinder_entries", "text", "text", "NO");
        assertColumn("tinder_entries", "metadata", "jsonb", "NO");
        assertColumn("tinder_entries", "source", "text", "YES");
        assertColumn("tinder_entries", "active", "boolean", "NO");
        assertColumn("tinder_entries", "created_at", "timestamp without time zone", "NO");

        assertColumn("tinder_swipes", "id", "uuid", "NO");
        assertColumn("tinder_swipes", "user_id", "uuid", "NO");
        assertColumn("tinder_swipes", "entry_id", "uuid", "NO");
        assertColumn("tinder_swipes", "direction", "text", "NO");
        assertColumn("tinder_swipes", "created_at", "timestamp without time zone", "NO");
    }

    @Test
    @Order(2)
    void v8CreatedTheUniqueIndexesTheDedupeAndTheUpsertDependOn() {
        // The import's ON CONFLICT (deck_id, text) and the swipe's ON CONFLICT
        // (user_id, entry_id) are not merely nice to have: without exactly these
        // unique indexes both statements fail at runtime rather than deduping.
        assertUniqueIndex("tinder_entries", "tinder_entries_deck_text_key", "deck_id", "text");
        assertUniqueIndex("tinder_swipes", "tinder_swipes_user_entry_key", "user_id", "entry_id");
        assertUniqueIndex("tinder_decks", "tinder_decks_key_key", "key");
    }

    @Test
    @Order(3)
    void reRunningTheMigrationIsANoOp() {
        // Idempotence is the whole safety story for a migration against live
        // Neon, so it is proven rather than assumed.
        Migrations.migrate(pg.getPostgresDatabase());
        assertColumn("tinder_decks", "key", "text", "NO");
    }

    // -- auth ------------------------------------------------------------------

    @Test
    @Order(4)
    void everyTinderRouteRequiresABearerToken() throws Exception {
        // The API has exactly ONE unauthenticated route (the public share
        // reader) and this feature does not add a second. Proven by an actual
        // 401 on every route, including the import endpoint, which is driven by
        // a pasted prompt and would otherwise be an open write endpoint.
        assertEquals(401, call("GET", "/decks", null, null).statusCode());
        assertEquals(401, call("GET", "/decks/anything/cards", null, null).statusCode());
        assertEquals(401, call("POST", "/decks/anything/swipes",
                "{\"entryId\":\"x\",\"direction\":\"right\"}", null).statusCode());
        assertEquals(401, call("POST", "/decks/anything/entries",
                "{\"entries\":[{\"text\":\"x\"}]}", null).statusCode());
        assertEquals(401, call("GET", "/matches", null, null).statusCode());
    }

    @Test
    @Order(5)
    void anUnknownOrInactiveDeckIsAPlain404() throws Exception {
        assertEquals(404, call("GET", "/decks/no-such-deck/cards", null, tokenA).statusCode());

        String key = createDeck("sovende-daek", "Sovende", TinderDeckRow.MODE_DEPLETE, null, false);
        assertEquals(404, call("GET", "/decks/" + key + "/cards", null, tokenA).statusCode(),
                "an inactive deck must be indistinguishable from an unknown one");
        assertEquals(404, call("POST", "/decks/" + key + "/entries",
                "{\"entries\":[{\"text\":\"x\"}]}", tokenA).statusCode());

        // And it is not listed either.
        JsonArray decks = decks(tokenA);
        assertFalse(deckKeys(decks).contains(key), "an inactive deck must not be listed");
    }

    // -- matches (these two decide how many users exist, see the class javadoc) -

    @Test
    @Order(10)
    void oneUserAloneNeverMatchesWithThemselves() throws Exception {
        matchDeckKey = createDeck("match-deck", "Match Deck", TinderDeckRow.MODE_DEPLETE, null, true);
        matchEntryId = addEntry(matchDeckKey, "Weekend i Berlin", "{}");

        JsonObject swipe = jsonOf(swipeRight(matchDeckKey, matchEntryId, tokenA));
        assertFalse(swipe.get("match").getAsBoolean(),
                "with a single user in the database, a right swipe must not be a match");
        assertEquals(0, matches(tokenA).size(),
                "a lone user swiping right must not match with themselves");

        // From here on the database has the production shape: two accounts.
        userB = createUser("tinder-b@example.com", "Tinder B");
        tokenB = token.issue(userB);
    }

    @Test
    @Order(11)
    void aMatchAppearsOnlyOnceBothUsersHaveSwipedRight() throws Exception {
        // Still only A has swiped it, but now there are two users.
        assertEquals(0, matches(tokenA).size(),
                "one right swipe out of two users is not a match");
        assertFalse(tinder.isMatch(matchEntryId));

        JsonObject swipe = jsonOf(swipeRight(matchDeckKey, matchEntryId, tokenB));
        assertTrue(swipe.get("match").getAsBoolean(),
                "the swipe that completes the pair must report the match");

        JsonArray found = matches(tokenA);
        assertEquals(1, found.size(), "expected exactly one match, got " + found);
        JsonObject m = found.get(0).getAsJsonObject();
        assertEquals(matchEntryId, m.get("entryId").getAsString());
        assertEquals(matchDeckKey, m.get("deckKey").getAsString());
        assertEquals("Weekend i Berlin", m.get("text").getAsString());
        assertNotNull(m.get("matchedAt").getAsString());

        // The Matches view is the same query for both callers: it is a property
        // of the swipes, not of who is asking.
        assertEquals(1, matches(tokenB).size());

        // A left swipe from one side takes the match away again, which is the
        // point of deriving it rather than storing a flag.
        assertEquals(200, call("POST", "/decks/" + matchDeckKey + "/swipes",
                swipeBody(matchEntryId, "left"), tokenB).statusCode());
        assertEquals(0, matches(tokenA).size(),
                "a match must disappear when one of its right swipes does");

        // Put it back, so the next test starts from a real, established match.
        swipeRight(matchDeckKey, matchEntryId, tokenB);
        assertEquals(1, matches(tokenA).size());
    }

    /**
     * The regression that decided the quorum rule. {@code users} is shared with
     * the website and {@code SeedUser} can add a row to it for reasons that
     * have nothing to do with this app. Under an "every row in users has swiped
     * right" rule, that row alone would wipe every established match with no
     * error and no log line, until the newcomer swiped right on each card too.
     * A quorum of two cannot fail that way.
     */
    @Test
    @Order(12)
    void addingAThirdAccountDoesNotDisturbAMatchTwoPeopleAlreadyMade() throws Exception {
        assertEquals(1, matches(tokenA).size(), "precondition: A and B have matched this card");

        String userC = createUser("tinder-c@example.com", "Tinder C");
        String tokenC = token.issue(userC);

        assertEquals(1, matches(tokenA).size(),
                "a third account must not silently unmake an existing match");
        assertEquals(1, matches(tokenC).size(),
                "and the newcomer sees the same match: it is a property of the swipes");
        assertTrue(tinder.isMatch(matchEntryId),
                "the per-swipe flag must agree with the list after the population changes");
    }

    // -- depletion vs recycling ------------------------------------------------

    @Test
    @Order(20)
    void anIdeaDeckDepletesForTheSwiperAndStaysFullForTheOtherUser() throws Exception {
        String key = createDeck("deplete-deck", "Idéer", TinderDeckRow.MODE_DEPLETE, null, true);
        String first = addEntry(key, "Kajak på Furesøen", "{}");
        String second = addEntry(key, "Loppemarked i Valby", "{}");

        assertEquals(List.of(first, second), cardIds(key, tokenA));
        assertEquals(2, deckOf(key, tokenA).get("remaining").getAsInt());

        // A left swipe retires the card for this user, permanently.
        assertEquals(200, call("POST", "/decks/" + key + "/swipes",
                swipeBody(first, "left"), tokenA).statusCode());
        assertEquals(List.of(second), cardIds(key, tokenA));

        // So does a right swipe: on an idea deck, swiped is swiped.
        swipeRight(key, second, tokenA);
        assertEquals(List.of(), cardIds(key, tokenA));
        assertEquals(0, deckOf(key, tokenA).get("remaining").getAsInt());
        assertEquals(2, deckOf(key, tokenA).get("total").getAsInt(),
                "the deck still HOLDS both cards; only what is left for this caller changed");

        // The filter is per user, so one person emptying a deck must not empty
        // it for the other one.
        assertEquals(List.of(first, second), cardIds(key, tokenB));
        assertEquals(2, deckOf(key, tokenB).get("remaining").getAsInt());
    }

    @Test
    @Order(21)
    void theGroceryDeckRecyclesSoALeftSwipeOnlySkipsThisRun() throws Exception {
        String key = createDeck("recycle-deck", "SwoppingSwiper", TinderDeckRow.MODE_RECYCLE, null, true);
        String milk = addEntry(key, "Mælk", "{}");
        String bread = addEntry(key, "Rugbrød", "{}");

        assertEquals(List.of(milk, bread), cardIds(key, tokenA));

        assertEquals(200, call("POST", "/decks/" + key + "/swipes",
                swipeBody(milk, "left"), tokenA).statusCode());
        assertEquals(List.of(milk, bread), cardIds(key, tokenA),
                "a left swipe on the grocery deck means 'not this run', with no cooldown");

        // A right swipe does not retire the card either: the same milk question
        // every week is the entire point of that deck.
        swipeRight(key, milk, tokenA);
        assertEquals(List.of(milk, bread), cardIds(key, tokenA));
        assertEquals(2, deckOf(key, tokenA).get("remaining").getAsInt(),
                "a recycling deck never runs out, so remaining always equals total");

        // Swiping the same card over and over must keep exactly ONE swipe row:
        // the upsert is what stops this table growing without bound.
        assertEquals(1, swipeRowCount(userA, milk));
    }

    // -- right swipe creates the item, exactly once ----------------------------

    @Test
    @Order(30)
    void aRightSwipeCreatesAnOrdinaryItemInTheDecksTargetList() throws Exception {
        String key = createDeck("sync-deck", "Aktiviteter", TinderDeckRow.MODE_DEPLETE,
                ideasListId, true);
        String entry = addEntry(key, "Tur i Dyrehaven", "{}");

        JsonObject res = jsonOf(swipeRight(key, entry, tokenA));
        assertTrue(res.get("created").getAsBoolean());
        JsonObject item = res.getAsJsonObject("item");
        assertEquals("Tur i Dyrehaven", item.get("text").getAsString());
        assertEquals(ideasListId, item.get("listId").getAsString());
        assertEquals("NOT_STARTED", item.get("status").getAsString());

        assertEquals(1, openItemCount(ideasListId, "Tur i Dyrehaven"));
    }

    @Test
    @Order(31)
    void aDeckWithNoTargetListRecordsTheSwipeAndCreatesNothing() throws Exception {
        // Reachable in production: target_list_id is ON DELETE SET NULL, so
        // deleting the list must not break the deck.
        String key = createDeck("orphan-deck", "Hjemløs", TinderDeckRow.MODE_DEPLETE, null, true);
        String entry = addEntry(key, "Ingen liste at lande i", "{}");

        JsonObject res = jsonOf(swipeRight(key, entry, tokenA));
        assertFalse(res.get("created").getAsBoolean());
        assertTrue(res.get("item").isJsonNull());
        assertEquals("right", res.getAsJsonObject("swipe").get("direction").getAsString(),
                "the swipe itself must still be recorded");
    }

    @Test
    @Order(32)
    void swipingRightTwiceAndOnAnAlreadyOpenItemCreatesExactlyOneItem() throws Exception {
        String key = createDeck("dedupe-deck", "Indkøb", TinderDeckRow.MODE_RECYCLE,
                groceriesListId, true);
        String milk = addEntry(key, "Mælk", "{}");

        assertTrue(jsonOf(swipeRight(key, milk, tokenA)).get("created").getAsBoolean());
        assertEquals(1, openItemCount(groceriesListId, "Mælk"));

        // Same user, same card, next week: the grocery deck offers it again.
        JsonObject again = jsonOf(swipeRight(key, milk, tokenA));
        assertFalse(again.get("created").getAsBoolean(), "a second right swipe must not duplicate");
        assertTrue(again.get("item").isJsonNull());
        assertEquals(1, openItemCount(groceriesListId, "Mælk"));

        // The other user liking the same card must not duplicate it either.
        assertFalse(jsonOf(swipeRight(key, milk, tokenB)).get("created").getAsBoolean());
        assertEquals(1, openItemCount(groceriesListId, "Mælk"));

        // Text comparison is trimmed and case-insensitive, because the two sides
        // come from different places: a curated dataset and a hand-typed item.
        String shoutedMilk = addEntry(key, "  MÆLK ", "{}");
        assertFalse(jsonOf(swipeRight(key, shoutedMilk, tokenA)).get("created").getAsBoolean(),
                "'  MÆLK ' and 'Mælk' are the same shopping-list line");
        assertEquals(1, openItemCount(groceriesListId, "Mælk"));

        // Ticking the item off must let the next run create a fresh one: that is
        // what makes the grocery deck usable week after week.
        markEverythingDone(groceriesListId);
        assertEquals(0, openItemCount(groceriesListId, "Mælk"));
        assertTrue(jsonOf(swipeRight(key, milk, tokenA)).get("created").getAsBoolean(),
                "a DONE item must not suppress a new one");
        assertEquals(1, openItemCount(groceriesListId, "Mælk"));
    }

    @Test
    @Order(33)
    void aSwipeIsRejectedForABadDirectionAnAlienCardOrAMissingField() throws Exception {
        String key = createDeck("validate-deck", "Validering", TinderDeckRow.MODE_DEPLETE, null, true);
        String entry = addEntry(key, "Et kort", "{}");
        String otherKey = createDeck("validate-other", "Anden", TinderDeckRow.MODE_DEPLETE, null, true);

        assertEquals(400, call("POST", "/decks/" + key + "/swipes",
                swipeBody(entry, "up"), tokenA).statusCode());
        assertEquals(400, call("POST", "/decks/" + key + "/swipes",
                swipeBody(entry, "RIGHT"), tokenA).statusCode());
        assertEquals(400, call("POST", "/decks/" + key + "/swipes",
                "{\"direction\":\"right\"}", tokenA).statusCode());
        assertEquals(404, call("POST", "/decks/" + key + "/swipes",
                swipeBody("00000000-0000-0000-0000-000000000000", "right"), tokenA).statusCode());
        assertEquals(404, call("POST", "/decks/" + key + "/swipes",
                swipeBody("not-a-uuid", "right"), tokenA).statusCode());
        assertEquals(404, call("POST", "/decks/" + otherKey + "/swipes",
                swipeBody(entry, "right"), tokenA).statusCode(),
                "a card from another deck must not be swipeable through this one");
    }

    // -- cards: the limit ------------------------------------------------------

    @Test
    @Order(34)
    void theCardLimitDefaultsIsClampedAndRejectsNonsense() throws Exception {
        String key = createDeck("limit-deck", "Limit", TinderDeckRow.MODE_RECYCLE, null, true);
        for (int i = 0; i < 25; i++) {
            addEntry(key, "kort " + i, "{}");
        }

        assertEquals(20, cardIds(key, tokenA).size(), "the default page is 20 cards");
        assertEquals(3, cardIdsWithLimit(key, tokenA, "3").size());
        assertEquals(25, cardIdsWithLimit(key, tokenA, "999").size(),
                "an oversized limit is clamped to the maximum, not rejected");
        assertEquals(400, call("GET", "/decks/" + key + "/cards?limit=0", null, tokenA).statusCode());
        assertEquals(400, call("GET", "/decks/" + key + "/cards?limit=-5", null, tokenA).statusCode());
        assertEquals(400, call("GET", "/decks/" + key + "/cards?limit=lots", null, tokenA).statusCode());
    }

    // -- import (issue #59) ----------------------------------------------------

    @Test
    @Order(40)
    void importInsertsOnlyTheNewEntriesOfAPartiallyDuplicateBatch() throws Exception {
        String key = createDeck("import-deck", "Import", TinderDeckRow.MODE_DEPLETE, null, true);

        JsonObject first = jsonOf(call("POST", "/decks/" + key + "/entries",
                "{\"source\":\"claude-refill\",\"entries\":["
                        + "{\"text\":\"Kanotur\",\"metadata\":{\"kategori\":\"ude\"}},"
                        + "{\"text\":\"Brætspilscafé\",\"metadata\":{\"kategori\":\"inde\"}}]}",
                tokenA));
        assertEquals(key, first.get("deck").getAsString());
        assertEquals(2, first.get("received").getAsInt());
        assertEquals(2, first.get("inserted").getAsInt());
        assertEquals(0, first.get("skipped").getAsInt());
        assertEquals(2, first.get("total").getAsInt());

        // The refill that overlaps with the last one: insert the new row, skip
        // the repeat, and above all do NOT fail the whole request.
        JsonObject second = jsonOf(call("POST", "/decks/" + key + "/entries",
                "{\"entries\":[{\"text\":\"Kanotur\"},{\"text\":\"Svømmehal\"}]}", tokenA));
        assertEquals(2, second.get("received").getAsInt());
        assertEquals(1, second.get("inserted").getAsInt());
        assertEquals(1, second.get("skipped").getAsInt());
        assertEquals(3, second.get("total").getAsInt());

        // A batch that is entirely duplicates is a normal, successful no-op.
        JsonObject third = jsonOf(call("POST", "/decks/" + key + "/entries",
                "{\"entries\":[{\"text\":\"Kanotur\"}]}", tokenA));
        assertEquals(0, third.get("inserted").getAsInt());
        assertEquals(1, third.get("skipped").getAsInt());

        // Duplicates INSIDE one batch dedupe too, against the same index.
        JsonObject fourth = jsonOf(call("POST", "/decks/" + key + "/entries",
                "{\"entries\":[{\"text\":\"Minigolf\"},{\"text\":\"Minigolf\"}]}", tokenA));
        assertEquals(1, fourth.get("inserted").getAsInt());
        assertEquals(1, fourth.get("skipped").getAsInt());

        // The imported metadata survives the round trip as a real JSON object.
        JsonArray cards = cards(key, tokenA);
        JsonObject kanotur = null;
        for (int i = 0; i < cards.size(); i++) {
            if ("Kanotur".equals(cards.get(i).getAsJsonObject().get("text").getAsString())) {
                kanotur = cards.get(i).getAsJsonObject();
            }
        }
        assertNotNull(kanotur, "the imported card should be swipeable: " + cards);
        assertEquals("ude", kanotur.getAsJsonObject("metadata").get("kategori").getAsString());
        assertEquals("claude-refill", kanotur.get("source").getAsString());
    }

    @Test
    @Order(41)
    void importRejectsAnUnknownDeckAMissingTokenAndAnyMalformedElement() throws Exception {
        String key = createDeck("import-validate", "Import validering",
                TinderDeckRow.MODE_DEPLETE, null, true);
        String ok = "{\"entries\":[{\"text\":\"x\"}]}";

        assertEquals(404, call("POST", "/decks/no-such-deck/entries", ok, tokenA).statusCode());
        assertEquals(401, call("POST", "/decks/" + key + "/entries", ok, null).statusCode());

        // Every one of these is a 400, never a 500 out of the driver, because
        // the whole batch is validated before a single row is written.
        assertEquals(400, importStatus(key, "{}"));
        assertEquals(400, importStatus(key, "{\"entries\":\"nope\"}"));
        assertEquals(400, importStatus(key, "{\"entries\":[]}"));
        assertEquals(400, importStatus(key, "{\"entries\":[\"just a string\"]}"));
        assertEquals(400, importStatus(key, "{\"entries\":[{}]}"));
        assertEquals(400, importStatus(key, "{\"entries\":[{\"text\":\"   \"}]}"));
        assertEquals(400, importStatus(key, "{\"entries\":[{\"text\":42}]}"));
        assertEquals(400, importStatus(key, "{\"entries\":[{\"text\":\"x\",\"metadata\":\"nope\"}]}"));
        assertEquals(400, importStatus(key, "{\"entries\":[{\"text\":\"x\",\"metadata\":[1,2]}]}"));
        assertEquals(400, importStatus(key, "{\"entries\":[{\"text\":\"x\",\"source\":7}]}"));
        assertEquals(400, importStatus(key,
                "{\"entries\":[{\"text\":" + jsonString("x".repeat(501)) + "}]}"));

        // Over the batch cap.
        StringBuilder tooBig = new StringBuilder("{\"entries\":[");
        for (int i = 0; i < 201; i++) {
            tooBig.append(i > 0 ? "," : "").append("{\"text\":\"kort ").append(i).append("\"}");
        }
        tooBig.append("]}");
        assertEquals(400, importStatus(key, tooBig.toString()));

        // Nothing above wrote anything.
        assertEquals(0, cards(key, tokenA).size(), "a rejected batch must leave the deck untouched");
    }

    // -- the refill prompt (issue #59) -----------------------------------------

    @Test
    @Order(50)
    void aDrainedIdeaDeckCarriesAReadyMadeRefillPromptNamingItsOwnEndpoint() throws Exception {
        String key = createDeck("refill-deck", "AcTindervitivities", TinderDeckRow.MODE_DEPLETE,
                null, true);
        String one = addEntry(key, "Kajak", "{\"kategori\":\"ude\",\"varighed\":\"halvdag\"}");
        String two = addEntry(key, "Keramik", "{\"kategori\":\"inde\",\"sted\":\"Nørrebro\"}");

        JsonObject before = deckOf(key, tokenA);
        assertTrue(before.get("needsRefill").getAsBoolean(),
                "two cards is already below the refill threshold");

        // Drain it completely for this caller.
        assertEquals(200, call("POST", "/decks/" + key + "/swipes",
                swipeBody(one, "left"), tokenA).statusCode());
        assertEquals(200, call("POST", "/decks/" + key + "/swipes",
                swipeBody(two, "left"), tokenA).statusCode());

        JsonObject drained = deckOf(key, tokenA);
        assertEquals(0, drained.get("remaining").getAsInt());
        assertTrue(drained.get("needsRefill").getAsBoolean());

        String prompt = drained.get("refillPrompt").getAsString();
        assertTrue(prompt.contains("AcTindervitivities"), prompt);
        assertTrue(prompt.contains("Deck key: " + key), prompt);
        assertTrue(prompt.contains("Cards to generate: 50"), prompt);
        // The metadata keys are DERIVED from the cards already in the deck, so a
        // generated batch speaks the same vocabulary instead of inventing one.
        assertTrue(prompt.contains("kategori, sted, varighed"), prompt);

        // The endpoint the prompt names must be the endpoint that actually
        // works. Asserted by posting to exactly the URL the prompt carries,
        // which is the only guard against the two drifting apart.
        String expected = PUBLIC_BASE + "/api/todo/tinder/decks/" + key + "/entries";
        assertTrue(prompt.contains(expected), prompt);
        String path = expected.substring(PUBLIC_BASE.length() + "/api/todo/tinder".length());
        assertEquals(200, call("POST", path, "{\"entries\":[{\"text\":\"Fra prompten\"}]}",
                tokenA).statusCode());
    }

    @Test
    @Order(51)
    void aFullDeckAndTheGroceryDeckNeverAskForARefill() throws Exception {
        String full = createDeck("full-deck", "Fyldt", TinderDeckRow.MODE_DEPLETE, null, true);
        for (int i = 0; i < 15; i++) {
            addEntry(full, "kort " + i, "{}");
        }
        JsonObject fullDeck = deckOf(full, tokenA);
        assertFalse(fullDeck.get("needsRefill").getAsBoolean());
        assertTrue(fullDeck.get("refillPrompt").isJsonNull(),
                "the prompt costs an extra query, so it is only built when it is needed");

        // A recycling deck cannot drain, so it must never nag: its cards are
        // staples, not generated ideas.
        String grocery = createDeck("never-refill", "SwoppingSwiper", TinderDeckRow.MODE_RECYCLE,
                null, true);
        JsonObject groceryDeck = deckOf(grocery, tokenA);
        assertEquals(0, groceryDeck.get("total").getAsInt());
        assertFalse(groceryDeck.get("needsRefill").getAsBoolean());
        assertTrue(groceryDeck.get("refillPrompt").isJsonNull());
    }

    @Test
    @Order(52)
    void theDeckListHasTheExactKeyOrder() throws Exception {
        HttpResponse<String> res = call("GET", "/decks", null, tokenA);
        assertEquals(200, res.statusCode());
        assertKeyOrder(res.body(), "decks", "key", "displayName", "recycleMode", "datasetKey",
                "targetListId", "total", "remaining", "needsRefill", "refillPrompt");
    }

    // -- 503 when the backend is not configured --------------------------------

    @Test
    @Order(60)
    void answersServiceUnavailableWhenDatabaseNotConfigured() throws Exception {
        Backend noDbBackend = new Backend(
                ApiConfig.of(0, null, "some-secret"), null,
                new Token("some-secret"), null, null, null);
        Javalin noDbApp = ApiServer.create(noDbBackend);
        try {
            noDbApp.start(0);
            String url = "http://localhost:" + noDbApp.port() + "/api/todo/tinder/decks";
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10)).GET().build();
            assertEquals(503, http.send(req, HttpResponse.BodyHandlers.ofString()).statusCode());
        } finally {
            noDbApp.stop();
        }
    }

    // -- fixtures --------------------------------------------------------------

    private String createUser(String email, String name) {
        jdbi.useHandle(h -> h
                .createUpdate("INSERT INTO users (email, name, pw_hash) VALUES (:e, :n, :p)")
                .bind("e", email)
                .bind("n", name)
                .bind("p", "irrelevant-hash")
                .execute());
        return todo.findUserByEmail(email).orElseThrow().id();
    }

    /** Inserts a deck and returns its KEY, which is what every route takes. */
    private String createDeck(String key, String displayName, String mode, String targetListId,
                              boolean active) {
        jdbi.useHandle(h -> {
            Update u = h.createUpdate(
                    "INSERT INTO tinder_decks (key, display_name, recycle_mode, target_list_id, "
                    + "dataset_key, active) VALUES (:key, :name, :mode, CAST(:listId AS uuid), "
                    + ":dataset, :active)");
            u.bind("key", key);
            u.bind("name", displayName);
            u.bind("mode", mode);
            if (targetListId == null) {
                u.bindNull("listId", Types.VARCHAR);
            } else {
                u.bind("listId", targetListId);
            }
            u.bind("dataset", key + "-da");
            u.bind("active", active);
            u.execute();
        });
        return key;
    }

    private String addEntry(String deckKey, String text, String metadata) {
        return jdbi.withHandle(h -> h
                .createUpdate("INSERT INTO tinder_entries (deck_id, text, metadata) "
                        + "SELECT d.id, :text, CAST(:metadata AS jsonb) FROM tinder_decks d "
                        + "WHERE d.key = :key RETURNING id")
                .bind("key", deckKey)
                .bind("text", text)
                .bind("metadata", metadata)
                .executeAndReturnGeneratedKeys()
                .mapTo(String.class)
                .one());
    }

    private int openItemCount(String listId, String text) {
        return jdbi.withHandle(h -> h
                .createQuery("SELECT COUNT(*) FROM items WHERE list_id = CAST(:listId AS uuid) "
                        + "AND status <> 'DONE' AND lower(btrim(text)) = lower(btrim(:text))")
                .bind("listId", listId)
                .bind("text", text)
                .mapTo(Integer.class)
                .one());
    }

    private void markEverythingDone(String listId) {
        jdbi.useHandle(h -> h
                .createUpdate("UPDATE items SET status = 'DONE', done = true "
                        + "WHERE list_id = CAST(:listId AS uuid)")
                .bind("listId", listId)
                .execute());
    }

    private int swipeRowCount(String userId, String entryId) {
        return jdbi.withHandle(h -> h
                .createQuery("SELECT COUNT(*) FROM tinder_swipes WHERE user_id = CAST(:u AS uuid) "
                        + "AND entry_id = CAST(:e AS uuid)")
                .bind("u", userId)
                .bind("e", entryId)
                .mapTo(Integer.class)
                .one());
    }

    private void assertColumn(String table, String column, String type, String nullable) {
        List<String> found = jdbi.withHandle(h -> h
                .createQuery("SELECT data_type || '/' || is_nullable FROM information_schema.columns "
                        + "WHERE table_name = :t AND column_name = :c")
                .bind("t", table)
                .bind("c", column)
                .mapTo(String.class)
                .list());
        assertEquals(List.of(type + "/" + nullable), found, table + "." + column);
    }

    private void assertUniqueIndex(String table, String indexName, String... columns) {
        String def = jdbi.withHandle(h -> h
                .createQuery("SELECT indexdef FROM pg_indexes WHERE tablename = :t AND indexname = :i")
                .bind("t", table)
                .bind("i", indexName)
                .mapTo(String.class)
                .findFirst()
                .orElse(null));
        assertNotNull(def, "missing index " + indexName + " on " + table);
        assertTrue(def.startsWith("CREATE UNIQUE INDEX"), def);
        for (String column : columns) {
            assertTrue(def.contains(column), indexName + " should cover " + column + ": " + def);
        }
    }

    // -- HTTP helpers ----------------------------------------------------------

    private HttpResponse<String> swipeRight(String deckKey, String entryId, String bearer)
            throws Exception {
        HttpResponse<String> res = call("POST", "/decks/" + deckKey + "/swipes",
                swipeBody(entryId, "right"), bearer);
        assertEquals(200, res.statusCode(), res.body());
        return res;
    }

    private static String swipeBody(String entryId, String direction) {
        return "{\"entryId\":" + jsonString(entryId) + ",\"direction\":" + jsonString(direction) + "}";
    }

    private int importStatus(String deckKey, String body) throws Exception {
        return call("POST", "/decks/" + deckKey + "/entries", body, tokenA).statusCode();
    }

    private JsonArray decks(String bearer) throws Exception {
        HttpResponse<String> res = call("GET", "/decks", null, bearer);
        assertEquals(200, res.statusCode(), res.body());
        return jsonOf(res).getAsJsonArray("decks");
    }

    private JsonObject deckOf(String key, String bearer) throws Exception {
        JsonArray all = decks(bearer);
        for (int i = 0; i < all.size(); i++) {
            JsonObject d = all.get(i).getAsJsonObject();
            if (key.equals(d.get("key").getAsString())) {
                return d;
            }
        }
        throw new AssertionError("deck " + key + " not listed: " + all);
    }

    private static List<String> deckKeys(JsonArray decks) {
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < decks.size(); i++) {
            keys.add(decks.get(i).getAsJsonObject().get("key").getAsString());
        }
        return keys;
    }

    private JsonArray cards(String key, String bearer) throws Exception {
        return cardsWithLimit(key, bearer, null);
    }

    private JsonArray cardsWithLimit(String key, String bearer, String limit) throws Exception {
        String path = "/decks/" + key + "/cards" + (limit == null ? "" : "?limit=" + limit);
        HttpResponse<String> res = call("GET", path, null, bearer);
        assertEquals(200, res.statusCode(), res.body());
        return jsonOf(res).getAsJsonArray("cards");
    }

    private List<String> cardIds(String key, String bearer) throws Exception {
        return idsOf(cards(key, bearer));
    }

    private List<String> cardIdsWithLimit(String key, String bearer, String limit) throws Exception {
        return idsOf(cardsWithLimit(key, bearer, limit));
    }

    private static List<String> idsOf(JsonArray cards) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < cards.size(); i++) {
            ids.add(cards.get(i).getAsJsonObject().get("id").getAsString());
        }
        return ids;
    }

    private JsonArray matches(String bearer) throws Exception {
        HttpResponse<String> res = call("GET", "/matches", null, bearer);
        assertEquals(200, res.statusCode(), res.body());
        return jsonOf(res).getAsJsonArray("matches");
    }

    private static JsonObject jsonOf(HttpResponse<String> res) {
        return JsonParser.parseString(res.body()).getAsJsonObject();
    }

    private static String jsonString(String s) {
        return new Gson().toJson(s);
    }

    private HttpResponse<String> call(String method, String path, String body, String bearer)
            throws Exception {
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .method(method, publisher);
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** Asserts each key literal appears in the body, in the given order. */
    private static void assertKeyOrder(String json, String... keys) {
        int last = -1;
        for (String key : keys) {
            int idx = json.indexOf("\"" + key + "\"");
            assertTrue(idx > last, "expected key '" + key + "' after position " + last + " in: " + json);
            last = idx;
        }
    }
}
