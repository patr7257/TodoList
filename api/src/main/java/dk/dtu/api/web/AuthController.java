package dk.dtu.api.web;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import dk.dtu.api.auth.AuthFilter;
import dk.dtu.api.auth.AuthService;

import io.javalin.http.Context;
import io.javalin.http.Cookie;
import io.javalin.http.SameSite;

/**
 * POST /api/todo/login and POST /api/todo/logout, mirroring the website routes.
 *
 * <p>Login matches the website exactly: 503 when the backend is unconfigured,
 * 400 on bad json/body, 429 when rate limited, uniform 401 on bad credentials,
 * 503 when no session secret is set. The success body is
 * {@code {ok:true, user:{id,name,email}}} plus, for headless (Bearer) clients,
 * an additive {@code token} field carrying the same value that is also set as
 * the {@code todo_session} cookie for website compatibility.
 */
public final class AuthController {

    private static final int MAX_EMAIL_LENGTH = 320;
    private static final int MAX_PASSWORD_LENGTH = 256;
    private static final int COOKIE_MAX_AGE_SECONDS = 30 * 24 * 60 * 60;

    private final Backend backend;

    public AuthController(Backend backend) {
        this.backend = backend;
    }

    public void login(Context ctx) {
        if (!backend.databaseConfigured()) {
            throw HttpError.backendNotConfigured();
        }

        Body body = Body.parse(ctx.body());
        if (!body.isString("email")) {
            throw HttpError.badBody();
        }
        String email = body.asString("email");
        if (email.isEmpty() || email.length() > MAX_EMAIL_LENGTH) {
            throw HttpError.badBody();
        }
        if (!body.isString("password")) {
            throw HttpError.badBody();
        }
        String password = body.asString("password");
        if (password.isEmpty() || password.length() > MAX_PASSWORD_LENGTH) {
            throw HttpError.badBody();
        }

        String rateKey = clientIp(ctx) + ":todo-login";
        if (!backend.loginRateLimiter().allow(rateKey)) {
            throw new HttpError(429, "too many attempts, try again later");
        }

        Optional<AuthService.LoginResult> result = backend.auth().login(email, password);
        if (result.isEmpty()) {
            throw new HttpError(401, "invalid credentials");
        }
        AuthService.LoginResult login = result.get();
        if (login.token() == null) {
            // TODO_SESSION_SECRET not set: no verifiable session can be issued.
            throw HttpError.backendNotConfigured();
        }

        Cookie cookie = new Cookie(AuthFilter.COOKIE_NAME, login.token());
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setSameSite(SameSite.LAX);
        cookie.setPath("/");
        cookie.setMaxAge(COOKIE_MAX_AGE_SECONDS);
        ctx.cookie(cookie);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("user", Views.user(login.user()));
        out.put("token", login.token());
        ctx.json(out);
    }

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

    private static String clientIp(Context ctx) {
        String forwarded = ctx.header("x-forwarded-for");
        if (forwarded != null) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        String real = ctx.header("x-real-ip");
        if (real != null && !real.isBlank()) {
            return real.trim();
        }
        return ctx.ip();
    }
}
