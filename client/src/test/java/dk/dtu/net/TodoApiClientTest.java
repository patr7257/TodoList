package dk.dtu.net;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dk.dtu.net.ApiModels.ItemDto;
import dk.dtu.net.ApiModels.ListDto;
import dk.dtu.net.ApiModels.LoginResponse;
import dk.dtu.net.ApiModels.StateResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises {@link TodoApiClient} against an in-process {@link HttpServer} stub,
 * so no network or deployed API is needed. Verifies request shaping (auth
 * header, always-present item keys), response parsing, and error mapping.
 */
public class TodoApiClientTest {

    private HttpServer server;
    private String baseUrl;

    // Captured details of the most recent request the stub handled.
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
        int status = 200;
        String body;

        if (path.contains("needsauth")) {
            status = 401;
            body = "{\"error\":\"unauthorized\"}";
        } else if (path.endsWith("/login")) {
            body = "{\"ok\":true,\"user\":{\"id\":\"u1\",\"name\":\"Alice\",\"email\":\"a@x.dk\"},\"token\":\"tok-123\"}";
        } else if (path.endsWith("/state")) {
            body = "{\"user\":{\"id\":\"u1\",\"name\":\"Alice\",\"email\":\"a@x.dk\"},"
                    + "\"users\":[{\"id\":\"u1\",\"name\":\"Alice\"},{\"id\":\"u2\",\"name\":\"Bob\"}],"
                    + "\"lists\":[{\"id\":\"l1\",\"name\":\"Inbox\",\"sort\":0,\"owner\":null,"
                    + "\"priority\":null,\"year\":null,\"location\":null,\"description\":null,"
                    + "\"taskColumnsJson\":null,\"completionPercentage\":50,"
                    + "\"items\":[{\"id\":\"i1\",\"listId\":\"l1\",\"text\":\"Buy milk\",\"description\":null,"
                    + "\"done\":false,\"status\":\"NOT_STARTED\",\"priority\":null,\"dueAt\":null,"
                    + "\"location\":null,\"assigneeId\":\"u2\",\"sort\":0,\"year\":null,\"assigneeName\":\"Bob\"}]}]}";
        } else if (path.endsWith("/items") && ex.getRequestMethod().equals("POST")) {
            body = "{\"item\":{\"id\":\"i9\",\"listId\":\"l1\",\"text\":\"New\",\"status\":\"NOT_STARTED\","
                    + "\"done\":false,\"sort\":0,\"assigneeName\":null}}";
        } else if (path.contains("/items/") && ex.getRequestMethod().equals("PATCH")) {
            body = "{\"item\":{\"id\":\"i1\",\"listId\":\"l1\",\"text\":\"Buy milk\",\"status\":\"DONE\","
                    + "\"done\":true,\"sort\":0,\"assigneeName\":\"Bob\"}}";
        } else if (path.endsWith("/lists") && ex.getRequestMethod().equals("POST")) {
            body = "{\"list\":{\"id\":\"l9\",\"name\":\"New\",\"sort\":0}}";
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
    void loginParsesResponseAndAppliesToken() throws Exception {
        TodoApiClient client = new TodoApiClient(baseUrl, null);
        LoginResponse res = client.login("a@x.dk", "pw");

        assertTrue(res.ok());
        assertEquals("Alice", res.user().name());
        assertEquals("tok-123", res.token());
        assertEquals("tok-123", client.token(), "token should be applied to the client");
        assertTrue(lastBody.get().contains("\"email\""));
        assertTrue(lastBody.get().contains("\"password\""));
    }

    @Test
    void getStateSendsBearerAndParsesNestedItems() throws Exception {
        TodoApiClient client = new TodoApiClient(baseUrl, "tok-123");
        StateResponse state = client.getState();

        assertEquals("Bearer tok-123", lastAuth.get(), "state must be authenticated");
        assertEquals(2, state.users().size());
        assertEquals(1, state.lists().size());
        ListDto list = state.lists().get(0);
        assertEquals("Inbox", list.name());
        assertEquals(50, list.completionPercentage());
        assertEquals(1, list.items().size());
        ItemDto item = list.items().get(0);
        assertEquals("Buy milk", item.text());
        assertEquals("Bob", item.assigneeName());
    }

    @Test
    void createItemAlwaysSendsDescriptionLocationAndAssigneeKeys() throws Exception {
        TodoApiClient client = new TodoApiClient(baseUrl, "tok");
        client.createItem("l1", "New", null, null, null, null, null, null);

        String body = lastBody.get();
        assertTrue(body.contains("\"description\""), "description key must be present even when null");
        assertTrue(body.contains("\"location\""), "location key must be present even when null");
        assertTrue(body.contains("\"assigneeId\""), "assigneeId key must be present even when null");
        // priority/dueAt/status are omitted when null (the API defaults them).
        assertFalse(body.contains("\"priority\""));
        assertFalse(body.contains("\"status\""));
    }

    @Test
    void updateItemReturnsParsedItem() throws Exception {
        TodoApiClient client = new TodoApiClient(baseUrl, "tok");
        java.util.Map<String, Object> patch = new java.util.LinkedHashMap<>();
        patch.put("assigneeId", null); // null must be serialized to clear the field
        ItemDto item = client.updateItem("i1", patch);

        assertEquals("DONE", item.status());
        assertTrue(lastBody.get().contains("\"assigneeId\":null"),
                "null values in a patch must be serialized, not dropped");
        assertEquals("PATCH", lastMethod.get());
    }

    @Test
    void nonSuccessMapsToApiExceptionWith401Flag() {
        TodoApiClient client = new TodoApiClient(baseUrl, "tok");
        ApiException ex = assertThrows(ApiException.class,
                () -> client.updateItem("needsauth", new java.util.LinkedHashMap<>()));
        assertEquals(401, ex.status());
        assertTrue(ex.isUnauthorized());
    }

    @Test
    void pingReturnsTrueWhenServerAnswers() {
        assertTrue(new TodoApiClient(baseUrl, null).ping());
    }

    @Test
    void pingReturnsFalseForUnreachableHost() {
        // Port 1 is not listening; connect should fail fast enough for the test.
        assertFalse(new TodoApiClient("http://127.0.0.1:1", null).ping());
    }
}
