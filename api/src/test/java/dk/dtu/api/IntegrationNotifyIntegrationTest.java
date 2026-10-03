package dk.dtu.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import dk.dtu.api.auth.IntegrationKeys;
import dk.dtu.api.auth.Token;
import dk.dtu.api.db.Migrations;
import dk.dtu.api.domain.IntegrationService;
import dk.dtu.api.domain.TodoService;
import dk.dtu.api.web.ApiServer;
import dk.dtu.api.web.Backend;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import io.javalin.Javalin;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * The notify hook: after a committed change the API POSTs to TODO_NOTIFY_URL,
 * asynchronously, and the target can never affect the upsert response.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IntegrationNotifyIntegrationTest {

    private static final String KEY = "notify-test-key-not-a-real-secret";
    private static final String NOTIFY_SECRET = "notify-test-secret";

    /** One captured call: the Authorization header, content type and body. */
    private record Call(String auth, String contentType, String body) {
    }

    private EmbeddedPostgres pg;
    private Jdbi jdbi;
    private HttpServer stub;
    private final BlockingQueue<Call> calls = new LinkedBlockingQueue<>();
    private final AtomicInteger stubStatus = new AtomicInteger(200);
    private final AtomicInteger stubDelayMs = new AtomicInteger(0);
    private String stubUrl;
    private String aliceId;
    private Token token;
    private final HttpClient http = HttpClient.newHttpClient();
    private final java.util.List<Javalin> apps = new java.util.ArrayList<>();

    @BeforeAll
    void start() throws IOException {
        pg = EmbeddedPostgres.builder().start();
        Migrations.migrate(pg.getPostgresDatabase());
        jdbi = Jdbi.create(pg.getPostgresDatabase());
        token = new Token("notify-secret");
        jdbi.useHandle(h -> h.createUpdate(
                "INSERT INTO users (email, name, pw_hash) VALUES ('alice-notify@example.com', 'Alice', NULL)")
                .execute());
        aliceId = jdbi.withHandle(h -> h.createQuery(
                "SELECT id FROM users WHERE email = 'alice-notify@example.com'").mapTo(String.class).one());

        stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stub.setExecutor(Executors.newCachedThreadPool());
        stub.createContext("/hook", ex -> {
            byte[] body = ex.getRequestBody().readAllBytes();
            calls.add(new Call(ex.getRequestHeaders().getFirst("Authorization"),
                    ex.getRequestHeaders().getFirst("Content-Type"),
                    new String(body, StandardCharsets.UTF_8)));
            try {
                Thread.sleep(stubDelayMs.get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            ex.sendResponseHeaders(stubStatus.get(), -1);
            ex.close();
        });
        stub.start();
        stubUrl = "http://127.0.0.1:" + stub.getAddress().getPort() + "/hook";
    }

    @AfterAll
    void stop() throws IOException {
        for (Javalin a : apps) {
            a.stop();
        }
        if (stub != null) {
            stub.stop(0);
        }
        if (pg != null) {
            pg.close();
        }
    }

    // -- helpers ---------------------------------------------------------------

    private String serverFor(String notifyUrl, String notifySecret) {
        String keys = "bartender:" + IntegrationKeys.sha256Hex(KEY) + ":" + aliceId;
        ApiConfig config = ApiConfig.of(0, null, "notify-secret")
                .withIntegrationKeys(keys).withNotify(notifyUrl, notifySecret);
        Backend backend = new Backend(config, new TodoService(jdbi), token, null, null, null, null,
                new IntegrationService(jdbi));
        Javalin app = ApiServer.create(backend);
        app.start(0);
        apps.add(app);
        return "http://127.0.0.1:" + app.port();
    }

    private HttpResponse<String> put(String base, String list, String itemsJson) throws Exception {
        String body = "{\"source\":\"bartender\",\"items\":" + itemsJson + "}";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(base + "/api/todo/integrations/lists/" + list + "/items"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + KEY)
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static String items(int n) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < n; i++) {
            sb.append(i == 0 ? "" : ",")
                    .append("{\"externalId\":\"id-").append(i).append("\",\"text\":\"Item ").append(i)
                    .append("\",\"description\":null,\"done\":false}");
        }
        return sb.append("]").toString();
    }

    private void reset(int status, int delayMs) {
        calls.clear();
        stubStatus.set(status);
        stubDelayMs.set(delayMs);
    }

    // -- tests -----------------------------------------------------------------

    @Test
    void firesOnChangeWithTheAgreedBodyAndHeaders() throws Exception {
        reset(200, 0);
        String base = serverFor(stubUrl, NOTIFY_SECRET);
        HttpResponse<String> res = put(base, "Notify-change", items(2));
        assertEquals(200, res.statusCode());
        String listId = JsonParser.parseString(res.body()).getAsJsonObject().get("listId").getAsString();

        Call call = calls.poll(5, TimeUnit.SECONDS);
        assertNotNull(call, "the hook was called");
        assertEquals("Bearer " + NOTIFY_SECRET, call.auth());
        assertEquals("application/json", call.contentType());
        JsonObject b = JsonParser.parseString(call.body()).getAsJsonObject();
        assertEquals(java.util.List.of("event", "source", "listId", "listName", "created", "updated",
                "closed", "createdItems"), new java.util.ArrayList<>(b.keySet()));
        assertEquals("integration.items.changed", b.get("event").getAsString());
        assertEquals("bartender", b.get("source").getAsString());
        assertEquals(listId, b.get("listId").getAsString());
        assertEquals("Notify-change", b.get("listName").getAsString());
        assertEquals(2, b.get("created").getAsInt());
        assertEquals(0, b.get("updated").getAsInt());
        assertEquals(0, b.get("closed").getAsInt());
        var created = b.getAsJsonArray("createdItems");
        assertEquals(2, created.size());
        JsonObject first = created.get(0).getAsJsonObject();
        assertEquals(java.util.List.of("id", "text"), new java.util.ArrayList<>(first.keySet()));
        assertEquals("Item 0", first.get("text").getAsString());
        UUID.fromString(first.get("id").getAsString());
    }

    @Test
    void createdItemsAreCappedAt20ButCountsAreNot() throws Exception {
        reset(200, 0);
        String base = serverFor(stubUrl, NOTIFY_SECRET);
        put(base, "Notify-cap", items(25));
        Call call = calls.poll(5, TimeUnit.SECONDS);
        assertNotNull(call);
        JsonObject b = JsonParser.parseString(call.body()).getAsJsonObject();
        assertEquals(25, b.get("created").getAsInt());
        assertEquals(20, b.getAsJsonArray("createdItems").size());
    }

    @Test
    void doesNotFireOnANoOp() throws Exception {
        reset(200, 0);
        String base = serverFor(stubUrl, NOTIFY_SECRET);
        put(base, "Notify-noop", items(1));
        assertNotNull(calls.poll(5, TimeUnit.SECONDS), "first sync changes something");
        calls.clear();
        HttpResponse<String> again = put(base, "Notify-noop", items(1));
        assertEquals(200, again.statusCode());
        assertNull(calls.poll(1, TimeUnit.SECONDS), "an identical second sync must not notify");
    }

    @Test
    void aTargetThatAnswers500StillLeavesTheUpsertAt200() throws Exception {
        reset(500, 0);
        String base = serverFor(stubUrl, NOTIFY_SECRET);
        HttpResponse<String> res = put(base, "Notify-500", items(1));
        assertEquals(200, res.statusCode());
        assertNotNull(calls.poll(5, TimeUnit.SECONDS), "it was still called");
    }

    @Test
    void aTargetThatHangsDoesNotSlowOrFailTheUpsert() throws Exception {
        reset(200, 6000);
        String base = serverFor(stubUrl, NOTIFY_SECRET);
        long t0 = System.nanoTime();
        HttpResponse<String> res = put(base, "Notify-hang", items(1));
        long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
        assertEquals(200, res.statusCode());
        assertTrue(ms < 2000, "PUT must not wait for the hook, took " + ms + " ms");
        assertNotNull(calls.poll(5, TimeUnit.SECONDS), "the hook was still attempted");
    }

    @Test
    void unsetOrBlankEnvMeansNoCall() throws Exception {
        reset(200, 0);
        assertEquals(200, put(serverFor(null, NOTIFY_SECRET), "Notify-nourl", items(1)).statusCode());
        assertEquals(200, put(serverFor(stubUrl, null), "Notify-nosecret", items(1)).statusCode());
        assertEquals(200, put(serverFor("  ", NOTIFY_SECRET), "Notify-blankurl", items(1)).statusCode());
        assertEquals(200, put(serverFor(stubUrl, "  "), "Notify-blanksecret", items(1)).statusCode());
        assertNull(calls.poll(1, TimeUnit.SECONDS), "no call without both settings");
    }
}
