package dk.dtu.net;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dk.dtu.net.ApiModels.ItemDto;
import dk.dtu.net.ApiModels.ListDto;
import dk.dtu.net.ApiModels.LoginResponse;
import dk.dtu.net.ApiModels.StateResponse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thin, typed client over the shared todo HTTP API (base path {@code /api/todo}).
 *
 * <p>Uses {@link java.net.http.HttpClient} + Gson. It holds the API origin and
 * the bearer token, sends {@code Authorization: Bearer <token>} on every call
 * except login/logout, and maps any non-2xx response to an {@link ApiException}
 * (401 signals an expired session). It performs blocking network I/O, so callers
 * must invoke it off the JavaFX thread.
 */
public final class TodoApiClient {

    // Response bodies parsed with the plain Gson. Request bodies use a
    // null-serializing Gson because POST /items requires description, location
    // and assigneeId keys to be PRESENT (even when null), and PATCH uses null to
    // clear a field (unassign, clear due date).
    private static final Gson GSON = new Gson();
    private static final Gson GSON_NULLS = new GsonBuilder().serializeNulls().create();

    private final HttpClient http;
    private final String origin;
    private volatile String token;

    public TodoApiClient(String baseUrl, String token) {
        this.origin = normalizeOrigin(baseUrl);
        this.token = token;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String token() {
        return token;
    }

    public String origin() {
        return origin;
    }

    // -- auth ------------------------------------------------------------------

    /** POST /login. On success the returned token is also applied to this client. */
    public LoginResponse login(String email, String password) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("password", password);
        String json = send("POST", "/login", GSON_NULLS.toJson(body), false);
        LoginResponse res = GSON.fromJson(json, LoginResponse.class);
        if (res != null && res.token() != null) {
            this.token = res.token();
        }
        return res;
    }

    /** POST /logout. Clears the local token regardless of the server response. */
    public void logout() throws Exception {
        try {
            send("POST", "/logout", "{}", false);
        } finally {
            this.token = null;
        }
    }

    // -- state -----------------------------------------------------------------

    /** GET /state: the whole shared space plus the assignee user list. */
    public StateResponse getState() throws Exception {
        String json = send("GET", "/state", null, true);
        return GSON.fromJson(json, StateResponse.class);
    }

    /**
     * Best-effort reachability probe. Returns true when the API answers at all
     * (any HTTP status, including 401), false when the host cannot be reached.
     * Never throws.
     */
    public boolean ping() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(origin + "/api/todo/state"))
                    .timeout(Duration.ofSeconds(6))
                    .GET()
                    .build();
            http.send(req, HttpResponse.BodyHandlers.discarding());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // -- lists -----------------------------------------------------------------

    /** POST /lists. */
    public ListDto createList(String name) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        String json = send("POST", "/lists", GSON_NULLS.toJson(body), true);
        return unwrapList(json);
    }

    /** PATCH /lists/{id}. Pass null for a field to leave it unchanged. */
    public ListDto updateList(String id, String name, Integer sort) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        if (name != null) {
            body.put("name", name);
        }
        if (sort != null) {
            body.put("sort", sort);
        }
        String json = send("PATCH", "/lists/" + enc(id), GSON.toJson(body), true);
        return unwrapList(json);
    }

    /** DELETE /lists/{id}. */
    public void deleteList(String id) throws Exception {
        send("DELETE", "/lists/" + enc(id), null, true);
    }

    // -- items -----------------------------------------------------------------

    /**
     * POST /items. {@code description}, {@code location} and {@code assigneeId}
     * are always sent (may be null) as the API requires those keys present.
     * {@code priority}, {@code dueAt} and {@code status} are sent only when
     * non-null (the API defaults them).
     */
    public ItemDto createItem(String listId, String text, String description, String location,
                              String assigneeId, Integer priority, String dueAt, String status) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("listId", listId);
        body.put("text", text);
        body.put("description", description);
        body.put("location", location);
        body.put("assigneeId", assigneeId);
        if (priority != null) {
            body.put("priority", priority);
        }
        if (dueAt != null) {
            body.put("dueAt", dueAt);
        }
        if (status != null) {
            body.put("status", status);
        }
        String json = send("POST", "/items", GSON_NULLS.toJson(body), true);
        return unwrapItem(json);
    }

    /**
     * PATCH /items/{id}. The map is sent verbatim (null values are preserved so
     * they can clear a field). Only include the keys you intend to change.
     */
    public ItemDto updateItem(String id, Map<String, Object> patch) throws Exception {
        String json = send("PATCH", "/items/" + enc(id), GSON_NULLS.toJson(patch), true);
        return unwrapItem(json);
    }

    /** DELETE /items/{id}. */
    public void deleteItem(String id) throws Exception {
        send("DELETE", "/items/" + enc(id), null, true);
    }

    // -- plumbing --------------------------------------------------------------

    private ListDto unwrapList(String json) {
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        return GSON.fromJson(obj.get("list"), ListDto.class);
    }

    private ItemDto unwrapItem(String json) {
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        return GSON.fromJson(obj.get("item"), ItemDto.class);
    }

    private String send(String method, String path, String body, boolean authenticated) throws Exception {
        HttpRequest.BodyPublisher publisher = (body == null)
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(origin + "/api/todo" + path))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .method(method, publisher);

        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        if (authenticated && token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }

        HttpResponse<String> res = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        int status = res.statusCode();
        if (status / 100 != 2) {
            throw new ApiException(status, res.body(),
                    "API " + method + " " + path + " failed with HTTP " + status);
        }
        return res.body();
    }

    private static String enc(String pathSegment) {
        // Path segments are UUIDs from the API, but encode defensively.
        return java.net.URLEncoder.encode(pathSegment == null ? "" : pathSegment,
                java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String normalizeOrigin(String baseUrl) {
        String u = (baseUrl == null || baseUrl.isBlank())
                ? dk.dtu.shared.Config.DEFAULT_API_BASE_URL
                : baseUrl.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        // Tolerate a base that already includes the /api/todo suffix.
        if (u.endsWith("/api/todo")) {
            u = u.substring(0, u.length() - "/api/todo".length());
        }
        return u;
    }
}
