package dk.dtu.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import javax.sql.DataSource;

import dk.dtu.api.auth.AuthService;
import dk.dtu.api.auth.Token;
import dk.dtu.api.db.Migrations;
import dk.dtu.api.domain.CounterRow;
import dk.dtu.api.domain.CountersService;
import dk.dtu.api.domain.TodoService;
import dk.dtu.api.web.ApiServer;
import dk.dtu.api.web.Backend;
import dk.dtu.api.web.RateLimiter;

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
 * End-to-end HTTP tests for the fun-counters routes (issue #46), against a real
 * (embedded) Postgres and a real Javalin app bound to an OS-assigned ephemeral
 * port (never 8080, so this never collides with a locally running API). Covers:
 * V5's schema + idempotent seed, that every route is auth-protected (proven by
 * an actual 401, not assumed), the exact response shapes/key order, and the
 * delta-bump validation rules.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CountersIntegrationTest {

    private EmbeddedPostgres pg;
    private CountersService counters;
    private Javalin app;
    private String baseUrl;
    private String bearerToken;
    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeAll
    void startServer() throws IOException {
        pg = EmbeddedPostgres.builder().start();
        DataSource ds = pg.getPostgresDatabase();
        Migrations.migrate(ds);

        Jdbi jdbi = Jdbi.create(ds);
        TodoService todo = new TodoService(jdbi);
        counters = new CountersService(jdbi);
        Token token = new Token("counters-integration-secret");
        AuthService auth = new AuthService(todo, token);
        Backend backend = new Backend(
                ApiConfig.of(0, null, "counters-integration-secret", 1000, 60),
                todo, auth, token, new RateLimiter(1000, 60), counters);

        app = ApiServer.create(backend);
        app.start(0);
        baseUrl = "http://localhost:" + app.port() + "/api/todo/counters";

        // A real user id so bump/created_by paths mirror production usage.
        jdbi.useHandle(h -> h
                .createUpdate("INSERT INTO users (email, name, pw_hash) VALUES (:e, :n, :p)")
                .bind("e", "counters-tester@example.com")
                .bind("n", "Counters Tester")
                .bind("p", "irrelevant-hash")
                .execute());
        String uid = todo.findUserByEmail("counters-tester@example.com").orElseThrow().id();
        bearerToken = token.issue(uid);
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

    // -- V5 schema + seed -------------------------------------------------------

    // Tests share ONE embedded database (@TestInstance PER_CLASS), and later
    // tests create/delete counters, so the seed-shape assertions below must run
    // (and are ordered to run) before anything else touches the table.
    @Test
    @Order(1)
    void freshDatabaseHasFunCountersTableWithSeededRows() {
        List<CounterRow> rows = counters.allOrdered();
        assertEquals(3, rows.size(), "expected exactly the 3 seeded counters, got " + rows);
        assertEquals("Total Flights", rows.get(0).label());
        assertEquals(0, rows.get(0).sort());
        assertEquals("fth-send", rows.get(0).icon());
        assertEquals("Total Ships", rows.get(1).label());
        assertEquals(1, rows.get(1).sort());
        assertEquals("fth-anchor", rows.get(1).icon());
        assertEquals("Tour de Brede", rows.get(2).label());
        assertEquals(2, rows.get(2).sort());
        assertEquals("fth-compass", rows.get(2).icon());
        for (CounterRow r : rows) {
            assertEquals(0, r.value());
        }
    }

    @Test
    @Order(2)
    void reMigratingDoesNotDuplicateAndDeletingASeedDoesNotResurrectIt() throws IOException {
        DataSource ds = pg.getPostgresDatabase();

        Migrations.migrate(ds);
        assertEquals(3, counters.allOrdered().size(), "re-running migrate should not duplicate the seed");

        CounterRow toDelete = counters.allOrdered().stream()
                .filter(r -> "Total Ships".equals(r.label())).findFirst().orElseThrow();
        assertTrue(counters.delete(toDelete.id()));
        assertEquals(2, counters.allOrdered().size());

        Migrations.migrate(ds);
        assertEquals(2, counters.allOrdered().size(),
                "deleting a seeded counter then re-migrating must not resurrect it");
        assertTrue(counters.allOrdered().stream().noneMatch(r -> r.id().equals(toDelete.id())));

        // Restore for the remaining tests, which assume the three seeded counters
        // are undisturbed by test ordering (JUnit does not guarantee method order,
        // so re-seed defensively without relying on the migration's own guard).
        if (counters.allOrdered().stream().noneMatch(r -> "Total Ships".equals(r.label()))) {
            counters.insert("Total Ships", "restored by test", 0, "fth-anchor", null);
        }
    }

    // -- auth ---------------------------------------------------------------

    @Test
    @Order(3)
    void allFourRoutesRequireABearerToken() throws Exception {
        assertEquals(401, call("GET", "", null, null).statusCode());
        assertEquals(401, call("POST", "", "{\"label\":\"x\"}", null).statusCode());
        assertEquals(401, call("PATCH", "/00000000-0000-0000-0000-000000000000", "{\"label\":\"x\"}", null).statusCode());
        assertEquals(401, call("DELETE", "/00000000-0000-0000-0000-000000000000", null, null).statusCode());
    }

    // -- GET ----------------------------------------------------------------

    @Test
    @Order(4)
    void getReturnsCountersWrappedAndOrderedWithExactKeyOrder() throws Exception {
        HttpResponse<String> res = call("GET", "", null, bearerToken);
        assertEquals(200, res.statusCode());
        assertKeyOrder(res.body(), "counters", "id", "label", "description", "value", "icon", "sort",
                "createdBy", "createdAt", "updatedAt");

        JsonObject obj = JsonParser.parseString(res.body()).getAsJsonObject();
        assertTrue(obj.get("counters").getAsJsonArray().size() >= 3);
    }

    // -- POST -----------------------------------------------------------------

    @Test
    @Order(5)
    void createReturnsCounterWrappedWithDefaultsAndExactKeyOrder() throws Exception {
        int maxSortBefore = counters.allOrdered().stream().mapToInt(CounterRow::sort).max().orElse(-1);

        HttpResponse<String> res = call("POST", "", "{\"label\":\"Marathons\"}", bearerToken);
        assertEquals(200, res.statusCode());
        assertKeyOrder(res.body(), "counter", "id", "label", "description", "value", "icon", "sort",
                "createdBy", "createdAt", "updatedAt");

        JsonObject c = JsonParser.parseString(res.body()).getAsJsonObject().getAsJsonObject("counter");
        assertEquals("Marathons", c.get("label").getAsString());
        assertEquals(0, c.get("value").getAsInt());
        assertTrue(c.get("description").isJsonNull());
        assertTrue(c.get("icon").isJsonNull());
        // A new counter must be appended after every existing one (max(sort)+1),
        // never fall back to the column DEFAULT 0 and collide with a seeded
        // counter's sort. Not a hardcoded number: earlier @Order tests add rows,
        // so only "strictly greater than whatever existed before" is asserted.
        assertTrue(c.get("sort").getAsInt() > maxSortBefore,
                "new counter's sort (" + c.get("sort").getAsInt() + ") must be greater than every existing sort (" + maxSortBefore + ")");
    }

    @Test
    @Order(6)
    void createIgnoresAnyClientSuppliedSortAndAppendsAtTheEnd() throws Exception {
        // "sort" is deliberately not part of the create contract: a client
        // cannot pick where its new counter lands. Sending one anyway (even a
        // deliberately colliding/low value like 0) must be silently ignored,
        // not honoured and not a 400: the server-computed max(sort)+1 always wins.
        int maxSortBefore = counters.allOrdered().stream().mapToInt(CounterRow::sort).max().orElse(-1);

        HttpResponse<String> res = call("POST", "", "{\"label\":\"Sort spoof\",\"sort\":0}", bearerToken);
        assertEquals(200, res.statusCode(), "an unrecognized/ignored 'sort' key must not cause a 400");

        JsonObject c = JsonParser.parseString(res.body()).getAsJsonObject().getAsJsonObject("counter");
        assertTrue(c.get("sort").getAsInt() > maxSortBefore,
                "a client-supplied sort must be ignored; the server-computed value must still win");
    }

    @Test
    @Order(7)
    void createRejectsMissingOrBlankOrOverlongLabel() throws Exception {
        assertEquals(400, call("POST", "", "{}", bearerToken).statusCode());
        assertEquals(400, call("POST", "", "{\"label\":\"   \"}", bearerToken).statusCode());
        assertEquals(400, call("POST", "", "{\"label\":" + jsonString("x".repeat(201)) + "}", bearerToken).statusCode());
    }

    // -- PATCH: fields + null clears -------------------------------------------

    @Test
    @Order(8)
    void patchUpdatesFieldsAndExplicitNullClearsDescriptionAndIcon() throws Exception {
        String id = createCounter("Board games", "game nights", 3, "fth-anchor");

        HttpResponse<String> renamed = call("PATCH", "/" + id, "{\"label\":\"Board game nights\",\"sort\":9}", bearerToken);
        assertEquals(200, renamed.statusCode());
        JsonObject renamedC = counterOf(renamed);
        assertEquals("Board game nights", renamedC.get("label").getAsString());
        assertEquals(9, renamedC.get("sort").getAsInt());

        HttpResponse<String> cleared = call("PATCH", "/" + id, "{\"description\":null,\"icon\":null}", bearerToken);
        assertEquals(200, cleared.statusCode());
        JsonObject clearedC = counterOf(cleared);
        assertTrue(clearedC.get("description").isJsonNull());
        assertTrue(clearedC.get("icon").isJsonNull());
    }

    @Test
    @Order(9)
    void patchWithNoRecognizedFieldsIsBadRequest() throws Exception {
        String id = createCounter("Empty patch target", null, 0, null);
        assertEquals(400, call("PATCH", "/" + id, "{}", bearerToken).statusCode());
        assertEquals(400, call("PATCH", "/" + id, "{\"nonsense\":1}", bearerToken).statusCode());
    }

    // -- PATCH: delta bump ------------------------------------------------------

    @Test
    @Order(10)
    void deltaBumpsAreRelativeInSql() throws Exception {
        String id = createCounter("Bump target", null, 10, null);

        HttpResponse<String> up = call("PATCH", "/" + id, "{\"delta\":5}", bearerToken);
        assertEquals(200, up.statusCode());
        assertEquals(15, counterOf(up).get("value").getAsInt());

        HttpResponse<String> down = call("PATCH", "/" + id, "{\"delta\":-3}", bearerToken);
        assertEquals(200, down.statusCode());
        assertEquals(12, counterOf(down).get("value").getAsInt());
    }

    @Test
    @Order(11)
    void deltaTogetherWithValueIsBadRequest() throws Exception {
        String id = createCounter("Delta plus value", null, 0, null);
        assertEquals(400, call("PATCH", "/" + id, "{\"delta\":1,\"value\":5}", bearerToken).statusCode());
    }

    @Test
    @Order(12)
    void deltaOfZeroIsBadRequest() throws Exception {
        String id = createCounter("Delta zero", null, 0, null);
        assertEquals(400, call("PATCH", "/" + id, "{\"delta\":0}", bearerToken).statusCode());
    }

    // -- unknown id -------------------------------------------------------------

    @Test
    @Order(13)
    void unknownOrNonUuidIdIs404WithNoExceptionLeaking() throws Exception {
        HttpResponse<String> unknownUuid = call("PATCH", "/00000000-0000-0000-0000-000000000000",
                "{\"label\":\"x\"}", bearerToken);
        assertEquals(404, unknownUuid.statusCode());

        HttpResponse<String> notAUuid = call("PATCH", "/not-a-uuid", "{\"label\":\"x\"}", bearerToken);
        assertEquals(404, notAUuid.statusCode());

        HttpResponse<String> deleteUnknown = call("DELETE", "/00000000-0000-0000-0000-000000000000", null, bearerToken);
        assertEquals(404, deleteUnknown.statusCode());
    }

    // -- DELETE -------------------------------------------------------------

    @Test
    @Order(14)
    void deleteReturnsOkAndTheCounterIsGone() throws Exception {
        String id = createCounter("To delete", null, 0, null);

        HttpResponse<String> res = call("DELETE", "/" + id, null, bearerToken);
        assertEquals(200, res.statusCode());
        assertKeyOrder(res.body(), "ok");
        assertTrue(JsonParser.parseString(res.body()).getAsJsonObject().get("ok").getAsBoolean());

        assertFalse(counters.findById(id).isPresent());
    }

    // -- 503 when the backend is not configured --------------------------------

    @Test
    @Order(15)
    void answersServiceUnavailableWhenDatabaseNotConfigured() throws Exception {
        Backend noDbBackend = new Backend(
                ApiConfig.of(0, null, "some-secret", 1000, 60), null, null,
                new Token("some-secret"), new RateLimiter(1000, 60));
        Javalin noDbApp = ApiServer.create(noDbBackend);
        try {
            noDbApp.start(0);
            String url = "http://localhost:" + noDbApp.port() + "/api/todo/counters";
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10)).GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            assertEquals(503, res.statusCode());
        } finally {
            noDbApp.stop();
        }
    }

    // -- helpers ------------------------------------------------------------

    private String createCounter(String label, String description, int value, String icon) throws Exception {
        StringBuilder json = new StringBuilder("{\"label\":").append(jsonString(label));
        if (description != null) {
            json.append(",\"description\":").append(jsonString(description));
        }
        json.append(",\"value\":").append(value);
        if (icon != null) {
            json.append(",\"icon\":").append(jsonString(icon));
        }
        json.append("}");
        HttpResponse<String> res = call("POST", "", json.toString(), bearerToken);
        assertEquals(200, res.statusCode(), res.body());
        return counterOf(res).get("id").getAsString();
    }

    private static JsonObject counterOf(HttpResponse<String> res) {
        return JsonParser.parseString(res.body()).getAsJsonObject().getAsJsonObject("counter");
    }

    private static String jsonString(String s) {
        return new com.google.gson.Gson().toJson(s);
    }

    private HttpResponse<String> call(String method, String path, String body, String token) throws Exception {
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .method(method, publisher);
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
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
