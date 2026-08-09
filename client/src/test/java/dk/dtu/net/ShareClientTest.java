package dk.dtu.net;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link TodoApiClient}'s share-link methods against an in-process
 * {@link HttpServer} stub (mirrors {@code CounterClientTest}'s pattern): no
 * network or deployed API needed. Verifies request shaping (bearer header,
 * paths) and response parsing, including that null {@code label} /
 * {@code lastViewedAt} / {@code expiresAt} parse cleanly and that an unknown
 * extra key in the JSON does not break parsing.
 */
class ShareClientTest {

    private HttpServer server;
    private String baseUrl;

    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastMethod = new AtomicReference<>();
    private final AtomicReference<String> lastAuth = new AtomicReference<>();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/todo", this::dispatch);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void dispatch(HttpExchange ex) throws java.io.IOException {
        lastPath.set(ex.getRequestURI().getPath());
        lastMethod.set(ex.getRequestMethod());
        lastAuth.set(ex.getRequestHeaders().getFirst("Authorization"));
        try (InputStream in = ex.getRequestBody()) {
            in.readAllBytes();
        }

        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        int status = 200;
        String body;

        if (path.endsWith("/shares") && "GET".equals(method)) {
            // Includes an unrecognized extra key ("note") that must not break parsing,
            // and one entry with null label/expiresAt/lastViewedAt.
            body = "{\"shares\":["
                    + "{\"id\":\"s1\",\"label\":\"sent to mum\",\"url\":\"https://patrickrobel.dk/s/Kf3xQ9mZ\","
                    + "\"token\":\"Kf3xQ9mZ\",\"createdAt\":\"2026-08-09T10:11:12Z\",\"expiresAt\":null,"
                    + "\"lastViewedAt\":\"2026-08-09T12:00:00Z\",\"viewCount\":3,\"note\":\"unexpected extra field\"},"
                    + "{\"id\":\"s2\",\"label\":null,\"url\":\"https://patrickrobel.dk/s/abc123\","
                    + "\"token\":\"abc123\",\"createdAt\":\"2026-08-01T00:00:00Z\",\"expiresAt\":null,"
                    + "\"lastViewedAt\":null,\"viewCount\":0}"
                    + "]}";
        } else if (path.endsWith("/shares") && "POST".equals(method)) {
            body = "{\"share\":{\"id\":\"s9\",\"label\":\"new link\",\"url\":\"https://patrickrobel.dk/s/newtok\","
                    + "\"token\":\"newtok\",\"createdAt\":\"2026-08-09T13:00:00Z\",\"expiresAt\":null,"
                    + "\"lastViewedAt\":null,\"viewCount\":0}}";
        } else if (path.contains("/shares/") && "DELETE".equals(method)) {
            body = "{\"ok\":true}";
        } else {
            body = "{\"ok\":true}";
        }

        byte[] out = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(status, out.length);
        ex.getResponseBody().write(out);
        ex.close();
    }

    @Test
    void getSharesSendsBearerAndParsesEveryField() throws Exception {
        TodoApiClient client = new TodoApiClient(baseUrl, "tok-123");
        List<ShareDto> shares = client.getShares("l1");

        assertEquals("Bearer tok-123", lastAuth.get(), "shares must be authenticated");
        assertEquals(2, shares.size());

        ShareDto first = shares.get(0);
        assertEquals("s1", first.id());
        assertEquals("sent to mum", first.label());
        assertEquals("https://patrickrobel.dk/s/Kf3xQ9mZ", first.url());
        assertEquals("Kf3xQ9mZ", first.token());
        assertEquals("2026-08-09T10:11:12Z", first.createdAt());
        assertNull(first.expiresAt());
        assertEquals("2026-08-09T12:00:00Z", first.lastViewedAt());
        assertEquals(3, first.viewCount());
    }

    @Test
    void getSharesToleratesNullLabelAndLastViewedAt() throws Exception {
        TodoApiClient client = new TodoApiClient(baseUrl, "tok-123");
        List<ShareDto> shares = client.getShares("l1");

        ShareDto second = shares.get(1);
        assertNull(second.label());
        assertNull(second.lastViewedAt());
        assertNull(second.expiresAt());
        assertEquals(0, second.viewCount());
    }

    @Test
    void getSharesToleratesAnUnknownExtraJsonKey() throws Exception {
        // The stub's first entry carries an extra "note" key not present on
        // ShareDto; parsing must not throw and must still bind every known field.
        TodoApiClient client = new TodoApiClient(baseUrl, "tok-123");
        List<ShareDto> shares = client.getShares("l1");

        assertEquals("s1", shares.get(0).id());
    }

    @Test
    void createShareSendsToTheRightPathAndParsesResult() throws Exception {
        TodoApiClient client = new TodoApiClient(baseUrl, "tok");
        ShareDto created = client.createShare("l1", "new link");

        assertEquals("POST", lastMethod.get());
        assertTrue(lastPath.get().endsWith("/lists/l1/shares"));
        assertEquals("new link", created.label());
        assertEquals("https://patrickrobel.dk/s/newtok", created.url());
    }

    @Test
    void revokeShareSendsDeleteToTheRightPath() throws Exception {
        TodoApiClient client = new TodoApiClient(baseUrl, "tok");
        client.revokeShare("l1", "s1");

        assertEquals("DELETE", lastMethod.get());
        assertTrue(lastPath.get().endsWith("/lists/l1/shares/s1"));
    }
}
