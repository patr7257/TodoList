package dk.dtu;

import java.util.Optional;
import java.util.prefs.Preferences;

// Persists the client's connection + session state so the next launch can
// default to the last-used API server and stay logged in.
//
// Migrated from the old jSpace transport (which stored server.ip/server.port)
// to the HTTP API: it now stores the API base URL plus the bearer token and the
// signed-in email. The legacy ip/port accessors are kept only for backward
// compatibility and are no longer used by the connect flow.
public final class ServerPrefs {

    private static final String KEY_IP = "server.ip";
    private static final String KEY_PORT = "server.port";

    private static final String KEY_API_URL = "api.url";
    private static final String KEY_AUTH_TOKEN = "auth.token";
    private static final String KEY_AUTH_EMAIL = "auth.email";

    private static final Preferences PREFS = Preferences.userNodeForPackage(ClientApp.class);

    private ServerPrefs() {}

    // -- API base URL ----------------------------------------------------------

    public static void saveApiBaseUrl(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        PREFS.put(KEY_API_URL, url.trim());
    }

    public static Optional<String> savedApiBaseUrl() {
        String url = PREFS.get(KEY_API_URL, null);
        return (url == null || url.isBlank()) ? Optional.empty() : Optional.of(url);
    }

    // -- auth session ----------------------------------------------------------

    public static void saveAuth(String token, String email) {
        if (token != null && !token.isBlank()) {
            PREFS.put(KEY_AUTH_TOKEN, token);
        }
        if (email != null && !email.isBlank()) {
            PREFS.put(KEY_AUTH_EMAIL, email.trim());
        }
    }

    public static Optional<String> savedToken() {
        String token = PREFS.get(KEY_AUTH_TOKEN, null);
        return (token == null || token.isBlank()) ? Optional.empty() : Optional.of(token);
    }

    public static Optional<String> savedEmail() {
        String email = PREFS.get(KEY_AUTH_EMAIL, null);
        return (email == null || email.isBlank()) ? Optional.empty() : Optional.of(email);
    }

    public static void clearAuth() {
        PREFS.remove(KEY_AUTH_TOKEN);
    }

    // -- legacy jSpace ip/port (deprecated, retained for compatibility) --------

    public static void save(String ip, int port) {
        if (ip == null || ip.isBlank()) {
            return;
        }
        PREFS.put(KEY_IP, ip.trim());
        PREFS.putInt(KEY_PORT, port);
    }

    public static Optional<String> savedIp() {
        String ip = PREFS.get(KEY_IP, null);
        return (ip == null || ip.isBlank()) ? Optional.empty() : Optional.of(ip);
    }

    public static int savedPort(int fallback) {
        return PREFS.getInt(KEY_PORT, fallback);
    }

    public static void clear() {
        PREFS.remove(KEY_IP);
        PREFS.remove(KEY_PORT);
    }
}
