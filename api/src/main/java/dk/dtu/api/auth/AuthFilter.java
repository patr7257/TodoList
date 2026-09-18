package dk.dtu.api.auth;

import java.util.Optional;
import java.util.OptionalInt;

import dk.dtu.api.web.Backend;
import dk.dtu.api.web.HttpError;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import org.jetbrains.annotations.NotNull;

/**
 * Javalin before-handler that enforces a valid session on every route except
 * the explicit allowlist below (logout and the public share reader).
 * The session token is read from an {@code Authorization:
 * Bearer <token>} header or, for drop-in compatibility with the website, from a
 * {@code todo_session} cookie. On success the verified user id is stashed as the
 * {@code uid} request attribute; otherwise a 401 is raised.
 *
 * <p>Since issue #74 a token may also carry a {@code tv} claim, the user's
 * {@code users.token_version} at mint time. When it is there it is checked
 * against the stored column, so bumping that column signs one person out
 * without touching anybody else's session.
 *
 * <p>When the database is not configured the filter steps aside so the data
 * controllers can answer 503 first, matching the website's ordering where an
 * unconfigured backend returns 503 rather than 401.
 */
public final class AuthFilter implements Handler {

    public static final String COOKIE_NAME = "todo_session";
    public static final String UID_ATTRIBUTE = "uid";

    /**
     * The complete set of unauthenticated paths, matched EXACTLY (logout) or by
     * prefix (the public share reader). Two entries, and that is the whole list.
     *
     * <p>There used to be a third, exact {@code /api/todo/login}, for the email
     * plus password route. Issue #61 deleted that route, so the exemption went
     * with it: an allowlist entry that outlives its route is a hole waiting for
     * someone to reuse the path.
     *
     * <p>Matching is exact or prefix and never by suffix. It was once
     * {@code path.endsWith("/login") || path.endsWith("/logout")}, which was
     * fine while those were the only exemptions and brittle the moment a third
     * arrived: a suffix match opens any future route whose path happens to end
     * that way, anywhere in the tree.
     *
     * <p>The prefix {@code /api/todo/share/} is safe because the singular /
     * plural split is load-bearing, not cosmetic. {@code share} singular appears
     * in exactly ONE path in the whole API and that path is the public one;
     * managing shares lives under {@code /api/todo/lists/{id}/shares}, plural,
     * which this prefix cannot match. Keep it that way: never add a second
     * route under {@code /api/todo/share/}.
     *
     * <p>Logout stays exempt because clearing an expired session must not
     * require a valid one, and because the route only expires a cookie: it
     * reads nothing and cannot leak anything.
     */
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
        Optional<Token.Session> session = backend.token().verify(value);
        if (session.isEmpty()) {
            throw HttpError.unauthorized();
        }
        String uid = session.get().uid();
        if (!versionCurrent(session.get())) {
            throw HttpError.unauthorized();
        }
        ctx.attribute(UID_ATTRIBUTE, uid);
    }

    /**
     * True when the token's {@code tv} claim still matches the user's stored
     * {@code token_version}, or when the token carries no claim at all.
     *
     * <p>The claimless branch exists ONLY for the transition: every session
     * minted before issue #74 is sitting in a browser without a {@code tv}, and
     * rejecting those would sign everybody out on deploy, which is precisely
     * the blunt instrument this feature replaces. It is also the one gap in the
     * feature, because a pre-#74 token survives a revocation. Once every live
     * session carries a claim (the website mints them, and the 30 day TTL ages
     * the rest out), this branch can be tightened to reject a missing claim,
     * and per-user revocation is then complete.
     *
     * <p>An unknown or non-uuid uid yields an empty version and is rejected:
     * a deleted user's token must not outlive the row.
     *
     * <p>This is the only database read on the authenticated request path, and
     * it is deliberately NOT cached. A cache would delay a revocation by its
     * TTL, and immediacy is the entire point of the feature: "signed out now"
     * that means "signed out within five minutes" is not what somebody reaches
     * for when a device goes missing. This is a two person app, so the read is
     * a primary key lookup on a table with two rows, against a pool that is
     * already warm. If traffic ever makes that cost visible, a short TTL cache
     * (seconds, keyed by uid, invalidated on bump) is the obvious optimisation,
     * and it should be added then rather than now.
     */
    private boolean versionCurrent(Token.Session session) {
        OptionalInt claimed = session.tokenVersion();
        if (claimed.isEmpty()) {
            return true;
        }
        OptionalInt current = backend.todo().tokenVersion(session.uid());
        return current.isPresent() && current.getAsInt() == claimed.getAsInt();
    }

    /** Exact match for logout, prefix match for the public share reader. */
    static boolean isPublic(String path) {
        if (path == null) {
            return false;
        }
        return LOGOUT_PATH.equals(path)
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
