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
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link TodoApiClient}'s counter methods against an in-process
 * {@link HttpServer} stub (mirrors {@code TodoApiClientTest}'s pattern): no
 * network or deployed API needed. Verifies request shaping (bearer header,
 * null-preserving PATCH bodies, the relative-bump body shape) and response
 * parsing.
 */
class CounterClientTest {

    private HttpServer server;
    private String baseUrl;

    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastMethod = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();
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
            lastBody.set(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }

        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        int status = 200;
        String body;

        if (path.endsWith("/counters") && "GET".equals(method)) {
            body = "{\"counters\":["
                    + "{\"id\":\"c1\",\"label\":\"Total Flights\",\"description\":null,\"value\":3,"
                    + "\"icon\":\"fth-send\",\"sort\":0,\"createdBy\":null,"
                    + "\"createdAt\":\"2026-01-01T00:00:00Z\",\"updatedAt\":\"2026-01-01T00:00:00Z\"},"
                    + "{\"id\":\"c2\",\"label\":\"Total Ships\",\"description\":\"boats\",\"value\":1,"
                    + "\"icon\":\"fth-anchor\",\"sort\":1,\"createdBy\":\"u1\","
                    + "\"createdAt\":\"2026-01-02T00:00:00Z\",\"updatedAt\":\"2026-01-02T00:00:00Z\"}"
                    + "]}";
        } else if (path.endsWith("/counters") && "POST".equals(method)) {
            body = "{\"counter\":{\"id\":\"c9\",\"label\":\"Marathons\",\"description\":null,\"value\":0,"
                    + "\"icon\":null,\"sort\":0,\"createdBy\":\"u1\","
                    + "\"createdAt\":\"2026-01-03T00:00:00Z\",\"updatedAt\":\"2026-01-03T00:00:00Z\"}}";
        } else if (path.contains("/counters/") && "PATCH".equals(method)) {
            body = "{\"counter\":{\"id\":\"c1\",\"label\":\"Total Flights\",\"description\":null,\"value\":8,"
                    + "\"icon\":\"fth-send\",\"sort\":0,\"createdBy\":null,"
                    + "\"createdAt\":\"2026-01-01T00:00:00Z\",\"updatedAt\":\"2026-01-04T00:00:00Z\"}}";
        } else if (path.contains("/counters/") && "DELETE".equals(method)) {
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
    void getCountersSendsBearerAndParsesOrderedList() throws Exception {
        TodoApiClient client = new TodoApiClient(baseUrl, "tok-123");
        List<CounterDto> counters = client.getCounters();

        assertEquals("Bearer tok-123", lastAuth.get(), "counters must be authenticated");
        assertEquals(2, counters.size());
        assertEquals("Total Flights", counters.get(0).label());
        assertEquals(3, counters.get(0).value());
        assertEquals("Total Ships", counters.get(1).label());
        assertEquals("boats", counters.get(1).description());
    }

    @Test
    void createCounterSendsOnlyProvidedFieldsAndParsesResult() throws Exception {
        TodoApiClient client = new TodoApiClient(baseUrl, "tok");
        CounterDto created = client.createCounter("Marathons", null, null, null);

        assertEquals("POST", lastMethod.get());
        assertTrue(lastBody.get().contains("\"label\":\"Marathons\""));
        assertFalse(lastBody.get().contains("\"description\""), "null description should be omitted, not sent as null, on create");
        assertFalse(lastBody.get().contains("\"value\""), "null value should be omitted (API defaults to 0)");
        assertEquals("Marathons", created.label());
        assertEquals(0, created.value());
    }

    @Test
    void updateCounterPatchSerializesNullToClearAField() throws Exception {
        TodoApiClient client = new TodoApiClient(baseUrl, "tok");
        Map<String, Object> patch = new java.util.LinkedHashMap<>();
        patch.put("description", null); // null clears the description
        patch.put("value", 8);
        CounterDto updated = client.updateCounter("c1", patch);

        assertEquals("PATCH", lastMethod.get());
        assertTrue(lastBody.get().contains("\"description\":null"),
                "null values in a counter patch must be serialized, not dropped");
        assertEquals(8, updated.value());
    }

    @Test
    void bumpCounterSendsDeltaBody() throws Exception {
        TodoApiClient client = new TodoApiClient(baseUrl, "tok");
        client.bumpCounter("c1", 5);

        assertEquals("PATCH", lastMethod.get());
        assertTrue(lastBody.get().contains("\"delta\":5"));
    }

    @Test
    void deleteCounterSendsDeleteToTheRightPath() throws Exception {
        TodoApiClient client = new TodoApiClient(baseUrl, "tok");
        client.deleteCounter("c1");

        assertEquals("DELETE", lastMethod.get());
        assertTrue(lastPath.get().endsWith("/counters/c1"));
    }
}
