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
 * All {@code fun_counters} database access, built on JDBI. Deliberately its
 * own service (not a method on {@link TodoService}, which mirrors the
 * website's queries): the fun counters are a desktop-only concept with no
 * website equivalent. Follows the same fluent-{@code Handle}, raw-SQL,
 * manual-row-mapper style as {@link TodoService}, including reusing its
 * package-private {@link TodoService#isUuid(String)} for id validation.
 */
public final class CountersService {

    private final Jdbi jdbi;

    public CountersService(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    /** All counters ordered by (sort, created_at), the order the UI displays them in. */
    public List<CounterRow> allOrdered() {
        return jdbi.withHandle(h -> h
                .createQuery("SELECT * FROM fun_counters ORDER BY sort ASC, created_at ASC")
                .map((rs, ctx) -> mapCounter(rs))
                .list());
    }

    public Optional<CounterRow> findById(String id) {
        if (!TodoService.isUuid(id)) {
            return Optional.empty();
        }
        return jdbi.withHandle(h -> h
                .createQuery("SELECT * FROM fun_counters WHERE id = CAST(:id AS uuid) LIMIT 1")
                .bind("id", id)
                .map((rs, ctx) -> mapCounter(rs))
                .findFirst());
    }

    /**
     * Inserts a new counter. {@code description} and {@code icon} may be null;
     * {@code value} defaults are the caller's responsibility (the controller
     * defaults an absent value to 0). {@code createdBy} may be null.
     *
     * <p>{@code sort} is always computed here as {@code max(sort) + 1} over the
     * existing rows (0 on an empty table), never left to the column's {@code
     * DEFAULT 0}: a new counter must land after every existing one, not collide
     * with (and lose a tiebreak against) the first seeded counter's sort 0.
     * There is no client-supplied {@code sort} on create; reordering is done
     * exclusively via PATCH, one row at a time (see {@link #update}).
     */
    public CounterRow insert(String label, String description, int value, String icon, String createdBy) {
        return jdbi.withHandle(h -> {
            Update u = h.createUpdate(
                    "INSERT INTO fun_counters (label, description, value, icon, sort, created_by) "
                    + "VALUES (:label, :description, :value, :icon, "
                    + "COALESCE((SELECT MAX(sort) + 1 FROM fun_counters), 0), CAST(:createdBy AS uuid)) RETURNING *");
            u.bind("label", label);
            bindNullable(u, "description", description, Types.VARCHAR);
            u.bind("value", value);
            bindNullable(u, "icon", icon, Types.VARCHAR);
            bindNullable(u, "createdBy", createdBy, Types.VARCHAR);
            return u.executeAndReturnGeneratedKeys().map((rs, ctx) -> mapCounter(rs)).one();
        });
    }

    /**
     * Applies a validated set of column assignments (already normalised by the
     * controller) plus {@code updated_at = now()}. Returns the updated row, or
     * empty when the id is unknown (or not a valid uuid) or the set is empty.
     */
    public Optional<CounterRow> update(String id, List<ColumnValue> sets) {
        if (!TodoService.isUuid(id) || sets == null || sets.isEmpty()) {
            return Optional.empty();
        }

        StringBuilder sql = new StringBuilder("UPDATE fun_counters SET ");
        for (int i = 0; i < sets.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(sets.get(i).column()).append(" = ").append(sets.get(i).placeholder());
        }
        sql.append(", updated_at = :updated_at WHERE id = CAST(:id AS uuid) RETURNING *");

        return jdbi.withHandle(h -> {
            Update u = h.createUpdate(sql.toString());
            u.bind("id", id);
            u.bind("updated_at", Timestamp.from(Instant.now()));
            for (ColumnValue cv : sets) {
                bindNullable(u, cv.column(), cv.value(), cv.sqlType());
            }
            return u.executeAndReturnGeneratedKeys().map((rs, ctx) -> mapCounter(rs)).findFirst();
        });
    }

    /**
     * Relative bump: {@code SET value = value + :delta}, so two concurrent +1s
     * both land instead of racing on a read-modify-write. Returns empty when the
     * id is unknown (or not a valid uuid).
     */
    public Optional<CounterRow> bump(String id, int delta) {
        if (!TodoService.isUuid(id)) {
            return Optional.empty();
        }
        return jdbi.withHandle(h -> h
                .createUpdate("UPDATE fun_counters SET value = value + :delta, updated_at = :updated_at "
                        + "WHERE id = CAST(:id AS uuid) RETURNING *")
                .bind("id", id)
                .bind("delta", delta)
                .bind("updated_at", Timestamp.from(Instant.now()))
                .executeAndReturnGeneratedKeys()
                .map((rs, ctx) -> mapCounter(rs))
                .findFirst());
    }

    public boolean delete(String id) {
        if (!TodoService.isUuid(id)) {
            return false;
        }
        return jdbi.withHandle(h -> h
                .createUpdate("DELETE FROM fun_counters WHERE id = CAST(:id AS uuid)")
                .bind("id", id)
                .execute()) > 0;
    }

    private static void bindNullable(Update u, String name, Object value, int sqlType) {
        if (value == null) {
            u.bindNull(name, sqlType);
        } else {
            u.bind(name, value);
        }
    }

    private CounterRow mapCounter(ResultSet rs) throws SQLException {
        return new CounterRow(
                rs.getString("id"),
                rs.getString("label"),
                rs.getString("description"),
                rs.getInt("value"),
                rs.getString("icon"),
                rs.getInt("sort"),
                rs.getString("created_by"),
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("updated_at")));
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
