package dk.dtu.api.web;

import java.util.LinkedHashMap;
import java.util.Map;

import dk.dtu.api.auth.AuthFilter;

import io.javalin.http.Context;
import io.javalin.http.Cookie;
import io.javalin.http.SameSite;

/**
 * POST /api/todo/logout, mirroring the website route of the same name.
 *
 * <p>Logout is all that is left here. This class used to own POST
 * /api/todo/login too, the email plus password path that the retired JavaFX
 * desktop client signed in with; issue #61 deleted it, because sign-in now
 * happens entirely on the website (passkey or magic link, issue #51) and the
 * website mints the {@code todo_session} token itself in the byte-identical
 * format {@link dk.dtu.api.auth.Token} verifies. There is no credential this
 * API can be handed any more, so there is nothing for a login route to check.
 *
 * <p>Logout survives the removal because it is not the inverse of login: it
 * only expires the {@code todo_session} cookie, which is exactly as useful for
 * a session minted on the website as for one minted here. It stays on
 * {@link AuthFilter}'s unauthenticated allowlist for the same reason it always
 * was: clearing an already-invalid cookie must not require a valid one, or an
 * expired session could never be cleaned up.
 *
 * <p>The cookie attributes must keep matching the ones the website sets (same
 * name, path, HttpOnly, Secure, SameSite=Lax). A browser only replaces a cookie
 * when those line up, so a drift here would leave the old value in place and
 * make logout silently do nothing.
 *
 * <p>Unlike every other controller this one takes no {@link Backend}: expiring
 * a cookie touches neither the database nor the session secret, and holding a
 * reference it never reads would only suggest otherwise.
 */
public final class AuthController {

    public void logout(Context ctx) {
        Cookie cookie = new Cookie(AuthFilter.COOKIE_NAME, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setSameSite(SameSite.LAX);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        ctx.cookie(cookie);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        ctx.json(out);
    }
}
