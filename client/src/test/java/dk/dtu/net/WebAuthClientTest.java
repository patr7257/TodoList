package dk.dtu.net;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises {@link WebAuthClient} against an in-process {@link HttpServer} stub,
 * in the same style as {@link TodoApiClientTest}, so no website deployment is
 * needed. The website half of this contract is being built separately, so what
 * is pinned here is the request shape it will receive and the response shape it
 * promises to send back.
 */
public class WebAuthClientTest {

    private HttpServer server;
    private String baseUrl;

    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastMethod = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastContentType = new AtomicReference<>();
    private final AtomicInteger nextStatus = new AtomicInteger(200);
    private final AtomicReference<String> nextBody = new AtomicReference<>(
            "{\"ok\":true,\"token\":\"tok-42\","
                    + "\"user\":{\"id\":\"u1\",\"name\":\"Alice\",\"email\":\"a@x.dk\"}}");

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::dispatch);
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
        lastContentType.set(ex.getRequestHeaders().getFirst("Content-Type"));
        try (InputStream in = ex.getRequestBody()) {
            lastBody.set(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }

        byte[] out = nextBody.get().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(nextStatus.get(), out.length);
        ex.getResponseBody().write(out);
        ex.close();
    }

    @Test
    void exchangePostsTheCodeAndVerifierToTheFrozenRoute() throws Exception {
        new WebAuthClient(baseUrl).exchange("ABCD2345", "the-verifier");

        assertEquals("/api/todo/auth/desktop-exchange", lastPath.get());
        assertEquals("POST", lastMethod.get());
        assertEquals("application/json", lastContentType.get());
        assertEquals("{\"code\":\"ABCD2345\",\"verifier\":\"the-verifier\"}", lastBody.get());
    }

    @Test
    void exchangeParsesTokenAndUser() throws Exception {
        WebAuthClient.DesktopExchange res =
                new WebAuthClient(baseUrl).exchange("ABCD2345", "the-verifier");

        assertTrue(res.ok());
        assertEquals("tok-42", res.token());
        assertEquals("u1", res.user().id());
        assertEquals("Alice", res.user().name());
        assertEquals("a@x.dk", res.user().email());
    }

    @Test
    void nonSuccessMapsToApiExceptionCarryingStatusAndBody() {
        nextStatus.set(400);
        nextBody.set("{\"error\":\"invalid_code\"}");

        WebAuthClient client = new WebAuthClient(baseUrl);
        ApiException ex = assertThrows(ApiException.class,
                () -> client.exchange("BADCODE1", "the-verifier"));

        assertEquals(400, ex.status());
        assertTrue(ex.body().contains("invalid_code"));
    }

    @Test
    void a401MapsToAnUnauthorizedApiException() {
        nextStatus.set(401);
        nextBody.set("{\"error\":\"expired\"}");

        WebAuthClient client = new WebAuthClient(baseUrl);
        ApiException ex = assertThrows(ApiException.class,
                () -> client.exchange("ABCD2345", "the-verifier"));

        assertTrue(ex.isUnauthorized());
    }

    @Test
    void a200WithoutATokenIsStillAFailure() {
        // A 200 body that says ok:false (or carries no token) must never be
        // mistaken for a session, or the client would sign in with a null token.
        nextBody.set("{\"ok\":false}");

        WebAuthClient client = new WebAuthClient(baseUrl);
        ApiException ex = assertThrows(ApiException.class,
                () -> client.exchange("ABCD2345", "the-verifier"));
        assertEquals(401, ex.status());
    }

    @Test
    void originIsNormalizedAndDefaulted() {
        assertEquals("https://patrickrobel.dk", new WebAuthClient("https://patrickrobel.dk/").origin());
        assertEquals("https://patrickrobel.dk", new WebAuthClient("  https://patrickrobel.dk  ").origin());
        assertEquals(dk.dtu.shared.Config.DEFAULT_WEB_BASE_URL, new WebAuthClient("").origin());
        assertEquals(dk.dtu.shared.Config.DEFAULT_WEB_BASE_URL, new WebAuthClient(null).origin());
    }
}
