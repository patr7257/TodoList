package dk.dtu.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Browser-mediated sign in for the desktop client (issue #51), the native-app
 * half of RFC 8252: the browser owns the credentials (passkey or magic link) and
 * hands a one-time code back to a loopback listener this class owns, which is
 * then exchanged for a session token over HTTPS by
 * {@link dk.dtu.net.WebAuthClient}. The desktop app never sees a password.
 *
 * <p>Security properties this class is responsible for:
 * <ul>
 *   <li>The listener binds to the LOOPBACK address on an EPHEMERAL port, so it
 *       is unreachable from the LAN and can never collide with another process's
 *       port.</li>
 *   <li>It binds BEFORE the browser is opened, so no other local process can
 *       claim the port first and impersonate the callback.</li>
 *   <li>The callback's {@code state} is compared in constant time, and a wrong
 *       or missing state answers 400 WITHOUT completing or stopping anything, so
 *       a stray browser prefetch cannot cancel a real sign in.</li>
 *   <li>Only the first valid callback is honoured.</li>
 * </ul>
 *
 * <p>No JavaFX here, so it is unit-testable by driving the listener over HTTP.
 * Opening the browser is a separate, explicitly called step
 * ({@link #openBrowser()}), which is why tests never open one.
 */
public final class BrowserSignIn {

    /**
     * How long the listener waits for the callback. Matches the magic-link TTL:
     * that path routes through an email inbox, so a shorter window (5 minutes,
     * say) would fail routinely for a perfectly normal user.
     */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(15);

    private static final String CALLBACK_PATH = "/callback";

    private final HttpServer server;
    private final Pkce.Handshake handshake;
    private final String signInUrl;
    private final int port;

    private final CompletableFuture<String> code = new CompletableFuture<>();
    // Only the first valid callback wins; later ones are answered but ignored.
    private final AtomicBoolean claimed = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    // -- lifecycle -------------------------------------------------------------

    /** Binds the loopback listener and starts waiting, with the default timeout. */
    public static BrowserSignIn start(String webOrigin) throws IOException {
        return start(webOrigin, DEFAULT_TIMEOUT);
    }

    /** Binds the loopback listener and starts waiting. The browser is NOT opened here. */
    public static BrowserSignIn start(String webOrigin, Duration timeout) throws IOException {
        // Port 0 means "any free ephemeral port", so "address already in use"
        // cannot happen; the real port is read back below.
        HttpServer server = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        return new BrowserSignIn(server, webOrigin, timeout);
    }

    private BrowserSignIn(HttpServer server, String webOrigin, Duration timeout) {
        this.server = server;
        this.handshake = Pkce.newHandshake();
        this.port = server.getAddress().getPort();
        this.signInUrl = signInUrl(webOrigin, port, handshake.state(), handshake.challenge());

        // Exactly one context. Anything else, including the browser's automatic
        // /favicon.ico, falls through to the server's own silent 404.
        server.createContext(CALLBACK_PATH, this::handleCallback);
        server.setExecutor(null);
        server.start();

        code.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
        // Covers every ending (success, timeout, cancel): the listener goes away.
        code.whenComplete((value, error) -> stopServer());
    }

    /**
     * Opens the sign-in URL in the system browser. Best effort: returns false
     * (and never throws) when there is no browser to open, so the caller can
     * fall back to showing the URL for the user to open by hand.
     */
    public boolean openBrowser() {
        return Browsers.open(signInUrl);
    }

    /** Stops the listener and discards the pending attempt. Safe to call twice. */
    public void cancel() {
        code.cancel(false);
        stopServer();
    }

    // -- accessors -------------------------------------------------------------

    /** The one-time code from the browser callback, or a failed future on timeout/cancel. */
    public CompletableFuture<String> code() {
        return code;
    }

    /** The URL to open (or to show the user when the browser did not open). */
    public String signInUrl() {
        return signInUrl;
    }

    /** The PKCE verifier this attempt must present when exchanging its code. */
    public String verifier() {
        return handshake.verifier();
    }

    /** The ephemeral loopback port the listener actually bound to. */
    public int port() {
        return port;
    }

    // -- URL building ----------------------------------------------------------

    /**
     * Builds the browser URL for one sign-in attempt:
     * {@code <webOrigin>/todo/login?desktop=1&port=<port>&state=<state>&challenge=<challenge>}.
     *
     * <p>{@code port} may be null, which omits the parameter. That is the
     * degraded case where the loopback listener could not bind at all: the
     * browser then has nowhere to call back to, so the website should show the
     * typeable code and the user finishes in the app's fallback field.
     */
    public static String signInUrl(String webOrigin, Integer port, String state, String challenge) {
        StringBuilder url = new StringBuilder(normalizeOrigin(webOrigin));
        url.append("/todo/login?desktop=1");
        if (port != null) {
            url.append("&port=").append(port);
        }
        url.append("&state=").append(enc(state));
        url.append("&challenge=").append(enc(challenge));
        return url.toString();
    }

    /** Trims the web origin and strips every trailing slash, defaulting when blank. */
    public static String normalizeOrigin(String webOrigin) {
        String u = (webOrigin == null || webOrigin.isBlank())
                ? dk.dtu.shared.Config.DEFAULT_WEB_BASE_URL
                : webOrigin.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    private static String enc(String value) {
        // base64url survives this untouched, but encode defensively anyway.
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    // -- the callback ----------------------------------------------------------

    private void handleCallback(HttpExchange exchange) throws IOException {
        try {
            Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
            String incomingState = params.get("state");
            String incomingCode = params.get("code");

            if (!stateMatches(incomingState) || incomingCode == null || incomingCode.isBlank()) {
                // Deliberately NOT fatal: keep listening, so a stray prefetch or
                // a foreign process cannot cancel a real sign in by guessing.
                respond(exchange, 400, "text/plain; charset=utf-8", REJECTED_BODY);
                return;
            }
            if (!claimed.compareAndSet(false, true)) {
                respond(exchange, 400, "text/plain; charset=utf-8", REJECTED_BODY);
                return;
            }

            respond(exchange, 200, "text/html; charset=utf-8", SUCCESS_PAGE);
            finishAfterResponse(incomingCode);
        } finally {
            exchange.close();
        }
    }

    private boolean stateMatches(String incomingState) {
        if (incomingState == null) {
            return false;
        }
        return MessageDigest.isEqual(
                incomingState.getBytes(StandardCharsets.UTF_8),
                handshake.state().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Stops the listener and only then completes the future, off the handler
     * thread. Stopping first means a caller that sees the code can rely on the
     * port already being closed; doing it on another thread means the response
     * is fully written before the connection is torn down.
     */
    private void finishAfterResponse(String value) {
        Thread stopper = new Thread(() -> {
            stopServer();
            code.complete(value);
        }, "browser-signin-stop");
        stopper.setDaemon(true);
        stopper.start();
    }

    private void stopServer() {
        if (stopped.compareAndSet(false, true)) {
            server.stop(0);
        }
    }

    private static void respond(HttpExchange exchange, int status, String contentType, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        // Close rather than keep alive: the listener is about to disappear.
        exchange.getResponseHeaders().set("Connection", "close");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> params = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return params;
        }
        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            params.put(decode(key), decode(value));
        }
        return params;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // Malformed percent escapes: treat the raw text as the value, it will
            // simply fail the state comparison.
            return value;
        }
    }

    // -- the pages the browser sees -------------------------------------------

    private static final String SUCCESS_PAGE = """
            <!doctype html>
            <html lang="da">
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Du er logget ind</title>
            <style>
            body { margin: 0; min-height: 100vh; display: flex; align-items: center;
                   justify-content: center; background: #faf7f2; color: #2c2622;
                   font-family: system-ui, -apple-system, Segoe UI, sans-serif; }
            main { max-width: 28rem; padding: 2rem; text-align: center; }
            h1 { font-size: 1.5rem; margin: 0 0 0.75rem; }
            p { margin: 0; line-height: 1.6; color: #5c534c; }
            </style>
            </head>
            <body>
            <main>
            <h1>Du er logget ind</h1>
            <p>Du kan lukke denne fane og gå tilbage til TodoList.</p>
            </main>
            </body>
            </html>
            """;

    private static final String REJECTED_BODY =
            "Denne anmodning hører ikke til det aktive login. Du kan lukke fanen.";
}
