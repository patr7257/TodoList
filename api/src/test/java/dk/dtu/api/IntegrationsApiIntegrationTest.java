package dk.dtu.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.UUID;

import javax.sql.DataSource;

import dk.dtu.api.auth.IntegrationKeys;
import dk.dtu.api.auth.Token;
import dk.dtu.api.db.Migrations;
import dk.dtu.api.domain.IntegrationService;
import dk.dtu.api.domain.TodoService;
import dk.dtu.api.web.ApiServer;
import dk.dtu.api.web.Backend;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.javalin.Javalin;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * The integration API end to end: AuthFilter's integration-key branch, the
 * controller's validation, and the PUT/GET contract the bartender client is
 * written against, over a real Javalin and an embedded Postgres.
 *
 * <p>The keys here are test literals, not secrets, and only their SHA-256 is
 * configured, exactly as in production.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IntegrationsApiIntegrationTest {

    private static final String KEY = "test-integration-key-not-a-real-secret";
    private static final String ORPHAN_KEY = "test-orphan-key-not-a-real-secret";
    private static final String SECRET = "integrations-secret";

    private EmbeddedPostgres pg;
    private Jdbi jdbi;
    private TodoService todo;
    private Token token;
    private ApiConfig config;
    private Javalin app;
    private String baseUrl;
    private final HttpClient http = HttpClient.newHttpClient();

    private String aliceId;
    private String aliceSession;

    @BeforeAll
    void start() throws IOException {
        pg = EmbeddedPostgres.builder().start();
        DataSource ds = pg.getPostgresDatabase();
        Migrations.migrate(ds);
        jdbi = Jdbi.create(ds);
        todo = new TodoService(jdbi);
        token = new Token(SECRET);

        aliceId = insertUser("Alice", "alice-int");
        aliceSession = token.issue(aliceId);

        String keys = "bartender:" + IntegrationKeys.sha256Hex(KEY) + ":" + aliceId
                + ",orphan:" + IntegrationKeys.sha256Hex(ORPHAN_KEY) + ":" + UUID.randomUUID();
        config = ApiConfig.of(0, null, SECRET).withIntegrationKeys(keys);
        Backend backend = new Backend(config, todo, token, null, null, null, null, new IntegrationService(jdbi));
        app = ApiServer.create(backend);
        app.start(0);
        baseUrl = "http://127.0.0.1:" + app.port();
    }

    @AfterAll
    void stop() throws IOException {
        if (app != null) {
            app.stop();
        }
        if (pg != null) {
            pg.close();
        }
    }

    // -- helpers ---------------------------------------------------------------

    private String insertUser(String name, String emailLocalPart) {
        jdbi.useHandle(h -> h
                .createUpdate("INSERT INTO users (email, name, pw_hash) VALUES (:e, :n, NULL)")
                .bind("e", emailLocalPart + "@example.com")
                .bind("n", name)
                .execute());
        return jdbi.withHandle(h -> h
                .createQuery("SELECT id FROM users WHERE email = :e")
                .bind("e", emailLocalPart + "@example.com")
                .mapTo(String.class)
                .one());
    }

    private HttpResponse<String> send(String base, String method, String path, String body,
                                      String bearer, String cookie) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder()
                .uri(URI.create(base + path))
                .timeout(Duration.ofSeconds(10))
                .method(method, body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
        if (body != null) {
            req.header("Content-Type", "application/json");
        }
        if (bearer != null) {
            req.header("Authorization", "Bearer " + bearer);
        }
        if (cookie != null) {
            req.header("Cookie", "todo_session=" + cookie);
        }
        return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> put(String encodedListName, String body, String bearer) throws Exception {
        return send(baseUrl, "PUT", "/api/todo/integrations/lists/" + encodedListName + "/items", body, bearer, null);
    }

    private HttpResponse<String> get(String encodedListNameAndQuery, String bearer) throws Exception {
        return send(baseUrl, "GET", "/api/todo/integrations/lists/" + encodedListNameAndQuery, null, bearer, null);
    }

    private static JsonObject json(HttpResponse<String> res) {
        return JsonParser.parseString(res.body()).getAsJsonObject();
    }

    private static JsonObject item(String id, String text, String description, boolean done) {
        JsonObject o = new JsonObject();
        o.addProperty("externalId", id);
        o.addProperty("text", text);
        if (description == null) {
            o.add("description", JsonNull.INSTANCE);
        } else {
            o.addProperty("description", description);
        }
        o.addProperty("done", done);
        return o;
    }

    private static String body(String source, JsonObject... items) {
        JsonObject b = new JsonObject();
        b.addProperty("source", source);
        JsonArray arr = new JsonArray();
        for (JsonObject i : items) {
            arr.add(i);
        }
        b.add("items", arr);
        return b.toString();
    }

    private static List<String> keys(JsonObject o) {
        return new ArrayList<>(o.keySet());
    }

    private int listsNamed(String name) {
        return jdbi.withHandle(h -> h
                .createQuery("SELECT COUNT(*) FROM lists WHERE name = :n")
                .bind("n", name)
                .mapTo(Integer.class)
                .one());
    }

    // -- auth ------------------------------------------------------------------

    @Test
    void putWithoutAnyAuthIs401Json() throws Exception {
        HttpResponse<String> res = put("Drinks-noauth", body("bartender"), null);
        assertEquals(401, res.statusCode());
        assertEquals("unauthorized", json(res).get("error").getAsString());
    }

    @Test
    void putWithAWrongKeyIs401() throws Exception {
        assertEquals(401, put("Drinks-wrong", body("bartender"), KEY + "x").statusCode());
    }

    @Test
    void aSessionTokenIsRefusedOnIntegrationPaths() throws Exception {
        assertEquals(401, put("Drinks-session", body("bartender"), aliceSession).statusCode());
        assertEquals(0, listsNamed("Drinks-session"));
    }

    @Test
    void anIntegrationKeyIsRefusedOnSessionRoutes() throws Exception {
        assertEquals(401, send(baseUrl, "GET", "/api/todo/state", null, KEY, null).statusCode());
        assertEquals(401, send(baseUrl, "GET", "/api/todo/state", null, null, KEY).statusCode());
    }

    @Test
    void integrationKeyCannotEscapeThePrefixWithDotSegments() throws Exception {
        // Review Focus 2.
        HttpResponse<String> res = send(baseUrl, "GET", "/api/todo/integrations/../state", null, KEY, null);
        assertNotEquals(200, res.statusCode(), res.body());
    }

    @Test
    void keyMappedToAMissingUserIs401() throws Exception {
        // Review Focus 3.
        assertEquals(401, put("Drinks-orphan", body("bartender", item("gin", "Gin", null, false)), ORPHAN_KEY)
                .statusCode());
        assertEquals(0, listsNamed("Drinks-orphan"));
    }

    @Test
    void unconfiguredBackendAnswers503() throws Exception {
        Javalin bare = ApiServer.create(new Backend(config, null, token));
        bare.start(0);
        try {
            HttpResponse<String> res = send("http://127.0.0.1:" + bare.port(), "PUT",
                    "/api/todo/integrations/lists/Drinks/items", body("bartender"), KEY, null);
            assertEquals(503, res.statusCode());
        } finally {
            bare.stop();
        }
    }

    // -- contract --------------------------------------------------------------

    @Test
    void putCreatesThenGetReadsBackInTheContractShape() throws Exception {
        HttpResponse<String> res = put("Drinks-http", body("bartender",
                item("gin", "Gin", "running low", false), item("tonic", "Tonic", null, false)), KEY);
        assertEquals(200, res.statusCode(), res.body());
        JsonObject out = json(res);
        assertEquals(List.of("listId", "created", "updated", "closed"), keys(out));
        assertTrue(out.get("listId").getAsJsonPrimitive().isString());
        assertEquals(2, out.get("created").getAsInt());
        assertEquals(0, out.get("updated").getAsInt());
        assertEquals(0, out.get("closed").getAsInt());

        HttpResponse<String> read = get("Drinks-http/items?source=bartender", KEY);
        assertEquals(200, read.statusCode(), read.body());
        JsonObject r = json(read);
        assertEquals(List.of("listId", "items"), keys(r));
        assertEquals(out.get("listId").getAsString(), r.get("listId").getAsString());
        JsonArray items = r.getAsJsonArray("items");
        assertEquals(2, items.size());
        JsonObject first = items.get(0).getAsJsonObject();
        assertEquals(List.of("externalId", "text", "done"), keys(first));
        assertEquals("gin", first.get("externalId").getAsString());
        assertEquals("Gin", first.get("text").getAsString());
        assertEquals(false, first.get("done").getAsBoolean());
    }

    @Test
    void putClosesAndReopensOverHttp() throws Exception {
        put("Drinks-http-done", body("bartender", item("gin", "Gin", null, false)), KEY);
        JsonObject closed = json(put("Drinks-http-done", body("bartender", item("gin", "Gin", null, true)), KEY));
        assertEquals(1, closed.get("closed").getAsInt());
        JsonObject read = json(get("Drinks-http-done/items?source=bartender", KEY));
        assertTrue(read.getAsJsonArray("items").get(0).getAsJsonObject().get("done").getAsBoolean());
        JsonObject reopened = json(put("Drinks-http-done", body("bartender", item("gin", "Gin", null, false)), KEY));
        assertEquals(1, reopened.get("updated").getAsInt());
    }

    @Test
    void syncedItemsAreOrdinaryItemsInTheOwnersState() throws Exception {
        String listId = json(put("Drinks-state", body("bartender", item("lime", "Lime", null, false)), KEY))
                .get("listId").getAsString();
        HttpResponse<String> state = send(baseUrl, "GET", "/api/todo/state", null, aliceSession, null);
        assertEquals(200, state.statusCode());
        boolean seen = false;
        for (JsonElement el : json(state).getAsJsonArray("lists")) {
            JsonObject list = el.getAsJsonObject();
            if (list.get("id").getAsString().equals(listId)) {
                JsonObject only = list.getAsJsonArray("items").get(0).getAsJsonObject();
                seen = "Lime".equals(only.get("text").getAsString());
            }
        }
        assertTrue(seen, "the synced list and item are visible in /state");
    }

    @Test
    void getOfAnUnknownListAnswersNullListIdAndNoItems() throws Exception {
        HttpResponse<String> res = get("Drinks-nope/items?source=bartender", KEY);
        assertEquals(200, res.statusCode());
        JsonObject r = json(res);
        assertTrue(r.get("listId").isJsonNull());
        assertEquals(0, r.getAsJsonArray("items").size());
    }

    @Test
    void getWithoutOrWithABlankSourceIs400() throws Exception {
        assertEquals(400, get("Drinks-http/items", KEY).statusCode());
        assertEquals(400, get("Drinks-http/items?source=", KEY).statusCode());
    }

    @Test
    void encodedListNameIsDecodedAndMatchedExactly() throws Exception {
        assertEquals(200, put("Bar%20Stash", body("bartender", item("gin", "Gin", null, false)), KEY).statusCode());
        assertEquals(1, listsNamed("Bar Stash"));
    }

    @Test
    void listNameWithSurroundingWhitespaceIs400() throws Exception {
        assertEquals(400, put("Drinks-ws%20", body("bartender"), KEY).statusCode());
        assertEquals(400, put("%20Drinks-ws", body("bartender"), KEY).statusCode());
    }

    @Test
    void exactly200ItemsIsAcceptedAndAnEmptyBatchJustEnsuresTheList() throws Exception {
        JsonObject[] many = new JsonObject[200];
        for (int i = 0; i < many.length; i++) {
            many[i] = item("id-" + i, "Item " + i, null, false);
        }
        JsonObject out = json(put("Drinks-200", body("bartender", many), KEY));
        assertEquals(200, out.get("created").getAsInt());

        HttpResponse<String> empty = put("Drinks-empty", body("bartender"), KEY);
        assertEquals(200, empty.statusCode());
        assertEquals(0, json(empty).get("created").getAsInt());
        assertEquals(1, listsNamed("Drinks-empty"));
    }

    @Test
    void invalidBodiesAre400AndWriteNothing() throws Exception {
        JsonObject[] tooMany = new JsonObject[201];
        for (int i = 0; i < tooMany.length; i++) {
            tooMany[i] = item("id-" + i, "Item " + i, null, false);
        }
        JsonObject noDone = item("gin", "Gin", null, false);
        noDone.remove("done");
        JsonObject stringDone = item("gin", "Gin", null, false);
        stringDone.addProperty("done", "true");
        JsonObject numberDescription = item("gin", "Gin", null, false);
        numberDescription.addProperty("description", 5);

        List<String> bad = List.of(
                "not json",
                "{\"items\":[]}",
                body(""),
                body("   "),
                body("x".repeat(65)),
                "{\"source\":\"bartender\"}",
                "{\"source\":\"bartender\",\"items\":{}}",
                "{\"source\":\"bartender\",\"items\":[\"gin\"]}",
                body("bartender", tooMany),
                body("bartender", item("", "Gin", null, false)),
                body("bartender", item("x".repeat(201), "Gin", null, false)),
                body("bartender", item("gin", "   ", null, false)),
                body("bartender", item("gin", "x".repeat(1001), null, false)),
                body("bartender", numberDescription),
                body("bartender", item("gin", "Gin", "x".repeat(4001), false)),
                body("bartender", noDone),
                body("bartender", stringDone),
                // Review Focus 1: duplicate externalId in one batch.
                body("bartender", item("gin", "Gin", null, false), item("gin", "Gin again", null, false)),
                // Valid first item, invalid second: nothing may be written.
                body("bartender", item("ok", "Ok", null, false), item("", "Bad", null, false)));
        for (String b : bad) {
            HttpResponse<String> res = put("Drinks-invalid", b, KEY);
            assertEquals(400, res.statusCode(), "body: " + b);
            assertTrue(json(res).has("error"), "400 is JSON for body: " + b);
        }
        assertEquals(0, listsNamed("Drinks-invalid"));
    }
}
