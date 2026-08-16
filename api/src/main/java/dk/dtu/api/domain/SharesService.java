package dk.dtu.api.domain;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.Update;

/**
 * All {@code list_shares} database access (issue #52), built on JDBI.
 *
 * <p>Deliberately its own service rather than methods on {@link TodoService},
 * for the same reason {@link CountersService} is: {@code TodoService} mirrors
 * the website's queries and is the hottest file in the repo. Shares are a
 * separate resource family with a separate (unauthenticated) reader, so they
 * own their SQL here, including their own {@link #itemsForList(String)} read.
 * That query duplicates a couple of lines of {@code TodoService.allItemsOrdered}
 * on purpose: the duplication is cheaper than coupling the public, world
 * readable path to the file every other work stream edits.
 *
 * <p>Follows the same fluent-{@code Handle}, raw-SQL, manual-row-mapper style
 * as {@link TodoService}, including reusing its package-private
 * {@link TodoService#isUuid(String)} for id validation.
 */
public final class SharesService {

    private final Jdbi jdbi;

    public SharesService(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    /**
     * A share that resolved to a live list: the share's own id and expiry, plus
     * the list it points at. Only ever produced by {@link #resolveActive(String)},
     * so holding one is proof the token was valid, unrevoked and unexpired.
     */
    public record ActiveShare(String shareId, Instant expiresAt, ListRow list) {
    }

    /**
     * Resolves a token to its list in ONE query: token match, not revoked, not
     * expired, and the list still exists are all conditions of the same
     * statement.
     *
     * <p>That is the whole point. Splitting this into "look up the share, then
     * check revoked, then check expiry, then load the list" would give four
     * branches that can drift apart, and would leak which branch failed through
     * response timing. One query means every failure is the same failure, and
     * the caller has exactly one thing to do with an empty result: 404.
     *
     * <p>The share's columns are explicitly aliased because {@code lists} also
     * has {@code id} and {@code created_at}: without the aliases a JDBC
     * {@code getString("id")} would silently read the wrong column.
     */
    public Optional<ActiveShare> resolveActive(String token) {
        if (token == null || token.isEmpty()) {
            return Optional.empty();
        }
        return jdbi.withHandle(h -> h
                .createQuery("""
                        SELECT s.id AS share_id, s.expires_at AS share_expires_at, l.*
                        FROM list_shares s
                        JOIN lists l ON l.id = s.list_id
                        WHERE s.token = :token
                          AND s.revoked_at IS NULL
                          AND (s.expires_at IS NULL OR s.expires_at > now())
                        LIMIT 1
                        """)
                .bind("token", token)
                .map((rs, ctx) -> new ActiveShare(
                        rs.getString("share_id"),
                        instant(rs.getTimestamp("share_expires_at")),
                        mapList(rs)))
                .findFirst());
    }

    /**
     * The items of one list, in the same (sort, created_at) order the desktop
     * and web clients see, so the shared view cannot present a different order
     * from the owner's own view.
     */
    public List<ItemRow> itemsForList(String listId) {
        if (!TodoService.isUuid(listId)) {
            return List.of();
        }
        return jdbi.withHandle(h -> h
                .createQuery("SELECT * FROM items WHERE list_id = CAST(:listId AS uuid) "
                        + "ORDER BY sort ASC, created_at ASC")
                .bind("listId", listId)
                .map((rs, ctx) -> mapItem(rs))
                .list());
    }

    /**
     * Records a view of a share, throttled to at most one bump per five minutes
     * per link.
     *
     * <p>Both the increment and the throttle live in SQL: {@code view_count =
     * view_count + 1} guarded by a {@code last_viewed_at} predicate, never a
     * read-modify-write, so two concurrent viewers cannot lose a count against
     * each other. The throttle exists because a shared page is polled and
     * refreshed; without it the counter would measure page reloads rather than
     * visits.
     *
     * <p>Returns true when a row was actually bumped (useful to tests, ignored
     * by the controller: a failed bump must never affect the response).
     */
    public boolean recordView(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        return jdbi.withHandle(h -> h
                .createUpdate("UPDATE list_shares SET view_count = view_count + 1, last_viewed_at = now() "
                        + "WHERE token = :token "
                        + "AND (last_viewed_at IS NULL OR last_viewed_at < now() - interval '5 minutes')")
                .bind("token", token)
                .execute()) > 0;
    }

    /** The live (not revoked, not expired) shares of one list, oldest first. */
    public List<ShareRow> activeForList(String listId) {
        if (!TodoService.isUuid(listId)) {
            return List.of();
        }
        return jdbi.withHandle(h -> h
                .createQuery("SELECT * FROM list_shares WHERE list_id = CAST(:listId AS uuid) "
                        + "AND revoked_at IS NULL AND (expires_at IS NULL OR expires_at > now()) "
                        + "ORDER BY created_at ASC")
                .bind("listId", listId)
                .map((rs, ctx) -> mapShare(rs))
                .list());
    }

    /**
     * Mints a share for a list. Returns empty when the list id is unknown (or
     * not a uuid), so the controller can answer 404 instead of letting a
     * foreign-key violation surface as a 500.
     *
     * <p>{@code label} and {@code createdBy} may be null. So may
     * {@code expiresInDays}, which means "never expires" and is the default.
     *
     * <p>The caller passes a NUMBER OF DAYS, not a timestamp, and the deadline
     * is computed as {@code now() + interval} inside the INSERT. That is
     * deliberate: the read path in {@link #resolveActive(String)} compares
     * {@code expires_at} against the database's {@code now()}, so deriving the
     * deadline from any other clock (a browser's, a phone's, the API
     * container's) would let the two disagree. One clock decides both when a
     * link dies and whether it is dead.
     */
    public Optional<ShareRow> create(String listId, String label, String createdBy, Integer expiresInDays) {
        if (!TodoService.isUuid(listId)) {
            return Optional.empty();
        }
        boolean listExists = jdbi.withHandle(h -> h
                .createQuery("SELECT 1 FROM lists WHERE id = CAST(:id AS uuid) LIMIT 1")
                .bind("id", listId)
                .mapTo(Integer.class)
                .findFirst()
                .isPresent());
        if (!listExists) {
            return Optional.empty();
        }

        return jdbi.withHandle(h -> {
            // The expiry expression is chosen from a fixed pair of literals
            // rather than interpolated, so no caller-supplied text ever reaches
            // the SQL string; the day count itself is a bound parameter.
            String expiresExpr = expiresInDays == null
                    ? "NULL"
                    : "now() + make_interval(days => :expiresInDays)";
            Update u = h.createUpdate(
                    "INSERT INTO list_shares (list_id, token, label, created_by, expires_at) "
                    + "VALUES (CAST(:listId AS uuid), :token, :label, CAST(:createdBy AS uuid), "
                    + expiresExpr + ") RETURNING *");
            u.bind("listId", listId);
            u.bind("token", ShareTokens.generate());
            bindNullable(u, "label", label, Types.VARCHAR);
            bindNullable(u, "createdBy", createdBy, Types.VARCHAR);
            if (expiresInDays != null) {
                u.bind("expiresInDays", expiresInDays.intValue());
            }
            return u.executeAndReturnGeneratedKeys().map((rs, ctx) -> mapShare(rs)).findFirst();
        });
    }

    /**
     * Backwards-compatible overload: a share that never expires. Kept so the
     * three-argument call reads as an explicit "no expiry" rather than a
     * trailing null nobody can interpret at the call site.
     */
    public Optional<ShareRow> create(String listId, String label, String createdBy) {
        return create(listId, label, createdBy, null);
    }

    /**
     * Revokes one share of one list. Returns false when the share is unknown,
     * belongs to a different list, or was already revoked, so the controller
     * answers 404 rather than pretending a no-op succeeded.
     *
     * <p>Scoping the UPDATE by {@code list_id} as well as {@code id} is
     * deliberate: it makes a mismatched (list, share) pair impossible to act
     * on, so a caller cannot revoke someone else's share by guessing its id
     * through a list they can see.
     */
    public boolean revoke(String listId, String shareId) {
        if (!TodoService.isUuid(listId) || !TodoService.isUuid(shareId)) {
            return false;
        }
        return jdbi.withHandle(h -> h
                .createUpdate("UPDATE list_shares SET revoked_at = now() "
                        + "WHERE id = CAST(:shareId AS uuid) AND list_id = CAST(:listId AS uuid) "
                        + "AND revoked_at IS NULL")
                .bind("shareId", shareId)
                .bind("listId", listId)
                .execute()) > 0;
    }

    /**
     * The display name of a list's owner, resolved through {@code owner_id}.
     * Null when the list has no owner_id, or when it points at a user whose
     * name is null. The free-text {@code lists.owner} column is deliberately
     * NOT used as a fallback: it is a denormalized display value that the
     * backfill left unresolved in the ambiguous cases, and the public payload
     * should say nothing rather than say something wrong.
     */
    public String ownerNameFor(ListRow list) {
        if (list == null || list.ownerId() == null || !TodoService.isUuid(list.ownerId())) {
            return null;
        }
        return jdbi.withHandle(h -> h
                .createQuery("SELECT name FROM users WHERE id = CAST(:id AS uuid) LIMIT 1")
                .bind("id", list.ownerId())
                .mapTo(String.class)
                .findFirst()
                .orElse(null));
    }

    // -- row mappers -----------------------------------------------------------

    private static void bindNullable(Update u, String name, Object value, int sqlType) {
        if (value == null) {
            u.bindNull(name, sqlType);
        } else {
            u.bind(name, value);
        }
    }

    private ShareRow mapShare(ResultSet rs) throws SQLException {
        return new ShareRow(
                rs.getString("id"),
                rs.getString("list_id"),
                rs.getString("token"),
                rs.getString("label"),
                rs.getString("created_by"),
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("expires_at")),
                instant(rs.getTimestamp("revoked_at")),
                instant(rs.getTimestamp("last_viewed_at")),
                rs.getInt("view_count"));
    }

    private ListRow mapList(ResultSet rs) throws SQLException {
        return new ListRow(
                rs.getString("id"),
                rs.getString("name"),
                rs.getInt("sort"),
                instant(rs.getTimestamp("created_at")),
                rs.getString("owner"),
                nullableInt(rs, "priority"),
                nullableInt(rs, "year"),
                rs.getString("location"),
                rs.getString("description"),
                rs.getString("task_columns_json"),
                rs.getString("owner_id"));
    }

    private ItemRow mapItem(ResultSet rs) throws SQLException {
        return new ItemRow(
                rs.getString("id"),
                rs.getString("list_id"),
                rs.getString("text"),
                rs.getString("description"),
                rs.getBoolean("done"),
                rs.getString("status"),
                nullableInt(rs, "priority"),
                instant(rs.getTimestamp("due_at")),
                rs.getString("location"),
                rs.getString("assignee_id"),
                rs.getInt("sort"),
                rs.getString("created_by"),
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("updated_at")),
                nullableInt(rs, "year"));
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private static Integer nullableInt(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }
}
