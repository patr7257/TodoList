package dk.dtu;

import java.util.Optional;
import java.util.prefs.Preferences;

// Persists the client's connection + session state so the next launch can
// default to the last-used API server and stay logged in: the API base URL
// plus the bearer token and the signed-in email.
public final class ServerPrefs {

    private static final String KEY_API_URL = "api.url";
    private static final String KEY_WEB_URL = "web.url";
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

    // -- web origin ------------------------------------------------------------
    //
    // Sign in is browser mediated (issue #51), so the client needs a second
    // origin: the website that owns the passkey / magic-link pages. Kept
    // alongside api.url rather than derived from it, because the two are
    // genuinely separate deployments.

    public static void saveWebBaseUrl(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        PREFS.put(KEY_WEB_URL, url.trim());
    }

    public static Optional<String> savedWebBaseUrl() {
        String url = PREFS.get(KEY_WEB_URL, null);
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
}
