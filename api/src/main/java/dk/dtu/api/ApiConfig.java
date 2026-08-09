package dk.dtu.api;

/**
 * Runtime configuration for the API. Every value is read as a JVM system
 * property first, then an environment variable, then a built-in default, so
 * the packaged jar can be configured either way (Config in the shared module
 * uses the same pattern).
 *
 * <p>The database URL and session secret are intentionally NOT required at
 * startup: like the website's lazy getDb(), the API boots even when they are
 * absent and answers 503 on the data routes, which keeps a misconfigured
 * deployment diagnosable instead of crash-looping.
 */
public final class ApiConfig {

    public static final int DEFAULT_HTTP_PORT = 8080;
    public static final int DEFAULT_RATE_LIMIT_MAX = 8;
    public static final int DEFAULT_RATE_LIMIT_WINDOW_SECONDS = 10 * 60;

    /**
     * Where a public share link is browsed. The API composes the full share URL
     * (see ShareViews) so the desktop client and the website can never show
     * different links for the same share.
     */
    public static final String DEFAULT_SHARE_BASE_URL = "https://patrickrobel.dk";

    /**
     * The public share route is unauthenticated, so its limit is per client IP
     * and much looser than the login limit (a shared page is refreshed and
     * polled by ordinary readers), while still capping a brute-force sweep.
     */
    public static final int DEFAULT_SHARE_RATE_LIMIT_MAX = 60;
    public static final int DEFAULT_SHARE_RATE_LIMIT_WINDOW_SECONDS = 60;

    private final int httpPort;
    private final String databaseUrl;
    private final String sessionSecret;
    private final int rateLimitMax;
    private final int rateLimitWindowSeconds;
    private final String shareBaseUrl;
    private final int shareRateLimitMax;
    private final int shareRateLimitWindowSeconds;

    private ApiConfig(int httpPort, String databaseUrl, String sessionSecret,
                      int rateLimitMax, int rateLimitWindowSeconds,
                      String shareBaseUrl, int shareRateLimitMax, int shareRateLimitWindowSeconds) {
        this.httpPort = httpPort;
        this.databaseUrl = databaseUrl;
        this.sessionSecret = sessionSecret;
        this.rateLimitMax = rateLimitMax;
        this.rateLimitWindowSeconds = rateLimitWindowSeconds;
        this.shareBaseUrl = shareBaseUrl;
        this.shareRateLimitMax = shareRateLimitMax;
        this.shareRateLimitWindowSeconds = shareRateLimitWindowSeconds;
    }

    /** Builds the config from system properties / environment variables. */
    public static ApiConfig fromEnvironment() {
        int port = intValue("API_HTTP_PORT", DEFAULT_HTTP_PORT);
        String db = normalizeJdbcUrl(stringValue("DATABASE_URL", null));
        String secret = stringValue("TODO_SESSION_SECRET", null);
        int rlMax = intValue("API_RATE_LIMIT_MAX", DEFAULT_RATE_LIMIT_MAX);
        int rlWindow = intValue("API_RATE_LIMIT_WINDOW_SECONDS", DEFAULT_RATE_LIMIT_WINDOW_SECONDS);
        String shareBase = normalizeBaseUrl(stringValue("TODO_SHARE_BASE_URL", DEFAULT_SHARE_BASE_URL));
        int shareMax = intValue("API_SHARE_RATE_LIMIT_MAX", DEFAULT_SHARE_RATE_LIMIT_MAX);
        int shareWindow = intValue("API_SHARE_RATE_LIMIT_WINDOW_SECONDS",
                DEFAULT_SHARE_RATE_LIMIT_WINDOW_SECONDS);
        return new ApiConfig(port, db, secret, rlMax, rlWindow, shareBase, shareMax, shareWindow);
    }

    /**
     * Explicit constructor for tests. Share settings take their defaults.
     *
     * <p>The signature is frozen: existing tests call it positionally, so share
     * settings were added as the overload below instead of extra parameters
     * here.
     */
    public static ApiConfig of(int httpPort, String databaseUrl, String sessionSecret,
                               int rateLimitMax, int rateLimitWindowSeconds) {
        return of(httpPort, databaseUrl, sessionSecret, rateLimitMax, rateLimitWindowSeconds,
                DEFAULT_SHARE_BASE_URL, DEFAULT_SHARE_RATE_LIMIT_MAX,
                DEFAULT_SHARE_RATE_LIMIT_WINDOW_SECONDS);
    }

    /** Explicit constructor for tests that need to pin the share settings too. */
    public static ApiConfig of(int httpPort, String databaseUrl, String sessionSecret,
                               int rateLimitMax, int rateLimitWindowSeconds,
                               String shareBaseUrl, int shareRateLimitMax,
                               int shareRateLimitWindowSeconds) {
        return new ApiConfig(httpPort, normalizeJdbcUrl(databaseUrl), sessionSecret,
                rateLimitMax, rateLimitWindowSeconds, normalizeBaseUrl(shareBaseUrl),
                shareRateLimitMax, shareRateLimitWindowSeconds);
    }

    /**
     * Trims and strips any trailing slashes, so the composed share URL is
     * always {@code <base>/s/<token>} and never {@code <base>//s/<token>}. A
     * blank value falls back to the default rather than producing a relative
     * link nobody can open.
     */
    public static String normalizeBaseUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_SHARE_BASE_URL;
        }
        String url = raw.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url.isEmpty() ? DEFAULT_SHARE_BASE_URL : url;
    }

    /**
     * Accepts either a JDBC url (jdbc:postgresql://...) or a libpq-style url
     * (postgres:// / postgresql://, as Neon and Vercel hand out) and returns a
     * jdbc:postgresql:// url the Postgres driver understands. Any query string
     * (for example ?sslmode=require) is preserved. Returns null unchanged.
     */
    public static String normalizeJdbcUrl(String raw) {
        if (raw == null) {
            return null;
        }
        String url = raw.trim();
        if (url.isEmpty()) {
            return null;
        }
        if (url.startsWith("jdbc:")) {
            return url;
        }
        if (url.startsWith("postgres://")) {
            return "jdbc:postgresql://" + url.substring("postgres://".length());
        }
        if (url.startsWith("postgresql://")) {
            return "jdbc:postgresql://" + url.substring("postgresql://".length());
        }
        // Unknown scheme: hand it back and let the driver report a clear error.
        return url;
    }

    public int httpPort() {
        return httpPort;
    }

    public String databaseUrl() {
        return databaseUrl;
    }

    public boolean databaseConfigured() {
        return databaseUrl != null && !databaseUrl.isBlank();
    }

    public String sessionSecret() {
        return sessionSecret;
    }

    public boolean sessionSecretConfigured() {
        return sessionSecret != null && !sessionSecret.isBlank();
    }

    public int rateLimitMax() {
        return rateLimitMax;
    }

    public int rateLimitWindowSeconds() {
        return rateLimitWindowSeconds;
    }

    /** Origin a public share link is browsed at, with no trailing slash. */
    public String shareBaseUrl() {
        return shareBaseUrl;
    }

    public int shareRateLimitMax() {
        return shareRateLimitMax;
    }

    public int shareRateLimitWindowSeconds() {
        return shareRateLimitWindowSeconds;
    }

    private static String stringValue(String key, String fallback) {
        String prop = System.getProperty(key);
        if (prop != null && !prop.isBlank()) {
            return prop.trim();
        }
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return fallback;
    }

    private static int intValue(String key, int fallback) {
        String value = stringValue(key, null);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
