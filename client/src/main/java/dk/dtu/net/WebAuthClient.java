package dk.dtu.net;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import dk.dtu.net.ApiModels.CurrentUser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client for the WEBSITE's desktop sign-in exchange (issue #51), which is a
 * different origin from the todo API: the browser authenticates the user on
 * {@code patrickrobel.dk} and hands back a one-time code, and this class trades
 * that code plus the PKCE verifier for a session token over HTTPS. The code
 * never travels back through the browser, which is the whole point of PKCE.
 *
 * <p>Same conventions as {@link TodoApiClient}: {@link java.net.http.HttpClient}
 * plus Gson, blocking I/O so callers must be off the JavaFX thread, and any
 * non-2xx mapped to an {@link ApiException} carrying the status and raw body.
 */
public final class WebAuthClient {

    /** The website route that trades a one-time code for a session token. */
    public static final String EXCHANGE_PATH = "/api/todo/auth/desktop-exchange";

    private static final Gson GSON = new Gson();
    private static final Gson GSON_NULLS = new GsonBuilder().serializeNulls().create();

    private final HttpClient http;
    private final String origin;

    public WebAuthClient(String webBaseUrl) {
        this.origin = normalizeOrigin(webBaseUrl);
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public String origin() {
        return origin;
    }

    /** Response of {@link #EXCHANGE_PATH}: {@code {ok, token, user}}. */
    public record DesktopExchange(boolean ok, String token, CurrentUser user) {
    }

    /**
     * POSTs {@code {"code": ..., "verifier": ...}} and returns the session token
     * plus the user it belongs to. The same route serves both paths: the code
     * from the loopback callback and the one the user typed by hand.
     *
     * @throws ApiException on any non-2xx response, and on a 200 that carries no
     *                      usable token (mapped to 401, since an unusable
     *                      exchange means the code was not accepted)
     */
    public DesktopExchange exchange(String code, String verifier) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("verifier", verifier);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(origin + EXCHANGE_PATH))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON_NULLS.toJson(body)))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        if (status / 100 != 2) {
            throw new ApiException(status, response.body(),
                    "Desktop sign in exchange failed with HTTP " + status);
        }

        DesktopExchange parsed = GSON.fromJson(response.body(), DesktopExchange.class);
        if (parsed == null || !parsed.ok() || parsed.token() == null || parsed.token().isBlank()) {
            throw new ApiException(401, response.body(),
                    "Desktop sign in exchange returned no session token");
        }
        return parsed;
    }

    private static String normalizeOrigin(String webBaseUrl) {
        String u = (webBaseUrl == null || webBaseUrl.isBlank())
                ? dk.dtu.shared.Config.DEFAULT_WEB_BASE_URL
                : webBaseUrl.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }
}
