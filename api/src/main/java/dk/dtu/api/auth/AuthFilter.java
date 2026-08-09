package dk.dtu.api.auth;

import java.util.Optional;

import dk.dtu.api.web.Backend;
import dk.dtu.api.web.HttpError;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import org.jetbrains.annotations.NotNull;

/**
 * Javalin before-handler that enforces a valid session on every route except
 * the explicit allowlist below (login, logout, and the public share reader).
 * The session token is read from an {@code Authorization:
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

    /**
     * The complete set of unauthenticated paths, matched EXACTLY (login,
     * logout) or by prefix (the public share reader).
     *
     * <p>This used to be {@code path.endsWith("/login") || path.endsWith(
     * "/logout")}, which was fine while those were the only two exemptions and
     * brittle the moment a third arrived: a suffix match opens any future route
     * whose path happens to end that way, anywhere in the tree.
     *
     * <p>The prefix {@code /api/todo/share/} is safe because the singular /
     * plural split is load-bearing, not cosmetic. {@code share} singular appears
     * in exactly ONE path in the whole API and that path is the public one;
     * managing shares lives under {@code /api/todo/lists/{id}/shares}, plural,
     * which this prefix cannot match. Keep it that way: never add a second
     * route under {@code /api/todo/share/}.
     */
    private static final String LOGIN_PATH = "/api/todo/login";
    private static final String LOGOUT_PATH = "/api/todo/logout";
    private static final String PUBLIC_SHARE_PREFIX = "/api/todo/share/";

    private final Backend backend;

    public AuthFilter(Backend backend) {
        this.backend = backend;
    }

    @Override
    public void handle(@NotNull Context ctx) {
        if (isPublic(ctx.path())) {
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

    /** Exact match for login/logout, prefix match for the public share reader. */
    static boolean isPublic(String path) {
        if (path == null) {
            return false;
        }
        return LOGIN_PATH.equals(path)
                || LOGOUT_PATH.equals(path)
                || path.startsWith(PUBLIC_SHARE_PREFIX);
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
