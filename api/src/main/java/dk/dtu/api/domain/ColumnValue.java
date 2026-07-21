package dk.dtu.api.domain;

/**
 * One column assignment for a dynamic UPDATE: the column name, the SQL
 * placeholder expression (which may wrap a cast, for example
 * {@code CAST(:status AS todo_status)}), the value (possibly null), and the
 * {@link java.sql.Types} code used to bind a null.
 */
public record ColumnValue(String column, String placeholder, Object value, int sqlType) {
}
