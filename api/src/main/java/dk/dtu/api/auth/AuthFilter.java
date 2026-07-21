package dk.dtu.api.auth;

import java.util.Optional;

import dk.dtu.api.web.Backend;
import dk.dtu.api.web.HttpError;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import org.jetbrains.annotations.NotNull;

/**
 * Javalin before-handler that enforces a valid session on every route except
 * login and logout. The session token is read from an {@code Authorization:
 * Bearer <token>} header or, for drop-in compatibility with the website, from a
 * {@code todo_session} cookie. On success the verified user id is stashed as the
 * {@code uid} request attribute; otherwise a 401 is raised.
 *
 * <p>When the database is not configured the filter steps aside so the data
 * controllers can answer 503 first, matching the website's ordering where an
 * unconfigured backend returns 503 rather than 401.
 */
public final class AuthFilter implements Handler {

    public static final String COOKIE_NAME = "todo_session";
    public static final String UID_ATTRIBUTE = "uid";

    private final Backend backend;

    public AuthFilter(Backend backend) {
        this.backend = backend;
    }

    @Override
    public void handle(@NotNull Context ctx) {
        String path = ctx.path();
        if (path.endsWith("/login") || path.endsWith("/logout")) {
            return;
        }
        if (!backend.databaseConfigured()) {
            return; // let the controller answer 503
        }

        String value = bearerOrCookie(ctx);
        Optional<String> uid = backend.token().verify(value);
        if (uid.isEmpty()) {
            throw HttpError.unauthorized();
        }
        ctx.attribute(UID_ATTRIBUTE, uid.get());
    }

    private String bearerOrCookie(Context ctx) {
        String auth = ctx.header("Authorization");
        if (auth != null) {
            String trimmed = auth.trim();
            if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
                return trimmed.substring(7).trim();
            }
        }
        return ctx.cookie(COOKIE_NAME);
    }
}
