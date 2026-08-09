package dk.dtu.api.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dk.dtu.api.auth.AuthFilter;
import dk.dtu.api.domain.ShareRow;
import dk.dtu.api.domain.SharesService;

import io.javalin.http.Context;

/**
 * GET|POST /api/todo/lists/{id}/shares and DELETE
 * /api/todo/lists/{id}/shares/{shareId}: managing a list's public share links
 * (issue #52). Auto-protected by the global {@link AuthFilter}, which exempts
 * only login, logout, and the singular public {@code /api/todo/share/} prefix.
 *
 * <p>GET returns only LIVE shares ({@code {"shares":[...]}}): a revoked or
 * expired link is not something anyone can act on, so listing it would only
 * invite someone to copy a dead URL.
 */
public final class ListSharesController {

    private static final int MAX_LABEL_LENGTH = 200;

    private final Backend backend;

    public ListSharesController(Backend backend) {
        this.backend = backend;
    }

    public void list(Context ctx) {
        SharesService shares = requireBackend();
        String listId = ctx.pathParam("id");

        List<Map<String, Object>> out = new ArrayList<>();
        for (ShareRow s : shares.activeForList(listId)) {
            out.add(ShareViews.share(s, backend.config().shareBaseUrl()));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("shares", out);
        ctx.json(body);
    }

    public void create(Context ctx) {
        SharesService shares = requireBackend();
        String listId = ctx.pathParam("id");

        // The body is optional in spirit but not in transport: an empty body is
        // a 400 from Body.parse, matching every other write route here, so
        // clients send at least "{}".
        Body body = Body.parse(ctx.body());
        String label = null;
        if (body.has("label") && !body.isNull("label")) {
            if (!body.isString("label")) {
                throw HttpError.badBody();
            }
            String raw = body.asString("label");
            if (raw.length() > MAX_LABEL_LENGTH) {
                throw HttpError.badBody();
            }
            String trimmed = raw.trim();
            label = trimmed.isEmpty() ? null : trimmed;
        }

        String uid = ctx.attribute(AuthFilter.UID_ATTRIBUTE);
        Optional<ShareRow> created = shares.create(listId, label, uid);
        if (created.isEmpty()) {
            // Unknown (or non-uuid) list id. The service checks the list up
            // front precisely so this is a clean 404 and not a foreign-key
            // violation surfacing as a 500.
            throw HttpError.notFound();
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("share", ShareViews.share(created.get(), backend.config().shareBaseUrl()));
        ctx.json(out);
    }

    public void delete(Context ctx) {
        SharesService shares = requireBackend();
        String listId = ctx.pathParam("id");
        String shareId = ctx.pathParam("shareId");

        // Revoking twice is a 404, not a silent success: "already revoked" and
        // "just revoked" look identical to a UI otherwise, and a stale tab
        // would report a revocation that did nothing.
        if (!shares.revoke(listId, shareId)) {
            throw HttpError.notFound();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        ctx.json(out);
    }

    private SharesService requireBackend() {
        if (!backend.databaseConfigured()) {
            throw HttpError.backendNotConfigured();
        }
        return backend.shares();
    }
}
