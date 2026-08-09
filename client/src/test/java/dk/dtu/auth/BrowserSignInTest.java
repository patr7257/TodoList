package dk.dtu.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Drives the real loopback listener over HTTP, the same way the browser would.
 * No browser is ever opened here: {@link BrowserSignIn#start} only binds, and
 * opening one is a separate call the tests never make.
 *
 * <p>The failure modes matter more than the happy path, because they are what a
 * hostile or merely careless local process can reach: a wrong or missing state
 * must not complete, must not cancel, and must not take the listener down.
 */
public class BrowserSignInTest {

    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private BrowserSignIn signIn;

    @AfterEach
    void stopListener() {
        if (signIn != null) {
            signIn.cancel();
        }
    }

    // -- helpers ---------------------------------------------------------------

    private BrowserSignIn started() throws IOException {
        signIn = BrowserSignIn.start("https://example.test", TEST_TIMEOUT);
        return signIn;
    }

    private HttpResponse<String> get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String callbackUrl(String state, String code) {
        return "http://127.0.0.1:" + signIn.port() + "/callback"
                + "?state=" + URLEncoder.encode(state, StandardCharsets.UTF_8)
                + "&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8);
    }

    /** The state the listener is waiting for, read back out of its own sign-in URL. */
    private String expectedState() {
        String url = signIn.signInUrl();
        int start = url.indexOf("state=") + "state=".length();
        int end = url.indexOf('&', start);
        String raw = end < 0 ? url.substring(start) : url.substring(start, end);
        return java.net.URLDecoder.decode(raw, StandardCharsets.UTF_8);
    }

    // -- the URL handed to the browser ----------------------------------------

    @Test
    void signInUrlCarriesTheFrozenQueryContract() throws Exception {
        started();
        String url = signIn.signInUrl();

        assertTrue(url.startsWith("https://example.test/todo/login?desktop=1&"), url);
        assertTrue(url.contains("&port=" + signIn.port()), url);
        assertTrue(url.contains("&state="), url);
        assertTrue(url.contains("&challenge="), url);
        // The challenge on the wire must be the one derived from OUR verifier.
        assertTrue(url.contains("&challenge=" + Pkce.challengeFor(signIn.verifier())), url);
    }

    @Test
    void signInUrlOmitsThePortWhenThereIsNoListener() {
        String url = BrowserSignIn.signInUrl("https://example.test/", null, "st", "ch");
        assertEquals("https://example.test/todo/login?desktop=1&state=st&challenge=ch", url);
    }

    // -- the happy path --------------------------------------------------------

    @Test
    void correctStateCompletesWithTheCodeAndServesAnHtmlPage() throws Exception {
        started();

        HttpResponse<String> response = get(callbackUrl(expectedState(), "CODE-1"));

        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("text/html"),
                "the browser must get a real page back");
        assertTrue(response.body().contains("logget ind"), response.body());

        assertEquals("CODE-1", signIn.code().get(5, TimeUnit.SECONDS));
    }

    @Test
    void listenerIsStoppedOnceTheCodeArrived() throws Exception {
        started();
        get(callbackUrl(expectedState(), "CODE-1"));
        signIn.code().get(5, TimeUnit.SECONDS);

        // Completion happens only after the server is stopped, so by now the port
        // must be closed: a follow-up callback cannot even connect, which is also
        // what makes a replayed code useless.
        assertThrows(IOException.class, () -> get(callbackUrl(expectedState(), "CODE-2")));
        assertEquals("CODE-1", signIn.code().get(5, TimeUnit.SECONDS),
                "the first code must stand");
    }

    // -- the failure modes -----------------------------------------------------

    @Test
    void wrongStateIsRejectedWithoutCompletingOrStoppingTheListener() throws Exception {
        started();

        HttpResponse<String> response = get(callbackUrl("not-the-state", "CODE-X"));

        assertEquals(400, response.statusCode());
        assertFalse(signIn.code().isDone(), "a foreign callback must not complete the sign in");

        // Still listening, so a real sign in that arrives afterwards still works.
        HttpResponse<String> real = get(callbackUrl(expectedState(), "CODE-OK"));
        assertEquals(200, real.statusCode());
        assertEquals("CODE-OK", signIn.code().get(5, TimeUnit.SECONDS));
    }

    @Test
    void missingStateIsRejectedWithoutCompletingOrStoppingTheListener() throws Exception {
        started();

        HttpResponse<String> response =
                get("http://127.0.0.1:" + signIn.port() + "/callback?code=CODE-X");

        assertEquals(400, response.statusCode());
        assertFalse(signIn.code().isDone());

        HttpResponse<String> real = get(callbackUrl(expectedState(), "CODE-OK"));
        assertEquals(200, real.statusCode());
        assertEquals("CODE-OK", signIn.code().get(5, TimeUnit.SECONDS));
    }

    @Test
    void missingCodeIsRejectedWithoutCompleting() throws Exception {
        started();

        HttpResponse<String> response = get("http://127.0.0.1:" + signIn.port()
                + "/callback?state=" + URLEncoder.encode(expectedState(), StandardCharsets.UTF_8));

        assertEquals(400, response.statusCode());
        assertFalse(signIn.code().isDone());
    }

    @Test
    void faviconAndOtherPathsGet404WithoutCompleting() throws Exception {
        started();

        assertEquals(404, get("http://127.0.0.1:" + signIn.port() + "/favicon.ico").statusCode());
        assertEquals(404, get("http://127.0.0.1:" + signIn.port() + "/").statusCode());
        assertFalse(signIn.code().isDone(), "only /callback may ever complete the sign in");
    }

    @Test
    void cancelStopsTheListenerAndFailsTheFuture() throws Exception {
        started();
        int port = signIn.port();

        signIn.cancel();

        assertTrue(signIn.code().isCompletedExceptionally());
        assertThrows(IOException.class, () -> get("http://127.0.0.1:" + port + "/callback"));
    }

    @Test
    void timeoutFailsTheFutureWithoutBlockingTheCaller() throws Exception {
        signIn = BrowserSignIn.start("https://example.test", Duration.ofMillis(150));

        java.util.concurrent.ExecutionException failure = assertThrows(
                java.util.concurrent.ExecutionException.class,
                () -> signIn.code().get(5, TimeUnit.SECONDS));
        assertInstanceOf(java.util.concurrent.TimeoutException.class, failure.getCause());
    }

    @Test
    void bindsToAnEphemeralPortReachableOnLoopback() throws Exception {
        started();
        // Port 0 was requested, so the OS picked a free one: "address already in
        // use" cannot happen, and two attempts never collide.
        assertTrue(signIn.port() > 0);
        assertEquals(404, get("http://127.0.0.1:" + signIn.port() + "/nope").statusCode());
    }
}
