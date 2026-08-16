package dk.dtu.api.web;

import dk.dtu.api.ApiConfig;
import dk.dtu.api.auth.Token;
import dk.dtu.api.domain.CountersService;
import dk.dtu.api.domain.SharesService;
import dk.dtu.api.domain.TinderService;
import dk.dtu.api.domain.TodoService;

/**
 * Holds the wired-up services and shared collaborators for the controllers.
 * When the database is not configured, {@link #todo()}, {@link #counters()},
 * {@link #shares()} and {@link #tinder()} are null and controllers answer 503,
 * mirroring the website's lazy getDb() that returns null and makes every data
 * route respond "backend not configured".
 *
 * <p>The narrower constructors delegate to the widest one so adding a service
 * never forces an edit to existing call sites (tests included).
 *
 * <p>Two collaborators left with password login in issue #61: an
 * {@code AuthService} that verified scrypt hashes, and a login
 * {@link RateLimiter}. Only the login route ever read either, and there is no
 * login route now that sign-in happens on the website. {@link #token()} stays
 * and is the whole of authentication here: the website mints a
 * {@code todo_session} value and this API verifies it. The share limiter is a
 * different object and stays, because the public share route is
 * unauthenticated and still needs a brute-force cap.
 */
public final class Backend {

    private final ApiConfig config;
    private final TodoService todo;
    private final Token token;
    private final CountersService counters;
    private final SharesService shares;
    private final RateLimiter shareRateLimiter;
    private final TinderService tinder;

    public Backend(ApiConfig config, TodoService todo, Token token) {
        this(config, todo, token, null);
    }

    public Backend(ApiConfig config, TodoService todo, Token token, CountersService counters) {
        this(config, todo, token, counters, null, null);
    }

    public Backend(ApiConfig config, TodoService todo, Token token, CountersService counters,
                   SharesService shares, RateLimiter shareRateLimiter) {
        this(config, todo, auth, token, loginRateLimiter, counters, shares, shareRateLimiter, null);
    }

    public Backend(ApiConfig config, TodoService todo, AuthService auth, Token token,
                   RateLimiter loginRateLimiter, CountersService counters,
                   SharesService shares, RateLimiter shareRateLimiter, TinderService tinder) {
        this.config = config;
        this.tinder = tinder;
        this.todo = todo;
        this.token = token;
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

    public Token token() {
        return token;
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

    public TinderService tinder() {
        return tinder;
    }
}
