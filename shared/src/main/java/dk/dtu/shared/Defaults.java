package dk.dtu.shared;

/**
 * Shared default values used across server, client, and shared persistence.
 */
public final class Defaults {

    /** Default visible task columns for new lists (JSON array string). */
    public static final String TASK_COLUMNS_JSON = "[\"reorder\",\"title\",\"status\",\"dueDate\",\"owner\",\"delete\"]";

    /** Default priority for lists/tasks when unset. */
    public static final int PRIORITY = 5;

    /** Default year field for lists/tasks when unset. */
    public static final int YEAR = 0;

    private Defaults() {
    }
}
