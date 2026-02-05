package dk.dtu.shared;

// Shared tuple space names and command/response constants
public final class TupleSpaces {

    // Request tuple format (6-tuple): (cmd, requestId, a1, a2, a3, a4)
    // Response tuple format (6-tuple): (status, requestId, p1, p2, p3, p4)

    // Space names (registered by the server, accessed by clients).
    public static final String REQUESTS = "requests";
    public static final String RESPONSES = "responses";
    public static final String LISTS = "lists";
    public static final String TASKS = "tasks";
    public static final String USERS = "users";
    public static final String NOTIFICATIONS = "notifications";
    
    // Simplified notification - just one type: "data changed"
    public static final String NOTIFY_DATA_CHANGED = "data_changed";
    
    // Commands
    public static final String CMD_PING = "ping";
    public static final String CMD_CLIENT_CONNECT = "client_connect";
    public static final String CMD_CLIENT_DISCONNECT = "client_disconnect";
    public static final String CMD_USER_LOGIN = "user_login";
    public static final String CMD_USER_LOGOUT = "user_logout";
    public static final String CMD_LIST_CREATE = "list_create";
    public static final String CMD_TASK_ADD = "task_add";
    public static final String CMD_TASK_STATUS = "task_status";
    public static final String CMD_TASK_ASSIGN = "task_assign";
    public static final String CMD_LISTS_GET = "lists_get";
    public static final String CMD_TASKS_GET = "tasks_get";
    public static final String CMD_TASK_DUEDATE = "TASK_DUEDATE";
    public static final String CMD_TASK_DELETE = "task_delete";
    public static final String CMD_LIST_DELETE = "list_delete";

    // New commands (2026-02 UI features)
    public static final String CMD_LIST_RENAME = "list_rename";
    public static final String CMD_TASK_RENAME = "task_rename";
    public static final String CMD_LIST_OWNER_SET = "list_owner_set";
    /** Clear list owner (set to empty string). */
    public static final String CMD_LIST_OWNER_CLEAR = "list_owner_clear";
    public static final String CMD_LIST_COLUMNS_SET = "list_columns_set";
    public static final String CMD_USER_DELETE = "user_delete";

    public static final String CMD_LIST_PRIORITY_SET = "list_priority_set";
    public static final String CMD_LIST_YEAR_SET = "list_year_set";
    public static final String CMD_TASK_PRIORITY_SET = "task_priority_set";
    public static final String CMD_TASK_YEAR_SET = "task_year_set";

    public static final String CMD_LIST_LOCATION_SET = "list_location_set";
    public static final String CMD_LIST_DESCRIPTION_SET = "list_description_set";
    public static final String CMD_TASK_LOCATION_SET = "task_location_set";
    public static final String CMD_TASK_DESCRIPTION_SET = "task_description_set";

    /** Clear task owner/assignee (set to empty string). */
    public static final String CMD_TASK_UNASSIGN = "task_unassign";

    /** Persist manual order of lists. a1 = JSON array of listIds in desired order. */
    public static final String CMD_LIST_ORDER_BULK_SET = "list_order_bulk_set";
    /** Persist manual order of tasks within a list. a1 = listId, a2 = JSON array of taskIds in desired order. */
    public static final String CMD_TASK_ORDER_BULK_SET = "task_order_bulk_set";

    // Response status (first field in response tuples).
    public static final String RESP_OK = "ok";
    public static final String RESP_ERROR = "error";

    // Fixed number of fields in request/response tuples
    public static final int ARITY = 6;

    //P revents accidental instantiation.
    private TupleSpaces() {}
}
