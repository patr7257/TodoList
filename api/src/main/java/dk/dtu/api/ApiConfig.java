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

    /**
     * Where a public share link is browsed. The API composes the full share URL
     * (see ShareViews) so the desktop client and the website can never show
     * different links for the same share.
     */
    public static final String DEFAULT_SHARE_BASE_URL = "https://patrickrobel.dk";

    /**
     * The public share route is unauthenticated, so its limit is per client IP
     * and deliberately loose (a shared page is refreshed and polled by ordinary
     * readers), while still capping a brute-force sweep.
     *
     * <p>It is also the only rate limit left. There used to be a second pair,
     * {@code API_RATE_LIMIT_MAX} / {@code API_RATE_LIMIT_WINDOW_SECONDS},
     * throttling password login attempts; issue #61 deleted that route, so the
     * knobs went with it rather than sitting here being read and ignored. The
     * two env vars are simply unrecognised now, which is harmless if one is
     * still set on a deployment.
     */
    public static final int DEFAULT_SHARE_RATE_LIMIT_MAX = 60;
    public static final int DEFAULT_SHARE_RATE_LIMIT_WINDOW_SECONDS = 60;

    /**
     * Where THIS API is reachable from outside the container. Distinct from
     * {@link #DEFAULT_SHARE_BASE_URL}, which is the website: a share link is
     * browsed on patrickrobel.dk while an API call goes to the API host, and
     * conflating the two would produce a share URL nobody can open or a refill
     * prompt that posts into the website.
     *
     * <p>Needed because the TodoTinder refill prompt (issue #59) has to name an
     * absolute endpoint: it is pasted into a Claude session that has no idea
     * where this API lives, and a relative path there would be useless.
     */
    public static final String DEFAULT_PUBLIC_BASE_URL = "https://api.todolist.patrickrobel.dk";

    private final int httpPort;
    private final String databaseUrl;
    private final String sessionSecret;
    private final String shareBaseUrl;
    private final int shareRateLimitMax;
    private final int shareRateLimitWindowSeconds;
    private final String publicBaseUrl;

    private ApiConfig(int httpPort, String databaseUrl, String sessionSecret,
                      String shareBaseUrl, int shareRateLimitMax, int shareRateLimitWindowSeconds,
                      String publicBaseUrl) {
        this.httpPort = httpPort;
        this.databaseUrl = databaseUrl;
        this.sessionSecret = sessionSecret;
        this.shareBaseUrl = shareBaseUrl;
        this.shareRateLimitMax = shareRateLimitMax;
        this.shareRateLimitWindowSeconds = shareRateLimitWindowSeconds;
        this.publicBaseUrl = publicBaseUrl;
    }

    /** Builds the config from system properties / environment variables. */
    public static ApiConfig fromEnvironment() {
        int port = intValue("API_HTTP_PORT", DEFAULT_HTTP_PORT);
        String db = normalizeJdbcUrl(stringValue("DATABASE_URL", null));
        String secret = stringValue("TODO_SESSION_SECRET", null);
        String shareBase = normalizeBaseUrl(stringValue("TODO_SHARE_BASE_URL", DEFAULT_SHARE_BASE_URL));
        int shareMax = intValue("API_SHARE_RATE_LIMIT_MAX", DEFAULT_SHARE_RATE_LIMIT_MAX);
        int shareWindow = intValue("API_SHARE_RATE_LIMIT_WINDOW_SECONDS",
                DEFAULT_SHARE_RATE_LIMIT_WINDOW_SECONDS);
        String publicBase = normalizeBaseUrl(stringValue("API_PUBLIC_BASE_URL", DEFAULT_PUBLIC_BASE_URL),
                DEFAULT_PUBLIC_BASE_URL);
        return new ApiConfig(port, db, secret, shareBase, shareMax, shareWindow, publicBase);
    }

    /** Explicit constructor for tests. Share settings take their defaults. */
    public static ApiConfig of(int httpPort, String databaseUrl, String sessionSecret) {
        return of(httpPort, databaseUrl, sessionSecret,
                DEFAULT_SHARE_BASE_URL, DEFAULT_SHARE_RATE_LIMIT_MAX,
                DEFAULT_SHARE_RATE_LIMIT_WINDOW_SECONDS);
    }

    /** Explicit constructor for tests that need to pin the share settings too. */
    public static ApiConfig of(int httpPort, String databaseUrl, String sessionSecret,
                               String shareBaseUrl, int shareRateLimitMax,
                               int shareRateLimitWindowSeconds) {
        return of(httpPort, databaseUrl, sessionSecret,
                shareBaseUrl, shareRateLimitMax, shareRateLimitWindowSeconds,
                DEFAULT_PUBLIC_BASE_URL);
    }

    /**
     * Explicit constructor for tests that also need to pin the API's own public
     * base URL (the one the tinder refill prompt names).
     */
    public static ApiConfig of(int httpPort, String databaseUrl, String sessionSecret,
                               String shareBaseUrl, int shareRateLimitMax,
                               int shareRateLimitWindowSeconds, String publicBaseUrl) {
        return new ApiConfig(httpPort, normalizeJdbcUrl(databaseUrl), sessionSecret,
                normalizeBaseUrl(shareBaseUrl), shareRateLimitMax, shareRateLimitWindowSeconds,
                normalizeBaseUrl(publicBaseUrl, DEFAULT_PUBLIC_BASE_URL));
    }

    /**
     * Trims and strips any trailing slashes, so the composed share URL is
     * always {@code <base>/s/<token>} and never {@code <base>//s/<token>}. A
     * blank value falls back to the default rather than producing a relative
     * link nobody can open.
     */
    public static String normalizeBaseUrl(String raw) {
        return normalizeBaseUrl(raw, DEFAULT_SHARE_BASE_URL);
    }

    /**
     * The same normalisation against an explicit fallback, because there are now
     * two different origins to normalise (the website a share is browsed on, and
     * this API itself) and defaulting the API's origin to the website's would
     * produce a refill prompt that posts to the wrong host.
     */
    public static String normalizeBaseUrl(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String url = raw.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url.isEmpty() ? fallback : url;
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

    /** Origin this API is reachable at from outside, with no trailing slash. */
    public String publicBaseUrl() {
        return publicBaseUrl;
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
