package dk.dtu.api.web;

import dk.dtu.api.ApiConfig;
import dk.dtu.api.auth.AuthService;
import dk.dtu.api.auth.Token;
import dk.dtu.api.domain.TodoService;

/**
 * Holds the wired-up services and shared collaborators for the controllers.
 * When the database is not configured, {@link #todo()} and {@link #auth()} are
 * null and controllers answer 503, mirroring the website's lazy getDb() that
 * returns null and makes every data route respond "backend not configured".
 */
public final class Backend {

    private final ApiConfig config;
    private final TodoService todo;
    private final AuthService auth;
    private final Token token;
    private final RateLimiter loginRateLimiter;

    public Backend(ApiConfig config, TodoService todo, AuthService auth, Token token,
                   RateLimiter loginRateLimiter) {
        this.config = config;
        this.todo = todo;
        this.auth = auth;
        this.token = token;
        this.loginRateLimiter = loginRateLimiter;
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
}
