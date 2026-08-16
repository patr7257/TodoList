package dk.dtu.api.domain;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

    /** All users, id + name only, ordered by name (assignee dropdown source). */
    public List<UserRow> allUsersByName() {
        return jdbi.withHandle(h -> h
                .createQuery("SELECT id, name FROM users ORDER BY name ASC")
                .map((rs, ctx) -> new UserRow(rs.getString("id"), null, rs.getString("name"), null))
                .list());
    }

    // -- lists -----------------------------------------------------------------

    public List<ListRow> allListsOrdered() {
        return jdbi.withHandle(h -> h
                .createQuery("SELECT * FROM lists ORDER BY sort ASC, created_at ASC")
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

    public List<ItemRow> allItemsOrdered() {
        return jdbi.withHandle(h -> h
                .createQuery("SELECT * FROM items ORDER BY sort ASC, created_at ASC")
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
