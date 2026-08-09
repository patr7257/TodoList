package dk.dtu.api.web;

import dk.dtu.api.ApiConfig;
import dk.dtu.api.auth.AuthService;
import dk.dtu.api.auth.Token;
import dk.dtu.api.domain.CountersService;
import dk.dtu.api.domain.SharesService;
import dk.dtu.api.domain.TodoService;

/**
 * Holds the wired-up services and shared collaborators for the controllers.
 * When the database is not configured, {@link #todo()}, {@link #auth()},
 * {@link #counters()} and {@link #shares()} are null and controllers answer
 * 503, mirroring the website's lazy getDb() that returns null and makes every
 * data route respond "backend not configured".
 *
 * <p>The narrower constructors delegate to the widest one so adding a service
 * never forces an edit to existing call sites (tests included).
 */
public final class Backend {

    private final ApiConfig config;
    private final TodoService todo;
    private final AuthService auth;
    private final Token token;
    private final RateLimiter loginRateLimiter;
    private final CountersService counters;
    private final SharesService shares;
    private final RateLimiter shareRateLimiter;

    public Backend(ApiConfig config, TodoService todo, AuthService auth, Token token,
                   RateLimiter loginRateLimiter) {
        this(config, todo, auth, token, loginRateLimiter, null);
    }

    public Backend(ApiConfig config, TodoService todo, AuthService auth, Token token,
                   RateLimiter loginRateLimiter, CountersService counters) {
        this(config, todo, auth, token, loginRateLimiter, counters, null, null);
    }

    public Backend(ApiConfig config, TodoService todo, AuthService auth, Token token,
                   RateLimiter loginRateLimiter, CountersService counters,
                   SharesService shares, RateLimiter shareRateLimiter) {
        this.config = config;
        this.todo = todo;
        this.auth = auth;
        this.token = token;
        this.loginRateLimiter = loginRateLimiter;
        this.counters = counters;
        this.shares = shares;
        // The public share route runs before any auth and must never NPE, so a
        // Backend built by an older/narrower constructor still gets a working
        // limiter derived from config (or a no-op one when there is no config).
        this.shareRateLimiter = shareRateLimiter != null
                ? shareRateLimiter
                : new RateLimiter(config == null ? 0 : config.shareRateLimitMax(),
                        config == null ? 60 : config.shareRateLimitWindowSeconds());
    }

    public boolean databaseConfigured() {
        return todo != null;
    }

    public ApiConfig config() {
        return config;
    }

    public TodoService todo() {
        return todo;
    }

    public AuthService auth() {
        return auth;
    }

    public Token token() {
        return token;
    }

    public RateLimiter loginRateLimiter() {
        return loginRateLimiter;
    }

    public CountersService counters() {
        return counters;
    }

    public SharesService shares() {
        return shares;
    }

    public RateLimiter shareRateLimiter() {
        return shareRateLimiter;
    }
}
