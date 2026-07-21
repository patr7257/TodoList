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

    private final int httpPort;
    private final String databaseUrl;
    private final String sessionSecret;
    private final int rateLimitMax;
    private final int rateLimitWindowSeconds;

    private ApiConfig(int httpPort, String databaseUrl, String sessionSecret,
                      int rateLimitMax, int rateLimitWindowSeconds) {
        this.httpPort = httpPort;
        this.databaseUrl = databaseUrl;
        this.sessionSecret = sessionSecret;
        this.rateLimitMax = rateLimitMax;
        this.rateLimitWindowSeconds = rateLimitWindowSeconds;
    }

    /** Builds the config from system properties / environment variables. */
    public static ApiConfig fromEnvironment() {
        int port = intValue("API_HTTP_PORT", DEFAULT_HTTP_PORT);
        String db = normalizeJdbcUrl(stringValue("DATABASE_URL", null));
        String secret = stringValue("TODO_SESSION_SECRET", null);
        int rlMax = intValue("API_RATE_LIMIT_MAX", DEFAULT_RATE_LIMIT_MAX);
        int rlWindow = intValue("API_RATE_LIMIT_WINDOW_SECONDS", DEFAULT_RATE_LIMIT_WINDOW_SECONDS);
        return new ApiConfig(port, db, secret, rlMax, rlWindow);
    }

    /** Explicit constructor for tests. */
    public static ApiConfig of(int httpPort, String databaseUrl, String sessionSecret,
                               int rateLimitMax, int rateLimitWindowSeconds) {
        return new ApiConfig(httpPort, normalizeJdbcUrl(databaseUrl), sessionSecret,
                rateLimitMax, rateLimitWindowSeconds);
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
