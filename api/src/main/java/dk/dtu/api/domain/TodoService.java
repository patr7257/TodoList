package dk.dtu.api.domain;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.Update;

/**
 * All todolist database access, built on JDBI. Queries mirror the website's
 * Drizzle queries: lists and items are ordered by (sort, created_at), users by
 * name. The desktop-superset columns are selected too, so callers can surface
 * them (null when unset).
 */
public final class TodoService {

    private final Jdbi jdbi;

    public TodoService(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    // -- users -----------------------------------------------------------------

    public Optional<UserRow> findUserByEmail(String email) {
        return jdbi.withHandle(h -> h
                .createQuery("SELECT * FROM users WHERE email = :email LIMIT 1")
                .bind("email", email)
                .map((rs, ctx) -> mapUser(rs))
                .findFirst());
    }

    public Optional<UserRow> findUserById(String id) {
        if (!isUuid(id)) {
            return Optional.empty();
        }
        return jdbi.withHandle(h -> h
                .createQuery("SELECT * FROM users WHERE id = CAST(:id AS uuid) LIMIT 1")
                .bind("id", id)
                .map((rs, ctx) -> mapUser(rs))
                .findFirst());
    }

    /**
     * The user's current {@code users.token_version} (V10, issue #74), or empty
     * when the id is unknown or not a uuid.
     *
     * <p>Empty is deliberately NOT the same as zero: an unknown id must fail a
     * version comparison rather than quietly pass it, so the caller treats
     * empty as "reject" instead of defaulting.
     */
    public OptionalInt tokenVersion(String userId) {
        if (!isUuid(userId)) {
            return OptionalInt.empty();
        }
        Optional<Integer> found = jdbi.withHandle(h -> h
                .createQuery("SELECT token_version FROM users WHERE id = CAST(:id AS uuid)")
                .bind("id", userId)
                .mapTo(Integer.class)
                .findFirst());
        return found.isPresent() ? OptionalInt.of(found.get()) : OptionalInt.empty();
    }

    /** All users, id + name only, ordered by name (assignee dropdown source). */
    public List<UserRow> allUsersByName() {
        return jdbi.withHandle(h -> h
                .createQuery("SELECT id, name FROM users ORDER BY name ASC")
                .map((rs, ctx) -> new UserRow(rs.getString("id"), null, rs.getString("name"), null))
                .list());
    }

    // -- lists -----------------------------------------------------------------

    /**
     * Every list in the caller's own order (issue #77): the V9 {@code list_order}
     * override when that user has one, the baseline {@code lists.sort} column
     * when they never reordered, then {@code created_at} as the tie-break.
     *
     * <p>The resolved value is served through the EXISTING {@code sort} field,
     * which is the whole point of the design: GET /api/todo/state gains no key,
     * so the append-only contract and its ViewsTest guard do not move, and the
     * website's read path needs no change at all.
     *
     * <p>The columns are listed out rather than selected as {@code l.*} because
     * {@code l.*} plus {@code COALESCE(...) AS sort} yields TWO result columns
     * labelled {@code sort}, and {@code ResultSet.getInt("sort")} then returns
     * the first one, which is the unresolved baseline. Naming the columns is
     * the version of this that cannot silently read the wrong one.
     *
     * <p>A uid that is null or not a uuid binds as NULL, so no override matches
     * and every caller falls back to the baseline order rather than getting a
     * 500 out of a failed cast.
     */
    public List<ListRow> allListsOrdered(String uid) {
        return jdbi.withHandle(h -> h
                .createQuery("SELECT l.id, l.name, COALESCE(lo.sort, l.sort) AS sort, l.created_at, "
                        + "l.owner, l.priority, l.year, l.location, l.description, "
                        + "l.task_columns_json, l.owner_id "
                        + "FROM lists l "
                        + "LEFT JOIN list_order lo ON lo.list_id = l.id AND lo.user_id = CAST(:uid AS uuid) "
                        + "ORDER BY COALESCE(lo.sort, l.sort) ASC, l.created_at ASC")
                .bind("uid", uuidOrNull(uid))
                .map((rs, ctx) -> mapList(rs))
                .list());
    }

    public ListRow insertList(String name) {
        return insertList(name, null, null);
    }

    /**
     * Inserts a list with an optional owner (a desktop-superset column). A null
     * owner leaves the column NULL, matching a website-created list.
     */
    public ListRow insertList(String name, String owner) {
        return insertList(name, owner, null);
    }

    /**
     * Inserts a list with an optional legacy owner name and an optional real
     * {@code ownerId} (a V3 column, references {@code users.id}). Callers are
     * expected to have already resolved/validated ownerId (see
     * {@link #findUserById(String)}) before calling this.
     */
    public ListRow insertList(String name, String owner, String ownerId) {
        return jdbi.withHandle(h -> {
            Update u = h.createUpdate(
                    "INSERT INTO lists (name, owner, owner_id) VALUES (:name, :owner, CAST(:ownerId AS uuid)) RETURNING *");
            u.bind("name", name);
            bindNullable(u, "owner", owner, Types.VARCHAR);
            bindNullable(u, "ownerId", ownerId, Types.VARCHAR);
            return u.executeAndReturnGeneratedKeys().map((rs, ctx) -> mapList(rs)).one();
        });
    }

    public boolean listExists(String id) {
        if (!isUuid(id)) {
            return false;
        }
        return jdbi.withHandle(h -> h
                .createQuery("SELECT 1 FROM lists WHERE id = CAST(:id AS uuid) LIMIT 1")
                .bind("id", id)
                .mapTo(Integer.class)
                .findFirst()
                .isPresent());
    }

    /**
     * Applies a validated set of list column assignments (already normalised by
     * the controller). Returns the updated row, or empty when the id is unknown
     * (or not a valid uuid) or the set is empty.
     */
    public Optional<ListRow> updateList(String id, List<ColumnValue> sets) {
        if (!isUuid(id) || sets == null || sets.isEmpty()) {
            return Optional.empty();
        }
        return runUpdateReturning("lists", id, sets, this::mapList);
    }

    public boolean deleteList(String id) {
        if (!isUuid(id)) {
            return false;
        }
        return jdbi.withHandle(h -> h
                .createUpdate("DELETE FROM lists WHERE id = CAST(:id AS uuid)")
                .bind("id", id)
                .execute()) > 0;
    }

    // -- items -----------------------------------------------------------------

    /**
     * Every item in the caller's own order, resolved from the V9
     * {@code item_order} override exactly as {@link #allListsOrdered(String)}
     * resolves lists, and subject to the same two notes: the columns are named
     * so the resolved {@code sort} is the only one in the result set, and a
     * non-uuid uid simply matches no override.
     */
    public List<ItemRow> allItemsOrdered(String uid) {
        return jdbi.withHandle(h -> h
                .createQuery("SELECT i.id, i.list_id, i.text, i.description, i.done, i.status, "
                        + "i.priority, i.due_at, i.location, i.assignee_id, "
                        + "COALESCE(io.sort, i.sort) AS sort, i.created_by, i.created_at, "
                        + "i.updated_at, i.year "
                        + "FROM items i "
                        + "LEFT JOIN item_order io ON io.item_id = i.id AND io.user_id = CAST(:uid AS uuid) "
                        + "ORDER BY COALESCE(io.sort, i.sort) ASC, i.created_at ASC")
                .bind("uid", uuidOrNull(uid))
                .map((rs, ctx) -> mapItem(rs))
                .list());
    }

    public ItemRow insertItem(NewItem in) {
        return jdbi.withHandle(h -> {
            Update u = h.createUpdate(
                    "INSERT INTO items (list_id, text, description, status, done, priority, due_at, location, assignee_id, created_by) "
                    + "VALUES (CAST(:listId AS uuid), :text, :description, CAST(:status AS todo_status), :done, "
                    + ":priority, :dueAt, :location, CAST(:assigneeId AS uuid), CAST(:createdBy AS uuid)) RETURNING *");
            u.bind("listId", in.listId());
            u.bind("text", in.text());
            bindNullable(u, "description", in.description(), Types.VARCHAR);
            u.bind("status", in.status());
            u.bind("done", "DONE".equals(in.status()));
            bindNullable(u, "priority", in.priority(), Types.SMALLINT);
            bindNullable(u, "dueAt", in.dueAt() == null ? null : Timestamp.from(in.dueAt()), Types.TIMESTAMP);
            bindNullable(u, "location", in.location(), Types.VARCHAR);
            bindNullable(u, "assigneeId", in.assigneeId(), Types.VARCHAR);
            bindNullable(u, "createdBy", in.createdBy(), Types.VARCHAR);
            return u.executeAndReturnGeneratedKeys().map((rs, ctx) -> mapItem(rs)).one();
        });
    }

    /**
     * Applies a validated set of item column assignments (already normalised by
     * the controller) plus updated_at = now(). Returns the updated row, or empty
     * when the id is unknown.
     */
    public Optional<ItemRow> updateItem(String id, List<ColumnValue> sets) {
        if (!isUuid(id)) {
            return Optional.empty();
        }
        List<ColumnValue> all = new ArrayList<>(sets);
        all.add(new ColumnValue("updated_at", ":updated_at", Timestamp.from(Instant.now()), Types.TIMESTAMP));
        return runUpdateReturning("items", id, all, this::mapItem);
    }

    public boolean deleteItem(String id) {
        if (!isUuid(id)) {
            return false;
        }
        return jdbi.withHandle(h -> h
                .createUpdate("DELETE FROM items WHERE id = CAST(:id AS uuid)")
                .bind("id", id)
                .execute()) > 0;
    }

    // -- per-user ordering (issue #77) -----------------------------------------

    /** One row of a bulk reorder: which resource, and where the caller put it. */
    public record SortEntry(String id, int sort) {
    }

    /**
     * Upserts the caller's whole list ordering in ONE transaction, writing only
     * into {@code list_order} and only on the caller's own user_id, so a request
     * cannot move anyone else's arrangement no matter what it contains.
     *
     * <p>Returns the number of rows written, or empty when the uid is not a uuid
     * or ANY id in the batch is unknown. In the unknown-id case nothing at all is
     * written: the ids are checked first, inside the same transaction, so a bad
     * row halfway down the array cannot leave half an arrangement behind. That is
     * the failure the single-row PATCH path had, where the website fired N
     * independent requests and a half applied reorder was a real outcome.
     */
    public OptionalInt saveListOrder(String uid, List<SortEntry> order) {
        return saveOrder("list_order", "list_id", "lists", uid, order);
    }

    /** The items half of {@link #saveListOrder(String, List)}, same contract. */
    public OptionalInt saveItemOrder(String uid, List<SortEntry> order) {
        return saveOrder("item_order", "item_id", "items", uid, order);
    }

    /**
     * The shared body of the two methods above. {@code overrideTable},
     * {@code idColumn} and {@code resourceTable} are compile-time constants from
     * the two call sites and never request data, so interpolating them into the
     * SQL is safe; every value in the statement is bound.
     */
    private OptionalInt saveOrder(String overrideTable, String idColumn, String resourceTable,
                                  String uid, List<SortEntry> order) {
        if (!isUuid(uid)) {
            return OptionalInt.empty();
        }
        List<SortEntry> entries = order == null ? List.of() : order;
        for (SortEntry e : entries) {
            if (e == null || !isUuid(e.id())) {
                return OptionalInt.empty();
            }
        }
        if (entries.isEmpty()) {
            return OptionalInt.of(0);
        }

        return jdbi.inTransaction(h -> {
            List<UUID> ids = entries.stream().map(e -> UUID.fromString(e.id())).distinct().toList();
            int known = h.createQuery("SELECT COUNT(*) FROM " + resourceTable + " WHERE id IN (<ids>)")
                    .bindList("ids", ids)
                    .mapTo(Integer.class)
                    .one();
            if (known != ids.size()) {
                return OptionalInt.empty();
            }
            // Row by row rather than one multi-VALUES statement: a batch that
            // names the same id twice would make a single statement fail with
            // "cannot affect row a second time", and last-one-wins is the
            // friendlier answer for a drag that produced a duplicate.
            for (SortEntry e : entries) {
                h.createUpdate("INSERT INTO " + overrideTable + " (user_id, " + idColumn + ", sort) "
                                + "VALUES (CAST(:uid AS uuid), CAST(:id AS uuid), :sort) "
                                + "ON CONFLICT (user_id, " + idColumn + ") "
                                + "DO UPDATE SET sort = EXCLUDED.sort")
                        .bind("uid", uid)
                        .bind("id", e.id())
                        .bind("sort", e.sort())
                        .execute();
            }
            return OptionalInt.of(entries.size());
        });
    }

    // -- dynamic update helper -------------------------------------------------

    private <T> Optional<T> runUpdateReturning(String table, String id, List<ColumnValue> sets,
                                               RowMapper<T> mapper) {
        StringBuilder sql = new StringBuilder("UPDATE ").append(table).append(" SET ");
        for (int i = 0; i < sets.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(sets.get(i).column()).append(" = ").append(sets.get(i).placeholder());
        }
        sql.append(" WHERE id = CAST(:id AS uuid) RETURNING *");

        return jdbi.withHandle(h -> {
            Update u = h.createUpdate(sql.toString());
            u.bind("id", id);
            for (ColumnValue cv : sets) {
                bindNullable(u, cv.column(), cv.value(), cv.sqlType());
            }
            return u.executeAndReturnGeneratedKeys().map((rs, ctx) -> mapper.map(rs)).findFirst();
        });
    }

    private static void bindNullable(Update u, String name, Object value, int sqlType) {
        if (value == null) {
            u.bindNull(name, sqlType);
        } else {
            u.bind(name, value);
        }
    }

    // -- row mappers -----------------------------------------------------------

    private UserRow mapUser(ResultSet rs) throws SQLException {
        return new UserRow(
                rs.getString("id"),
                rs.getString("email"),
                rs.getString("name"),
                instant(rs.getTimestamp("created_at")));
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

    /** The value itself when it is a uuid, else null (binds as a NULL uuid). */
    private static String uuidOrNull(String s) {
        return isUuid(s) ? s : null;
    }

    static boolean isUuid(String s) {
        if (s == null) {
            return false;
        }
        try {
            UUID.fromString(s);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @FunctionalInterface
    private interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }
}
