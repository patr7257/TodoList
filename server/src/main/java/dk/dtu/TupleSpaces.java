package dk.dtu;

/**
 * Tuple-space naming + structure.
 *
 * Example spaces:
 * - requests: client -> server command tuples
 * - responses: server -> client response tuples
 * - lists: tuples representing list metadata
 * - tasks: tuples representing tasks (possibly grouped by listId)
 */

public final class TupleSpaces {
    // Space names (registered by the server, accessed by clients).
    public static final String REQUESTS = "requests";
    public static final String RESPONSES = "responses";
    public static final String LISTS = "lists";
    public static final String TASKS = "tasks";
    
    // Commands
    public static final String CMD_PING = "ping";
    public static final String CMD_LIST_CREATE = "list_create";
    public static final String CMD_TASK_ADD = "task_add";
    public static final String CMD_TASK_STATUS = "task_status";
    public static final String CMD_TASK_ASSIGN = "task_assign";
    public static final String CMD_LISTS_GET = "lists_get";
    public static final String CMD_TASKS_GET = "tasks_get";

    // Response status (first field in response tuples).
    public static final String RESP_OK = "ok";
    public static final String RESP_ERROR = "error";

    /**
     * We keep tuple arity fixed to make server pattern matching predictable.
     *
     * Request tuple format (6-tuple):
     *   (cmd, requestId, a1, a2, a3, a4)
     *
     * Response tuple format (6-tuple):
     *   (status, requestId, p1, p2, p3, p4)
     */
    public static final int ARITY = 6;

    /*
     * Examples: creating and matching tuples (jSpace)
     * ----------------------------------------------
     *
     * In jSpace, you "create" tuples by calling put(...) with the fields:
     *
     *   // client -> server
     *   requests.put(CMD_PING, requestId, null, null, null, null);
     *
     *   // create list
     *   requests.put(CMD_LIST_CREATE, requestId, listId, userName, null, null);
     *
     *   // add task
     *   requests.put(CMD_TASK_ADD, requestId, listId, taskId, title, owner);
     *
     * The server reads them back using templates (FormalField/ActualField):
     *
     *   Object[] anyReq = requests.get(
     *       new org.jspace.FormalField(String.class),  // cmd
     *       new org.jspace.FormalField(String.class),  // requestId
     *       new org.jspace.FormalField(Object.class),
     *       new org.jspace.FormalField(Object.class),
     *       new org.jspace.FormalField(Object.class),
     *       new org.jspace.FormalField(Object.class)
     *   );
     *
     * Match a specific command:
     *
     *   Object[] pingReq = requests.get(
     *       new org.jspace.ActualField(CMD_PING),
     *       new org.jspace.FormalField(String.class),
     *       new org.jspace.FormalField(Object.class),
     *       new org.jspace.FormalField(Object.class),
     *       new org.jspace.FormalField(Object.class),
     *       new org.jspace.FormalField(Object.class)
     *   );
     */

    // Private constructor: it prevents accidental instantiation.
    private TupleSpaces() {}
}
