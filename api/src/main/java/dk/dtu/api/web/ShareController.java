package dk.dtu.api.web;

import java.util.List;
import java.util.Optional;

import dk.dtu.api.domain.ItemRow;
import dk.dtu.api.domain.ShareTokens;
import dk.dtu.api.domain.SharesService;

import io.javalin.http.Context;

/**
 * GET /api/todo/share/{token}: the ONE unauthenticated route in this API
 * (issue #52). It returns a read-only view of a single list to whoever holds
 * the token.
 *
 * <p>The path segment is {@code share}, singular, on purpose. The global
 * {@link dk.dtu.api.auth.AuthFilter} exempts the prefix {@code
 * /api/todo/share/}, and {@code share} singular appears in exactly one path in
 * the whole API, so that exemption cannot accidentally open a management route
 * (those live under {@code .../lists/{id}/shares}, plural).
 *
 * <p>Every failure mode answers the same 404 with the same body: unknown,
 * malformed, revoked and expired tokens are indistinguishable from outside.
 * There is nothing to learn by probing.
 *
 * <p><b>Rule for any future share-scoped WRITE</b> (a "guests can tick items"
 * feature, say): the token authorizes exactly ONE list, so any such handler
 * must verify {@code item.list_id = share.list_id} before touching anything. A
 * write keyed only on the item id would let a token holder edit every list in
 * the database.
 */
public final class ShareController {

    private final Backend backend;

    public ShareController(Backend backend) {
        this.backend = backend;
    }

    public void get(Context ctx) {
        // Rate limit FIRST, before any work and before the token is even
        // examined: this route is the public front door, so the cheapest
        // possible response to a brute-force sweep is the goal. The key is the
        // client IP alone (not IP plus token) so that trying a million
        // different tokens still consumes one bucket.
        String rateKey = ClientIp.of(ctx) + ":todo-share";
        if (!backend.shareRateLimiter().allow(rateKey)) {
            throw new HttpError(429, "too many requests, try again later");
        }

        // AuthFilter steps aside entirely when the database is unconfigured, so
        // unlike the authenticated controllers this one is reached with null
        // services and has to answer 503 itself rather than NPE into a 500.
        if (!backend.databaseConfigured()) {
            throw HttpError.backendNotConfigured();
        }
        SharesService shares = backend.shares();

        String token = ctx.pathParam("token");
        // Cheap shape guard so junk never reaches Postgres. Same 404 as an
        // unknown token: a caller must not be able to tell "wrong shape" from
        // "no such share".
        if (!ShareTokens.isWellFormed(token)) {
            throw HttpError.notFound();
        }

        Optional<SharesService.ActiveShare> active = shares.resolveActive(token);
        if (active.isEmpty()) {
            throw HttpError.notFound();
        }
        SharesService.ActiveShare share = active.get();

        // Best-effort analytics, throttled in SQL. Deliberately before the read
        // and deliberately not checked: a view count is never a reason to fail
        // a page load.
        shares.recordView(token);

        List<ItemRow> items = shares.itemsForList(share.list().id());
        String sharedBy = shares.ownerNameFor(share.list());
        ctx.json(ShareViews.publicPayload(share.list(), items, sharedBy, share.expiresAt()));
    }
}
