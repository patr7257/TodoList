package dk.dtu.api.web;

import io.javalin.http.Context;

/**
 * Resolves the caller's IP for rate-limiting keys.
 *
 * <p>The API sits behind Dokploy's Traefik reverse proxy, so {@code ctx.ip()}
 * alone is the proxy's address and would put every caller in one shared bucket.
 * The order is {@code x-forwarded-for} (first entry, the original client),
 * then {@code x-real-ip}, then the socket address as a last resort.
 *
 * <p>Extracted from {@link AuthController} so the login limiter and the public
 * share limiter key on the SAME value. Two copies of this would eventually
 * disagree, and a limiter that keys differently from the one next to it is a
 * limiter that can be bypassed by picking the right endpoint.
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String of(Context ctx) {
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
