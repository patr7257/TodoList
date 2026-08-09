package dk.dtu.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import dk.dtu.api.auth.AuthService;
import dk.dtu.api.auth.Token;
import dk.dtu.api.db.Migrations;
import dk.dtu.api.domain.ItemRow;
import dk.dtu.api.domain.ListRow;
import dk.dtu.api.domain.NewItem;
import dk.dtu.api.domain.ShareTokens;
import dk.dtu.api.domain.SharesService;
import dk.dtu.api.domain.TodoService;
import dk.dtu.api.web.ApiServer;
import dk.dtu.api.web.Backend;
import dk.dtu.api.web.RateLimiter;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.javalin.Javalin;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * End-to-end HTTP tests for the public share links (issue #52), against a real
 * (embedded) Postgres and a real Javalin app on an OS-assigned ephemeral port.
 *
 * <p>The centre of gravity here is NEGATIVE: that the management routes are
 * still locked down after the AuthFilter allowlist change, that the public
 * route is reachable with no credentials at all, that every failure mode looks
 * byte-identical from outside, and that nothing internal leaks into the one
 * unauthenticated payload this API has.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SharesIntegrationTest {

    /** Pinned through ApiConfig's share overload, so the composed url is predictable. */
    private static final String SHARE_BASE_URL = "https://shares.test.invalid";

    private static final String OWNER_UUID_MARKER = "owner-uuid";
    private static final String ASSIGNEE_UUID_MARKER = "assignee-uuid";

    private EmbeddedPostgres pg;
    private Jdbi jdbi;
    private TodoService todo;
    private SharesService shares;
    private Javalin app;
    private String origin;
    private String bearerToken;
    private final HttpClient http = HttpClient.newHttpClient();

    /** The fully populated fixture list used by the leak test. */
    private String fixtureListId;
    private String fixtureOwnerId;
    private String fixtureAssigneeId;
    private String fixtureCreatorId;

    @BeforeAll
    void startServer() throws IOException {
        pg = EmbeddedPostgres.builder().start();
        DataSource ds = pg.getPostgresDatabase();
        Migrations.migrate(ds);

        jdbi = Jdbi.create(ds);
        todo = new TodoService(jdbi);
        shares = new SharesService(jdbi);
        Token token = new Token("shares-integration-secret");
        AuthService auth = new AuthService(todo, token);
        Backend backend = new Backend(
                ApiConfig.of(0, null, "shares-integration-secret", 1000, 60,
                        SHARE_BASE_URL, 1000, 60),
                todo, auth, token, new RateLimiter(1000, 60), null,
                shares, new RateLimiter(1000, 60));

        app = ApiServer.create(backend);
        app.start(0);
        origin = "http://localhost:" + app.port();

        String callerId = seedUser("shares-tester@example.com", "Shares Tester");
        bearerToken = token.issue(callerId);
        fixtureCreatorId = callerId;

        // A fixture with EVERY internal field populated, so the leak test has
        // something concrete to fail on rather than proving a vacuous absence.
        fixtureOwnerId = seedUser("shares-owner@example.com", "Patrick Robel");
        fixtureAssigneeId = seedUser("shares-assignee@example.com", "Assignee Person");
        ListRow list = todo.insertList("Christmas wishlist", "Owner Free Text", fixtureOwnerId);
        fixtureListId = list.id();
        jdbi.useHandle(h -> h
                .createUpdate("UPDATE lists SET priority = 1, year = 2026, location = :loc, "
                        + "description = :desc, task_columns_json = :cols "
                        + "WHERE id = CAST(:id AS uuid)")
                .bind("loc", "Brede Alle 12, Kongens Lyngby")
                .bind("desc", "Things I would like")
                .bind("cols", "[{\"col\":\"x\"}]")
                .bind("id", fixtureListId)
                .execute());

        seedItem("A very specific gift", "NOT_STARTED");
        seedItem("Something halfway", "IN_PROGRESS");
        seedItem("Already bought", "DONE");
        jdbi.useHandle(h -> h
                .createUpdate("UPDATE items SET year = 2026 WHERE list_id = CAST(:id AS uuid)")
                .bind("id", fixtureListId)
                .execute());
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

    // -- V6 schema shape --------------------------------------------------------

    @Test
    @Order(1)
    void v6CreatesListSharesWithAUniqueTokenIndexAndACascadingListForeignKey() {
        List<String> tables = jdbi.withHandle(h -> h
                .createQuery("SELECT table_name FROM information_schema.tables WHERE table_name = 'list_shares'")
                .mapTo(String.class)
                .list());
        assertEquals(List.of("list_shares"), tables, "V6 should create the list_shares table");

        List<String> columns = jdbi.withHandle(h -> h
                .createQuery("SELECT column_name FROM information_schema.columns WHERE table_name = 'list_shares'")
                .mapTo(String.class)
                .list());
        assertTrue(columns.containsAll(List.of("id", "list_id", "token", "label", "created_by",
                "created_at", "expires_at", "revoked_at", "last_viewed_at", "view_count")),
                "V6 column set is incomplete, got " + columns);

        List<String> tokenIndexDefs = jdbi.withHandle(h -> h
                .createQuery("SELECT indexdef FROM pg_indexes WHERE tablename = 'list_shares' "
                        + "AND indexname = 'list_shares_token_key'")
                .mapTo(String.class)
                .list());
        assertEquals(1, tokenIndexDefs.size(), "V6 should add list_shares_token_key");
        assertTrue(tokenIndexDefs.get(0).startsWith("CREATE UNIQUE INDEX"),
                "the token index must be UNIQUE (two shares sharing a token would be "
                + "ambiguous to resolve), got " + tokenIndexDefs.get(0));

        List<String> listIndexes = jdbi.withHandle(h -> h
                .createQuery("SELECT indexname FROM pg_indexes WHERE tablename = 'list_shares'")
                .mapTo(String.class)
                .list());
        assertTrue(listIndexes.contains("list_shares_list_id_idx"),
                "V6 should add list_shares_list_id_idx, got " + listIndexes);

        List<String> deleteRules = jdbi.withHandle(h -> h
                .createQuery("""
                        SELECT rc.delete_rule
                        FROM information_schema.referential_constraints rc
                        JOIN information_schema.key_column_usage kcu
                          ON kcu.constraint_name = rc.constraint_name
                        WHERE kcu.table_name = 'list_shares'
                          AND kcu.column_name = 'list_id'
                        """)
                .mapTo(String.class)
                .list());
        assertEquals(List.of("CASCADE"), deleteRules,
                "list_shares.list_id must be ON DELETE CASCADE, or deleting a list would leave "
                + "a live token pointing at nothing; got " + deleteRules);
    }

    @Test
    @Order(2)
    void reMigratingIsANoOp() {
        Migrations.migrate(pg.getPostgresDatabase());
        List<String> tables = jdbi.withHandle(h -> h
                .createQuery("SELECT table_name FROM information_schema.tables WHERE table_name = 'list_shares'")
                .mapTo(String.class)
                .list());
        assertEquals(List.of("list_shares"), tables);
    }

    // -- auth: the management routes stayed shut ------------------------------

    @Test
    @Order(3)
    void allThreeManagementRoutesRequireABearerToken() throws Exception {
        // The guard for the AuthFilter allowlist change: swapping the suffix
        // match for an explicit allowlist plus a /api/todo/share/ prefix must
        // NOT have opened anything under the plural .../shares management tree.
        String base = "/api/todo/lists/" + fixtureListId + "/shares";
        assertEquals(401, call("GET", base, null, null).statusCode());
        assertEquals(401, call("POST", base, "{}", null).statusCode());
        assertEquals(401, call("DELETE", base + "/00000000-0000-0000-0000-000000000000",
                null, null).statusCode());
    }

    // -- the public route needs no credentials at all --------------------------

    @Test
    @Order(4)
    void publicShareRouteAnswers200WithNoAuthorizationHeaderAndNoCookie() throws Exception {
        String token = createShare("no-auth check");

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(origin + "/api/todo/share/" + token))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        // Deliberately built without any Authorization header and without a
        // Cookie header: the exact inverse of the counters test's 401 sweep.
        assertTrue(req.headers().firstValue("Authorization").isEmpty());
        assertTrue(req.headers().firstValue("Cookie").isEmpty());

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res.statusCode(), res.body());
        assertEquals("Christmas wishlist",
                listOf(res).get("name").getAsString());
    }

    // -- exact payload shape ---------------------------------------------------

    @Test
    @Order(5)
    void publicPayloadHasTheExactPinnedKeyOrderForTheListAndForEveryItem() throws Exception {
        String token = createShare("shape check");
        HttpResponse<String> res = callPublic(token);
        assertEquals(200, res.statusCode(), res.body());

        JsonObject root = JsonParser.parseString(res.body()).getAsJsonObject();
        assertKeysExactlyInOrder(root, "list", "items");

        // Note: a whole-string indexOf walk (the counters test's helper) cannot
        // be used here, because "description" legitimately appears at BOTH the
        // list level and the item level. Gson's LinkedTreeMap keeps insertion
        // order, so each object is walked on its own instead.
        assertKeysExactlyInOrder(root.getAsJsonObject("list"),
                "name", "description", "sharedBy", "itemCount", "doneCount",
                "completionPercentage", "expiresAt");

        JsonArray items = root.getAsJsonArray("items");
        assertEquals(3, items.size());
        for (int i = 0; i < items.size(); i++) {
            assertKeysExactlyInOrder(items.get(i).getAsJsonObject(),
                    "id", "text", "description", "done", "status");
        }

        JsonObject list = root.getAsJsonObject("list");
        assertEquals("Patrick Robel", list.get("sharedBy").getAsString(),
                "sharedBy resolves users.name through lists.owner_id");
        assertEquals(3, list.get("itemCount").getAsInt());
        assertEquals(1, list.get("doneCount").getAsInt());
        // 0 + 50 + 100 = 150 / 3 = 50, the same math both clients use.
        assertEquals(50, list.get("completionPercentage").getAsInt());
        assertTrue(list.get("expiresAt").isJsonNull(),
                "a never-expiring share still emits the key, as null");
    }

    // -- every failure looks the same -----------------------------------------

    @Test
    @Order(6)
    void unknownMalformedRevokedAndExpiredTokensAllAnswerAByteIdentical404() throws Exception {
        // 1. Unknown: correctly shaped, never issued.
        HttpResponse<String> unknown = callPublic(ShareTokens.generate());

        // 2. Malformed: right route, wrong shape (rejected before the database).
        HttpResponse<String> malformed = callPublic("nope");

        // 3. Revoked.
        String revokedToken = createShare("to revoke");
        String revokedId = shareIdFor(revokedToken);
        assertEquals(200, call("DELETE",
                "/api/todo/lists/" + fixtureListId + "/shares/" + revokedId,
                null, bearerToken).statusCode());
        HttpResponse<String> revoked = callPublic(revokedToken);

        // 4. Expired: expires_at in the past. Nothing writes expiry yet, so it
        // is set directly here; the READ path must honour it regardless.
        String expiredToken = ShareTokens.generate();
        jdbi.useHandle(h -> h
                .createUpdate("INSERT INTO list_shares (list_id, token, expires_at) "
                        + "VALUES (CAST(:listId AS uuid), :token, now() - interval '1 day')")
                .bind("listId", fixtureListId)
                .bind("token", expiredToken)
                .execute());
        HttpResponse<String> expired = callPublic(expiredToken);

        assertEquals(404, unknown.statusCode());
        assertEquals(404, malformed.statusCode());
        assertEquals(404, revoked.statusCode());
        assertEquals(404, expired.statusCode());

        // Byte-identical bodies: a prober must not be able to tell "no such
        // share" from "revoked" from "expired" from "not even a token".
        assertEquals(unknown.body(), malformed.body());
        assertEquals(unknown.body(), revoked.body());
        assertEquals(unknown.body(), expired.body());
    }

    @Test
    @Order(7)
    void revokingMakesAPreviouslyWorkingLinkStop() throws Exception {
        String token = createShare("revoke me");
        assertEquals(200, callPublic(token).statusCode(), "the link works before revocation");

        String shareId = shareIdFor(token);
        HttpResponse<String> revoke = call("DELETE",
                "/api/todo/lists/" + fixtureListId + "/shares/" + shareId, null, bearerToken);
        assertEquals(200, revoke.statusCode());
        assertTrue(JsonParser.parseString(revoke.body()).getAsJsonObject().get("ok").getAsBoolean());

        assertEquals(404, callPublic(token).statusCode(), "the link is dead after revocation");

        // Revoking twice is a 404, not a silent success.
        assertEquals(404, call("DELETE",
                "/api/todo/lists/" + fixtureListId + "/shares/" + shareId, null, bearerToken).statusCode());

        // ...and it disappears from the management listing, which shows live links only.
        HttpResponse<String> listed = call("GET",
                "/api/todo/lists/" + fixtureListId + "/shares", null, bearerToken);
        assertEquals(200, listed.statusCode());
        assertFalse(listed.body().contains(shareId),
                "a revoked share must not be listed as active: " + listed.body());
    }

    // -- nothing internal leaks -------------------------------------------------

    @Test
    @Order(8)
    void publicPayloadLeaksNoInternalIdentifiersOrFieldNames() throws Exception {
        String token = createShare("leak check");
        HttpResponse<String> res = callPublic(token);
        assertEquals(200, res.statusCode(), res.body());
        String body = res.body();

        // Values. Every one of these is populated on the fixture, so their
        // absence here is a real assertion and not a vacuous one.
        assertFalse(body.contains(fixtureListId), "the list id leaked: " + body);
        assertFalse(body.contains(fixtureOwnerId), "an " + OWNER_UUID_MARKER + " leaked: " + body);
        assertFalse(body.contains(fixtureAssigneeId), "an " + ASSIGNEE_UUID_MARKER + " leaked: " + body);
        assertFalse(body.contains(fixtureCreatorId), "a created_by uuid leaked: " + body);
        assertFalse(body.contains("Brede Alle"), "the list location leaked: " + body);
        assertFalse(body.contains("Illum"), "an item location leaked: " + body);
        assertFalse(body.contains("Owner Free Text"), "the legacy lists.owner text leaked: " + body);
        assertFalse(body.contains("task_columns"), "task columns leaked: " + body);
        assertFalse(body.contains(token), "the bearer token is echoed back for nothing: " + body);

        // Key names.
        for (String forbidden : List.of("ownerId", "ownerName", "owner", "taskColumnsJson",
                "location", "priority", "year", "sort", "createdAt", "listId", "assigneeId",
                "assigneeName", "createdBy", "dueAt", "updatedAt")) {
            assertFalse(body.contains("\"" + forbidden + "\""),
                    "forbidden key '" + forbidden + "' is in the public payload: " + body);
        }

        // Sanity: the fixture really does carry the values asserted absent.
        List<ItemRow> items = shares.itemsForList(fixtureListId);
        assertEquals(3, items.size());
        assertTrue(items.stream().allMatch(i -> fixtureAssigneeId.equals(i.assigneeId())),
                "fixture items must actually have an assignee, or the leak test proves nothing");
        assertTrue(items.stream().allMatch(i -> i.location() != null));
        assertTrue(items.stream().allMatch(i -> i.dueAt() != null));
    }

    // -- view counting ----------------------------------------------------------

    @Test
    @Order(9)
    void viewCountIncrementsOnceAndIsThrottledOnAnImmediateSecondView() throws Exception {
        String token = createShare("view count");
        assertEquals(0, viewCountOf(token), "a fresh share starts at zero views");

        assertEquals(200, callPublic(token).statusCode());
        assertEquals(1, viewCountOf(token), "the first view must be counted");

        assertEquals(200, callPublic(token).statusCode());
        assertEquals(1, viewCountOf(token),
                "an immediate second view is inside the 5 minute throttle window, so the "
                + "counter measures visits rather than page refreshes");
    }

    // -- the composed url -------------------------------------------------------

    @Test
    @Order(10)
    void createdShareUrlIsTheConfiguredBasePlusSlashSPlusToken() throws Exception {
        HttpResponse<String> res = call("POST", "/api/todo/lists/" + fixtureListId + "/shares",
                "{\"label\":\"  phone  \"}", bearerToken);
        assertEquals(200, res.statusCode(), res.body());

        JsonObject share = JsonParser.parseString(res.body()).getAsJsonObject()
                .getAsJsonObject("share");
        assertKeysExactlyInOrder(share, "id", "label", "url", "token",
                "createdAt", "expiresAt", "lastViewedAt", "viewCount");

        String token = share.get("token").getAsString();
        assertEquals(SHARE_BASE_URL + "/s/" + token, share.get("url").getAsString(),
                "the API composes the share url, so both clients cannot disagree about it");
        assertTrue(ShareTokens.isWellFormed(token), "a served token must be well shaped: " + token);
        assertEquals("phone", share.get("label").getAsString(), "the label is trimmed");
        assertEquals(0, share.get("viewCount").getAsInt());
        assertTrue(share.get("expiresAt").isJsonNull());

        // A blank label collapses to null rather than to an empty string.
        HttpResponse<String> blank = call("POST", "/api/todo/lists/" + fixtureListId + "/shares",
                "{\"label\":\"   \"}", bearerToken);
        assertEquals(200, blank.statusCode(), blank.body());
        assertTrue(JsonParser.parseString(blank.body()).getAsJsonObject()
                .getAsJsonObject("share").get("label").isJsonNull());

        // Two shares of the same list never collide.
        HttpResponse<String> second = call("POST", "/api/todo/lists/" + fixtureListId + "/shares",
                "{}", bearerToken);
        assertEquals(200, second.statusCode(), second.body());
        assertNotEquals(token, JsonParser.parseString(second.body()).getAsJsonObject()
                .getAsJsonObject("share").get("token").getAsString());
    }

    @Test
    @Order(11)
    void creatingAShareForAnUnknownOrNonUuidListIs404NotAForeignKey500() throws Exception {
        assertEquals(404, call("POST",
                "/api/todo/lists/00000000-0000-0000-0000-000000000000/shares", "{}", bearerToken)
                .statusCode());
        assertEquals(404, call("POST",
                "/api/todo/lists/not-a-uuid/shares", "{}", bearerToken).statusCode());
    }

    // -- rate limiting ----------------------------------------------------------

    @Test
    @Order(12)
    void publicRouteRateLimitsWith429() throws Exception {
        // A SECOND Javalin with a deliberately tight limiter. The main app's
        // limiter is never squeezed: throttling it would poison every ordered
        // test that runs after this one.
        Token token = new Token("shares-integration-secret");
        Backend tight = new Backend(
                ApiConfig.of(0, null, "shares-integration-secret", 1000, 60,
                        SHARE_BASE_URL, 2, 60),
                todo, new AuthService(todo, token), token, new RateLimiter(1000, 60), null,
                shares, new RateLimiter(2, 60));
        Javalin tightApp = ApiServer.create(tight);
        try {
            tightApp.start(0);
            String url = "http://localhost:" + tightApp.port() + "/api/todo/share/"
                    + ShareTokens.generate();

            assertEquals(404, getStatus(url), "request 1 of 2 is allowed (unknown token, so 404)");
            assertEquals(404, getStatus(url), "request 2 of 2 is allowed");
            assertEquals(429, getStatus(url), "request 3 exceeds the window and is refused");
        } finally {
            tightApp.stop();
        }
    }

    // -- 503 when the database is not configured -------------------------------

    @Test
    @Order(13)
    void publicRouteAnswers503WhenTheDatabaseIsNotConfigured() throws Exception {
        // AuthFilter steps aside entirely with no database, so this route is
        // reached with null services and must answer 503 itself rather than
        // NPE into a 500.
        Backend noDb = new Backend(
                ApiConfig.of(0, null, "some-secret", 1000, 60), null, null,
                new Token("some-secret"), new RateLimiter(1000, 60));
        Javalin noDbApp = ApiServer.create(noDb);
        try {
            noDbApp.start(0);
            String url = "http://localhost:" + noDbApp.port() + "/api/todo/share/"
                    + ShareTokens.generate();
            HttpResponse<String> res = http.send(
                    HttpRequest.newBuilder().uri(URI.create(url))
                            .timeout(Duration.ofSeconds(10)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(503, res.statusCode(), res.body());
        } finally {
            noDbApp.stop();
        }
    }

    // -- cascade ----------------------------------------------------------------

    @Test
    @Order(14)
    void deletingTheListCascadesItsSharesSoTheTokenStopsResolving() throws Exception {
        ListRow throwaway = todo.insertList("Throwaway", null, null);
        HttpResponse<String> created = call("POST",
                "/api/todo/lists/" + throwaway.id() + "/shares", "{}", bearerToken);
        assertEquals(200, created.statusCode(), created.body());
        String token = JsonParser.parseString(created.body()).getAsJsonObject()
                .getAsJsonObject("share").get("token").getAsString();
        assertEquals(200, callPublic(token).statusCode());

        assertTrue(todo.deleteList(throwaway.id()));

        assertEquals(404, callPublic(token).statusCode(),
                "a deleted list must take its share links with it");
        Integer remaining = jdbi.withHandle(h -> h
                .createQuery("SELECT count(*) FROM list_shares WHERE token = :t")
                .bind("t", token)
                .mapTo(Integer.class)
                .one());
        assertEquals(0, remaining, "ON DELETE CASCADE should have removed the share row");
    }

    // -- helpers ---------------------------------------------------------------

    private String seedUser(String email, String name) {
        jdbi.useHandle(h -> h
                .createUpdate("INSERT INTO users (email, name, pw_hash) VALUES (:e, :n, :p)")
                .bind("e", email)
                .bind("n", name)
                .bind("p", "irrelevant-hash")
                .execute());
        return todo.findUserByEmail(email).orElseThrow().id();
    }

    private void seedItem(String text, String status) {
        todo.insertItem(new NewItem(fixtureListId, text, "in the blue box", status,
                1, java.time.Instant.parse("2026-12-24T17:00:00Z"), "Illum, Copenhagen",
                fixtureAssigneeId, fixtureCreatorId));
    }

    /** Creates a share for the fixture list through the authenticated API. */
    private String createShare(String label) throws Exception {
        HttpResponse<String> res = call("POST", "/api/todo/lists/" + fixtureListId + "/shares",
                "{\"label\":\"" + label + "\"}", bearerToken);
        assertEquals(200, res.statusCode(), res.body());
        return JsonParser.parseString(res.body()).getAsJsonObject()
                .getAsJsonObject("share").get("token").getAsString();
    }

    private String shareIdFor(String token) {
        return jdbi.withHandle(h -> h
                .createQuery("SELECT id FROM list_shares WHERE token = :t")
                .bind("t", token)
                .mapTo(String.class)
                .one());
    }

    private int viewCountOf(String token) {
        return jdbi.withHandle(h -> h
                .createQuery("SELECT view_count FROM list_shares WHERE token = :t")
                .bind("t", token)
                .mapTo(Integer.class)
                .one());
    }

    private static JsonObject listOf(HttpResponse<String> res) {
        return JsonParser.parseString(res.body()).getAsJsonObject().getAsJsonObject("list");
    }

    /** GET the public route with NO Authorization header and NO cookie. */
    private HttpResponse<String> callPublic(String token) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(origin + "/api/todo/share/" + token))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private int getStatus(String url) throws Exception {
        return http.send(HttpRequest.newBuilder().uri(URI.create(url))
                .timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private HttpResponse<String> call(String method, String path, String body, String token)
            throws Exception {
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(origin + path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .method(method, publisher);
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Asserts a JSON object's keys are EXACTLY these, in this order.
     *
     * <p>Walks {@code entrySet()} per object rather than doing an ordinal
     * indexOf over the whole response string (the counters test's helper),
     * which cannot work here: {@code description} legitimately appears at both
     * the list level and the item level, so a whole-string walk would match the
     * wrong occurrence. Gson parses into a LinkedTreeMap, so insertion order is
     * preserved and this is a real order assertion.
     */
    private static void assertKeysExactlyInOrder(JsonObject obj, String... expected) {
        List<String> actual = new ArrayList<>();
        obj.entrySet().forEach(e -> actual.add(e.getKey()));
        assertEquals(List.of(expected), actual,
                "unexpected key set or order in: " + obj);
    }
}
