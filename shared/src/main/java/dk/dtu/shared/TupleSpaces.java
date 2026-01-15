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
    public static final String CMD_LIST_CREATE = "list_create";
    public static final String CMD_TASK_ADD = "task_add";
    public static final String CMD_TASK_STATUS = "task_status";
    public static final String CMD_TASK_ASSIGN = "task_assign";
    public static final String CMD_LISTS_GET = "lists_get";
    public static final String CMD_TASKS_GET = "tasks_get";
    public static final String CMD_TASK_DELETE = "task_delete";
    public static final String CMD_LIST_DELETE = "list_delete";

    // Response status (first field in response tuples).
    public static final String RESP_OK = "ok";
    public static final String RESP_ERROR = "error";

    // Fixed number of fields in request/response tuples
    public static final int ARITY = 6;

    //P revents accidental instantiation.
    private TupleSpaces() {}
}
