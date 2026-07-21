package dk.dtu.net;

import dk.dtu.net.ApiModels.CurrentUser;
import dk.dtu.net.ApiModels.LoginResponse;
import dk.dtu.net.ApiModels.StateResponse;
import dk.dtu.net.ApiModels.UserRef;
import dk.dtu.shared.Config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Process-wide session state for the HTTP client: the {@link TodoApiClient},
 * the bearer token, the signed-in user, and the closed set of users used to
 * resolve assignee names to ids. Replaces the old jSpace connection pool.
 *
 * <p>The token, the user's email and the API base URL are persisted (via
 * {@link dk.dtu.ServerPrefs}-style preferences, injected here to avoid a
 * client-package cycle) so a relaunch stays logged in.
 */
public final class ApiSession {

    private static final ApiSession INSTANCE = new ApiSession();

    public static ApiSession get() {
        return INSTANCE;
    }

    private volatile TodoApiClient client;
    private volatile CurrentUser currentUser;

    // Name -> id and id -> name maps for the assignee dropdowns. Rebuilt on every
    // state fetch so they stay in sync with the shared user set.
    private volatile Map<String, String> nameToId = new LinkedHashMap<>();
    private volatile Map<String, String> idToName = new LinkedHashMap<>();
    private volatile List<String> userNames = new ArrayList<>();

    // Callbacks wired by the UI layer.
    private volatile Runnable onAuthExpired;

    private ApiSession() {
    }

    // -- lifecycle -------------------------------------------------------------

    /** (Re)build the client from the current Config base URL and the given token. */
    public synchronized void configure(String token) {
        this.client = new TodoApiClient(Config.getApiBaseUrl(), token);
    }

    /** The client, creating one from Config + no token if not configured yet. */
    public synchronized TodoApiClient client() {
        if (client == null) {
            client = new TodoApiClient(Config.getApiBaseUrl(), null);
        }
        return client;
    }

    public boolean hasToken() {
        return client != null && client().token() != null && !client().token().isBlank();
    }

    public String token() {
        return client == null ? null : client().token();
    }

    public CurrentUser currentUser() {
        return currentUser;
    }

    public void setOnAuthExpired(Runnable handler) {
        this.onAuthExpired = handler;
    }

    // -- auth ------------------------------------------------------------------

    /** Log in and remember the token + current user. Throws on failure. */
    public LoginResponse login(String email, String password) throws Exception {
        LoginResponse res = client().login(email, password);
        if (res == null || !res.ok() || res.token() == null) {
            throw new ApiException(401, "", "Login failed");
        }
        this.currentUser = res.user();
        return res;
    }

    /** Forget the session (local token + cached user set). Best-effort logout. */
    public void logout() {
        try {
            if (client != null) {
                client.logout();
            }
        } catch (Exception ignored) {
            // ignore network errors on logout
        }
        this.currentUser = null;
        this.nameToId = new LinkedHashMap<>();
        this.idToName = new LinkedHashMap<>();
        this.userNames = new ArrayList<>();
    }

    // -- state -----------------------------------------------------------------

    /**
     * Fetch GET /state and refresh the cached user set + current user. Blocking;
     * call off the FX thread.
     */
    public StateResponse fetchState() throws Exception {
        StateResponse state = client().getState();
        if (state != null) {
            if (state.user() != null) {
                this.currentUser = state.user();
            }
            Map<String, String> n2i = new LinkedHashMap<>();
            Map<String, String> i2n = new LinkedHashMap<>();
            List<String> names = new ArrayList<>();
            if (state.users() != null) {
                for (UserRef u : state.users()) {
                    if (u == null || u.name() == null) {
                        continue;
                    }
                    n2i.put(u.name(), u.id());
                    i2n.put(u.id(), u.name());
                    names.add(u.name());
                }
            }
            this.nameToId = n2i;
            this.idToName = i2n;
            this.userNames = names;
        }
        return state;
    }

    // -- identity mapping ------------------------------------------------------

    /** Names of all users (assignee options), from the last state fetch. */
    public List<String> userNames() {
        return new ArrayList<>(userNames);
    }

    /** Resolve a display name to a user id, or null if unknown/blank. */
    public String idForName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return nameToId.get(name.trim());
    }

    /** Resolve a user id to a display name, or null if unknown. */
    public String nameForId(String id) {
        if (id == null) {
            return null;
        }
        return idToName.get(id);
    }

    // -- error handling --------------------------------------------------------

    /**
     * Central place for the methods layer to route a failed API call. Fires the
     * auth-expired handler on 401, otherwise defers to the shared connection
     * error handler (which the navigator wires to the reconnect flow).
     */
    public void reportError(Throwable ex) {
        if (ex instanceof ApiException api && api.isUnauthorized()) {
            Runnable handler = onAuthExpired;
            if (handler != null) {
                handler.run();
            }
            return;
        }
        if (ex instanceof Exception e && Config.isConnectionError(e)) {
            Config.handleConnectionError(e);
        }
    }
}
