package dk.dtu;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dk.dtu.shared.Defaults;
import dk.dtu.shared.TupleSpaces;
import java.util.List;
import java.util.UUID;
import java.lang.reflect.Type;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jspace.ActualField;
import org.jspace.FormalField;
import org.jspace.Space;

// Backend service handling requests and responses.
public class ServerHandlerService implements Runnable {
    private final Space todoLists;
    private final Space counter;
    private final Space users;
    private final Space tasks;
    private final Space requests;
    private final Space responses;
    private final Space notifications;
    private final PersistenceService persistenceService;

    private static final int DEFAULT_ORDER_INDEX = 0;

    private static final long AUTOSAVE_DEBOUNCE_MS = 250;

    private static final Gson GSON = new Gson();
    private static final Type STRING_LIST_TYPE = new TypeToken<List<String>>() {}.getType();

    private final ScheduledExecutorService autosaveExecutor;
    private final AtomicBoolean autosaveScheduled = new AtomicBoolean(false);

    public ServerHandlerService(Space todoLists, Space counter, Space users, Space tasks, Space requests,
            Space responses, Space notifications, PersistenceService persistenceService) {
        this.todoLists = todoLists;
        this.counter = counter;
        this.users = users;
        this.tasks = tasks;
        this.requests = requests;
        this.responses = responses;
        this.notifications = notifications;
        this.persistenceService = persistenceService;

        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "autosave");
            t.setDaemon(true);
            return t;
        };
        this.autosaveExecutor = Executors.newSingleThreadScheduledExecutor(tf);
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Object[] tuple = requests.get(
                        new FormalField(String.class),
                        new FormalField(String.class),
                        new FormalField(Object.class),
                        new FormalField(Object.class),
                        new FormalField(Object.class),
                        new FormalField(Object.class));

                Request req = Request.fromTuple(tuple);
                switch (req.cmd()) {
                    case TupleSpaces.CMD_PING -> handlePing(req);
                    case TupleSpaces.CMD_CLIENT_CONNECT -> handleClientConnect(req);
                    case TupleSpaces.CMD_CLIENT_DISCONNECT -> handleClientDisconnect(req);
                    case TupleSpaces.CMD_USER_LOGIN -> handleUserLogin(req);
                    case TupleSpaces.CMD_USER_LOGOUT -> handleUserLogout(req);
                    case TupleSpaces.CMD_LIST_CREATE -> handleListCreate(req);
                    case TupleSpaces.CMD_TASK_ADD -> handleTaskAdd(req);
                    case TupleSpaces.CMD_TASK_STATUS -> handleTaskStatus(req);
                    case TupleSpaces.CMD_TASK_ASSIGN -> handleTaskAssign(req);
                    case TupleSpaces.CMD_TASK_UNASSIGN -> handleTaskUnassign(req);
                    case TupleSpaces.CMD_LISTS_GET -> handleListsGet(req);
                    case TupleSpaces.CMD_TASKS_GET -> handleTasksGet(req);
                    case TupleSpaces.CMD_TASK_DUEDATE -> handleTaskDueDate(req);
                    case TupleSpaces.CMD_TASK_DELETE -> handleTaskDelete(req);
                    case TupleSpaces.CMD_LIST_DELETE -> handleListDelete(req);
                    case TupleSpaces.CMD_LIST_RENAME -> handleListRename(req);
                    case TupleSpaces.CMD_TASK_RENAME -> handleTaskRename(req);
                    case TupleSpaces.CMD_LIST_OWNER_SET -> handleListOwnerSet(req);
                    case TupleSpaces.CMD_LIST_OWNER_CLEAR -> handleListOwnerClear(req);
                    case TupleSpaces.CMD_LIST_COLUMNS_SET -> handleListColumnsSet(req);
                    case TupleSpaces.CMD_USER_DELETE -> handleUserDelete(req);
                    case TupleSpaces.CMD_LIST_PRIORITY_SET -> handleListPrioritySet(req);
                    case TupleSpaces.CMD_TASK_PRIORITY_SET -> handleTaskPrioritySet(req);
                    case TupleSpaces.CMD_LIST_YEAR_SET -> handleListYearSet(req);
                    case TupleSpaces.CMD_TASK_YEAR_SET -> handleTaskYearSet(req);
                    case TupleSpaces.CMD_LIST_LOCATION_SET -> handleListLocationSet(req);
                    case TupleSpaces.CMD_LIST_DESCRIPTION_SET -> handleListDescriptionSet(req);
                    case TupleSpaces.CMD_TASK_LOCATION_SET -> handleTaskLocationSet(req);
                    case TupleSpaces.CMD_TASK_DESCRIPTION_SET -> handleTaskDescriptionSet(req);
                    case TupleSpaces.CMD_LIST_ORDER_BULK_SET -> handleListOrderBulkSet(req);
                    case TupleSpaces.CMD_TASK_ORDER_BULK_SET -> handleTaskOrderBulkSet(req);
                    case TupleSpaces.CMD_EXPORT_SESSION -> handleExportSession(req);
                    case TupleSpaces.CMD_IMPORT_SESSION -> handleImportSession(req);
                    default -> System.out.println("Unknown command: " + req.cmd());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
        
    private void sendOkResponse(String requestId, String p1, String p2, String p3, String p4) throws InterruptedException {
        responses.put(TupleSpaces.RESP_OK, requestId, p1, p2, p3, p4);
    }
    
    private void sendErrorResponse(String requestId, String errorMsg, String p2, String p3, String p4) throws InterruptedException {
        responses.put(TupleSpaces.RESP_ERROR, requestId, errorMsg, p2, p3, p4);
    }
    
    private void broadcastDataChange(String operation, String data1, String data2, String data3) throws InterruptedException {
        notifications.put(TupleSpaces.NOTIFY_DATA_CHANGED, System.currentTimeMillis(), operation, data1, data2, data3);
        requestAutosave();
    }
    
    /**
     * Auto-save session data asynchronously (debounced) to avoid blocking request processing
     * and to avoid spawning a new thread on every mutation.
     */
    private void requestAutosave() {
        if (!autosaveScheduled.compareAndSet(false, true)) {
            return;
        }

        autosaveExecutor.schedule(() -> {
            try {
                persistenceService.saveSession(users, todoLists, tasks);
            } finally {
                // Allow scheduling again.
                autosaveScheduled.set(false);
            }
        }, AUTOSAVE_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Stop background workers (safe to call multiple times).
     */
    public void close() {
        try {
            autosaveExecutor.shutdown();
            autosaveExecutor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {
        } finally {
            try {
                autosaveExecutor.shutdownNow();
            } catch (Exception ignored) {
            }
        }
    }
    
    private void handlePing(Request req) throws InterruptedException {
        System.out.println("Received ping from client.");
        sendOkResponse(req.requestId(), "pong", "", "", "");
    }

    private void handleClientConnect(Request req) throws InterruptedException {
        System.out.println("A new client connected to the server");
        // Acknowledge so clients can verify the request loop is alive.
        sendOkResponse(req.requestId(), "connected", "", "", "");
    }

    private void handleClientDisconnect(Request req) throws InterruptedException {
        String username = req.getString(0);
        System.out.println("Client disconnected from server: " + username);
        sendOkResponse(req.requestId(), "disconnected", "", "", "");
    }
    
    private void handleUserLogin(Request req) throws InterruptedException {
        String username = req.getString(0);
        System.out.println("New user logged in: " + username);
        sendOkResponse(req.requestId(), "logged_in", username != null ? username : "", "", "");
    }
    
    private void handleUserLogout(Request req) throws InterruptedException {
        String username = req.getString(0);
        System.out.println("User logged out: " + username);
        sendOkResponse(req.requestId(), "logged_out", username != null ? username : "", "", "");
    }
    
    private void handleListsGet(Request req) throws InterruptedException {
        List<Object[]> all = todoLists.queryAll(
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(Integer.class),
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(Integer.class),
                new FormalField(Integer.class),
                new FormalField(Integer.class),
                new FormalField(String.class),
                new FormalField(String.class));

        for (Object[] l : all) {
            String listId = (String) l[0];
            String listName = (String) l[1];
            int completion = (Integer) l[2];
            String owner = (String) l[3];
            String taskColumnsJson = (String) l[4];
            int priority = (Integer) l[5];
            int year = (Integer) l[6];
            int orderIndex = (Integer) l[7];
            String location = (String) l[8];
            String description = (String) l[9];
            sendOkResponse(req.requestId(), listId, listName, String.valueOf(completion), owner + "\t" + taskColumnsJson + "\t" + priority + "\t" + year + "\t" + orderIndex + "\t" + location + "\t" + description);
        }
    }

    private void handleTasksGet(Request req) throws InterruptedException {
        String listId = req.getString(0);
        if (listId == null) {
            sendErrorResponse(req.requestId(), "Missing listId", "", "", "");
            return;
        }
        
        List<Object[]> tuples = tasks.queryAll(
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(String.class),
            new FormalField(String.class),
            new FormalField(Integer.class),
            new FormalField(Integer.class),
            new FormalField(Integer.class),
            new FormalField(String.class),
            new FormalField(String.class));

        for (Object[] t : tuples) {
            String tListId = (String) t[0];
            if (!tListId.equals(listId)) continue;

            String taskId = (String) t[1];
            String title = (String) t[2];
            String assignee = (String) t[3];
            String status = (String) t[4];
            String dueDate = (String) t[5];
            int priority = (Integer) t[6];
            int year = (Integer) t[7];
            int orderIndex = (Integer) t[8];
            String location = (String) t[9];
            String description = (String) t[10];

            sendOkResponse(req.requestId(), listId, taskId, title + "\t" + assignee, status + "\t" + dueDate + "\t" + priority + "\t" + year + "\t" + orderIndex + "\t" + location + "\t" + description);
        }
        sendOkResponse(req.requestId(), "END", "", "", "");
    }
    
    private void handleListCreate(Request req) throws InterruptedException {
        String providedName = req.getString(0);
        String owner = req.getString(1, "");
        String taskColumnsJson = req.getString(2, "");
        String priorityStr = req.getString(3, "");
        if (taskColumnsJson == null || taskColumnsJson.isBlank()) {
            taskColumnsJson = Defaults.TASK_COLUMNS_JSON;
        }

        int priority = Defaults.PRIORITY;
        if (priorityStr != null && !priorityStr.isBlank()) {
            try {
                priority = Integer.parseInt(priorityStr.trim());
            } catch (Exception ignored) {}
        }
        priority = clampPriority(priority);
        
        final String listId;
        final String listName;
        final int orderIndex;
        synchronized (todoLists) {
            int count = Database.getTodoListCount(todoLists);
            listId = "l" + (count + 1);
            listName = (providedName != null && !providedName.isBlank())
                    ? providedName
                    : "New List " + (count + 1);

            orderIndex = nextListOrderIndex();

            // Lists: (listId, name, completion, owner, taskColumnsJson, priority, year, orderIndex, location, description)
            todoLists.put(listId, listName, 0, owner, taskColumnsJson, priority, Defaults.YEAR, orderIndex, "", "");  // Start with 0% completion
            counter.getp(new FormalField(Integer.class));
            counter.put(count + 1);
        }

        System.out.println("List created: " + listName);
        sendOkResponse(req.requestId(), listId, listName, "", "");
        broadcastDataChange("list_create", listId, listName, "");
    }

    private void handleListRename(Request req) throws InterruptedException {
        String listId = req.getString(0);
        String newName = req.getString(1);

        if (listId == null || listId.isBlank() || newName == null || newName.isBlank()) {
            sendErrorResponse(req.requestId(), "Invalid parameters", listId != null ? listId : "", "", "");
            return;
        }

        Object[] existing = todoLists.getp(
                new ActualField(listId),
                new FormalField(String.class),
                new FormalField(Integer.class),
                new FormalField(String.class),
            new FormalField(String.class),
            new FormalField(Integer.class),
            new FormalField(Integer.class),
            new FormalField(Integer.class),
            new FormalField(String.class),
            new FormalField(String.class));

        if (existing == null) {
            sendErrorResponse(req.requestId(), "List not found", listId, "", "");
            return;
        }

        int completion = (Integer) existing[2];
        String owner = (String) existing[3];
        String taskColumnsJson = (String) existing[4];
        int priority = (Integer) existing[5];
        int year = (Integer) existing[6];
        int orderIndex = (Integer) existing[7];
        String location = (String) existing[8];
        String description = (String) existing[9];

        todoLists.put(listId, newName, completion, owner, taskColumnsJson, priority, year, orderIndex, location, description);
        sendOkResponse(req.requestId(), listId, newName, "OK", "");
        broadcastDataChange("list_rename", listId, newName, "");
    }

    private void handleTaskAdd(Request req) {
        String listId = req.getString(0);
        String title = req.getString(1);
        String dueDate = req.getString(2, "");
        String assignee = req.getString(3, "");

        if (listId == null || title == null) {
            return;
        }

        String taskId = UUID.randomUUID().toString();
        String status = "NOT_STARTED";
        try {
            int orderIndex = nextTaskOrderIndex(listId);
            tasks.put(listId, taskId, title, assignee, status, dueDate, Defaults.PRIORITY, Defaults.YEAR, orderIndex, "", "");
            System.out.println("Task added: " + title);
            updateListCompletion(listId);
            sendOkResponse(req.requestId(), listId, taskId, title, status);
            broadcastDataChange("task_add", listId, title, dueDate);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void handleTaskRename(Request req) throws InterruptedException {
        String listId = req.getString(0);
        String taskId = req.getString(1);
        String newTitle = req.getString(2);

        if (listId == null || listId.isBlank() || taskId == null || taskId.isBlank() || newTitle == null || newTitle.isBlank()) {
            sendErrorResponse(req.requestId(), "Invalid parameters", listId != null ? listId : "", taskId != null ? taskId : "", "");
            return;
        }

        Object[] existing = tasks.getp(
                new ActualField(listId),
                new ActualField(taskId),
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(String.class),
            new FormalField(String.class),
            new FormalField(Integer.class),
            new FormalField(Integer.class),
            new FormalField(Integer.class),
            new FormalField(String.class),
            new FormalField(String.class));

        if (existing == null) {
            sendErrorResponse(req.requestId(), "Task not found", listId, taskId, "");
            return;
        }

        String assignee = (String) existing[3];
        String status = (String) existing[4];
        String dueDate = (String) existing[5];
        int priority = (Integer) existing[6];
        int year = (Integer) existing[7];
        int orderIndex = (Integer) existing[8];
        String location = (String) existing[9];
        String description = (String) existing[10];

        tasks.put(listId, taskId, newTitle, assignee, status, dueDate, priority, year, orderIndex, location, description);
        sendOkResponse(req.requestId(), listId, taskId, newTitle, "OK");
        broadcastDataChange("task_rename", listId, taskId, newTitle);
    }
    
    private void handleTaskStatus(Request req) throws InterruptedException {
        String listId = req.getString(0);
        String taskId = req.getString(1);
        String newStatus = req.getString(2);

        if (listId == null || taskId == null || newStatus == null) {
            sendErrorResponse(req.requestId(), "Invalid parameters", "", "", "");
            return;
        }

        Object[] existing = tasks.getp(
                new ActualField(listId),
                new ActualField(taskId),
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(String.class),
            new FormalField(String.class),
            new FormalField(Integer.class),
            new FormalField(Integer.class),
            new FormalField(Integer.class),
            new FormalField(String.class),
            new FormalField(String.class));
        
        if (existing == null) {
            sendErrorResponse(req.requestId(), "Task not found", listId, taskId, "");
            return;
        }

        String title = (String) existing[2];
        String assignee = (String) existing[3];
        String dueDate = (String) existing[5];
        int priority = (Integer) existing[6];
        int year = (Integer) existing[7];
        int orderIndex = (Integer) existing[8];
        String location = (String) existing[9];
        String description = (String) existing[10];

        tasks.put(listId, taskId, title, assignee, newStatus, dueDate, priority, year, orderIndex, location, description);
        updateListCompletion(listId);
        sendOkResponse(req.requestId(), listId, taskId, title, newStatus);
        broadcastDataChange("task_status", listId, newStatus, "");

        System.out.println("Task status updated: " + newStatus);
    }

    private void handleTaskAssign(Request req) {
        String listId = req.getString(0);
        String taskId = req.getString(1);
        String newAssignee = req.getString(2);
    
        if (listId == null || taskId == null || newAssignee == null) {
            try { sendErrorResponse(req.requestId(), "Invalid parameters", "", "", ""); }
            catch (InterruptedException ignored) {}
            return;
        }
    
        try {
            Object[] u = users.queryp(new ActualField(newAssignee));
            if (u == null) {
                sendErrorResponse(req.requestId(), "Unknown assignee", listId, taskId, newAssignee);
                return;
            }
    
            Object[] existing = tasks.getp(
                    new ActualField(listId),
                    new ActualField(taskId),
                    new FormalField(String.class),
                    new FormalField(String.class),
                    new FormalField(String.class),
                    new FormalField(String.class),
                    new FormalField(Integer.class),
                    new FormalField(Integer.class),
                    new FormalField(Integer.class),
                    new FormalField(String.class),
                    new FormalField(String.class));
    
            if (existing == null) {
                sendErrorResponse(req.requestId(), "Task not found", listId, taskId, "");
                return;
            }
    
            String title = (String) existing[2];
            String status = (String) existing[4];
            String dueDate = (String) existing[5];
            int priority = (Integer) existing[6];
            int year = (Integer) existing[7];
            int orderIndex = (Integer) existing[8];
            String location = (String) existing[9];
            String description = (String) existing[10];
    
            tasks.put(listId, taskId, title, newAssignee, status, dueDate, priority, year, orderIndex, location, description);
            sendOkResponse(req.requestId(), listId, taskId, newAssignee, "OK");
            broadcastDataChange("task_assign", listId, taskId, newAssignee);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void handleTaskUnassign(Request req) {
        String listId = req.getString(0);
        String taskId = req.getString(1);

        if (listId == null || taskId == null) {
            try {
                sendErrorResponse(req.requestId(), "Invalid parameters", "", "", "");
            } catch (InterruptedException ignored) {
            }
            return;
        }

        try {
            Object[] existing = tasks.getp(
                    new ActualField(listId),
                    new ActualField(taskId),
                    new FormalField(String.class),
                    new FormalField(String.class),
                    new FormalField(String.class),
                    new FormalField(String.class),
                    new FormalField(Integer.class),
                    new FormalField(Integer.class),
                    new FormalField(Integer.class),
                    new FormalField(String.class),
                    new FormalField(String.class));

            if (existing == null) {
                sendErrorResponse(req.requestId(), "Task not found", listId, taskId, "");
                return;
            }

            String title = (String) existing[2];
            String status = (String) existing[4];
            String dueDate = (String) existing[5];
            int priority = (Integer) existing[6];
            int year = (Integer) existing[7];
            int orderIndex = (Integer) existing[8];
            String location = (String) existing[9];
            String description = (String) existing[10];

            tasks.put(listId, taskId, title, "", status, dueDate, priority, year, orderIndex, location, description);
            sendOkResponse(req.requestId(), listId, taskId, "", "OK");
            broadcastDataChange("task_unassign", listId, taskId, "");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void handleListOwnerSet(Request req) throws InterruptedException {
        String listId = req.getString(0);
        String newOwner = req.getString(1);

        if (listId == null || listId.isBlank() || newOwner == null || newOwner.isBlank()) {
            sendErrorResponse(req.requestId(), "Invalid parameters", listId != null ? listId : "", "", "");
            return;
        }

        Object[] user = users.queryp(new ActualField(newOwner));
        if (user == null) {
            sendErrorResponse(req.requestId(), "Unknown user", listId, newOwner, "");
            return;
        }

        Object[] existing = todoLists.getp(
                new ActualField(listId),
                new FormalField(String.class),
                new FormalField(Integer.class),
                new FormalField(String.class),
            new FormalField(String.class),
            new FormalField(Integer.class),
            new FormalField(Integer.class),
            new FormalField(Integer.class),
            new FormalField(String.class),
            new FormalField(String.class));

        if (existing == null) {
            sendErrorResponse(req.requestId(), "List not found", listId, "", "");
            return;
        }

        String listName = (String) existing[1];
        int completion = (Integer) existing[2];
        String taskColumnsJson = (String) existing[4];
        int priority = (Integer) existing[5];
        int year = (Integer) existing[6];
        int orderIndex = (Integer) existing[7];
        String location = (String) existing[8];
        String description = (String) existing[9];

        todoLists.put(listId, listName, completion, newOwner, taskColumnsJson, priority, year, orderIndex, location, description);
        sendOkResponse(req.requestId(), listId, newOwner, "OK", "");
        broadcastDataChange("list_owner_set", listId, newOwner, "");
    }

    private void handleListOwnerClear(Request req) throws InterruptedException {
        String listId = req.getString(0);

        if (listId == null || listId.isBlank()) {
            sendErrorResponse(req.requestId(), "Invalid parameters", "", "", "");
            return;
        }

        Object[] existing = todoLists.getp(
                new ActualField(listId),
                new FormalField(String.class),
                new FormalField(Integer.class),
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(Integer.class),
                new FormalField(Integer.class),
            new FormalField(Integer.class),
            new FormalField(String.class),
            new FormalField(String.class));

        if (existing == null) {
            sendErrorResponse(req.requestId(), "List not found", listId, "", "");
            return;
        }

        String listName = (String) existing[1];
        int completion = (Integer) existing[2];
        String taskColumnsJson = (String) existing[4];
        int priority = (Integer) existing[5];
        int year = (Integer) existing[6];
        int orderIndex = (Integer) existing[7];
        String location = (String) existing[8];
        String description = (String) existing[9];

        todoLists.put(listId, listName, completion, "", taskColumnsJson, priority, year, orderIndex, location, description);
        sendOkResponse(req.requestId(), listId, "OK", "", "");
        broadcastDataChange("list_owner_clear", listId, "", "");
    }

    private void handleListColumnsSet(Request req) throws InterruptedException {
        String listId = req.getString(0);
        String taskColumnsJson = req.getString(1, "");

        if (listId == null || listId.isBlank()) {
            sendErrorResponse(req.requestId(), "Invalid parameters", "", "", "");
            return;
        }
        if (taskColumnsJson == null || taskColumnsJson.isBlank()) {
            taskColumnsJson = Defaults.TASK_COLUMNS_JSON;
        }

        Object[] existing = todoLists.getp(
                new ActualField(listId),
                new FormalField(String.class),
                new FormalField(Integer.class),
                new FormalField(String.class),
            new FormalField(String.class),
            new FormalField(Integer.class),
            new FormalField(Integer.class),
            new FormalField(Integer.class),
            new FormalField(String.class),
            new FormalField(String.class));

        if (existing == null) {
            sendErrorResponse(req.requestId(), "List not found", listId, "", "");
            return;
        }

        String listName = (String) existing[1];
        int completion = (Integer) existing[2];
        String owner = (String) existing[3];
        int priority = (Integer) existing[5];
        int year = (Integer) existing[6];
        int orderIndex = (Integer) existing[7];
        String location = (String) existing[8];
        String description = (String) existing[9];

        todoLists.put(listId, listName, completion, owner, taskColumnsJson, priority, year, orderIndex, location, description);
        sendOkResponse(req.requestId(), listId, "OK", "", "");
        broadcastDataChange("list_columns_set", listId, taskColumnsJson, "");
    }

    private void handleListPrioritySet(Request req) throws InterruptedException {
        String listId = req.getString(0);
        String priorityStr = req.getString(1);

        if (listId == null || listId.isBlank() || priorityStr == null || priorityStr.isBlank()) {
            sendErrorResponse(req.requestId(), "Invalid parameters", listId != null ? listId : "", "", "");
            return;
        }

        int priority;
        try {
            priority = Integer.parseInt(priorityStr.trim());
        } catch (Exception e) {
            sendErrorResponse(req.requestId(), "Invalid priority", listId, priorityStr, "");
            return;
        }
        priority = clampPriority(priority);

        Object[] existing = todoLists.getp(
                new ActualField(listId),
                new FormalField(String.class),
                new FormalField(Integer.class),
                new FormalField(String.class),
                new FormalField(String.class),
            new FormalField(Integer.class),
            new FormalField(Integer.class),
            new FormalField(Integer.class),
            new FormalField(String.class),
            new FormalField(String.class));

        if (existing == null) {
            sendErrorResponse(req.requestId(), "List not found", listId, "", "");
            return;
        }

        String listName = (String) existing[1];
        int completion = (Integer) existing[2];
        String owner = (String) existing[3];
        String taskColumnsJson = (String) existing[4];
        int year = (Integer) existing[6];
        int orderIndex = (Integer) existing[7];
        String location = (String) existing[8];
        String description = (String) existing[9];

        todoLists.put(listId, listName, completion, owner, taskColumnsJson, priority, year, orderIndex, location, description);
        sendOkResponse(req.requestId(), listId, String.valueOf(priority), "OK", "");
        broadcastDataChange("list_priority_set", listId, String.valueOf(priority), "");
    }

    private void handleListYearSet(Request req) throws InterruptedException {
        String listId = req.getString(0);
        String yearStr = req.getString(1);

        if (listId == null || listId.isBlank() || yearStr == null || yearStr.isBlank()) {
            sendErrorResponse(req.requestId(), "Invalid parameters", listId != null ? listId : "", "", "");
            return;
        }

        int year;
        try {
            year = Integer.parseInt(yearStr.trim());
        } catch (Exception e) {
            sendErrorResponse(req.requestId(), "Invalid year", listId, yearStr, "");
            return;
        }
        year = clampYear(year);

        Object[] existing = todoLists.getp(
                new ActualField(listId),
                new FormalField(String.class),
                new FormalField(Integer.class),
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(Integer.class),
                new FormalField(Integer.class),
            new FormalField(Integer.class),
            new FormalField(String.class),
            new FormalField(String.class));

        if (existing == null) {
            sendErrorResponse(req.requestId(), "List not found", listId, "", "");
            return;
        }

        String listName = (String) existing[1];
        int completion = (Integer) existing[2];
        String owner = (String) existing[3];
        String taskColumnsJson = (String) existing[4];
        int priority = (Integer) existing[5];
        int orderIndex = (Integer) existing[7];
        String location = (String) existing[8];
        String description = (String) existing[9];

        todoLists.put(listId, listName, completion, owner, taskColumnsJson, priority, year, orderIndex, location, description);
        sendOkResponse(req.requestId(), listId, String.valueOf(year), "OK", "");
        broadcastDataChange("list_year_set", listId, String.valueOf(year), "");
    }

    private void handleTaskPrioritySet(Request req) throws InterruptedException {
        String listId = req.getString(0);
        String taskId = req.getString(1);
        String priorityStr = req.getString(2);

        if (listId == null || listId.isBlank() || taskId == null || taskId.isBlank() || priorityStr == null || priorityStr.isBlank()) {
            sendErrorResponse(req.requestId(), "Invalid parameters", listId != null ? listId : "", taskId != null ? taskId : "", "");
            return;
        }

        int priority;
        try {
            priority = Integer.parseInt(priorityStr.trim());
        } catch (Exception e) {
            sendErrorResponse(req.requestId(), "Invalid priority", listId, taskId, priorityStr);
            return;
        }
        priority = clampPriority(priority);

        Object[] existing = tasks.getp(
                new ActualField(listId),
                new ActualField(taskId),
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(String.class),
            new FormalField(Integer.class),
            new FormalField(Integer.class),
            new FormalField(Integer.class),
            new FormalField(String.class),
            new FormalField(String.class));

        if (existing == null) {
            sendErrorResponse(req.requestId(), "Task not found", listId, taskId, "");
            return;
        }

        String title = (String) existing[2];
        String assignee = (String) existing[3];
        String status = (String) existing[4];
        String dueDate = (String) existing[5];
        int year = (Integer) existing[7];
        int orderIndex = (Integer) existing[8];
        String location = (String) existing[9];
        String description = (String) existing[10];

        tasks.put(listId, taskId, title, assignee, status, dueDate, priority, year, orderIndex, location, description);
        sendOkResponse(req.requestId(), listId, taskId, String.valueOf(priority), "OK");
        broadcastDataChange("task_priority_set", listId, taskId, String.valueOf(priority));
    }

    private void handleTaskYearSet(Request req) throws InterruptedException {
        String listId = req.getString(0);
        String taskId = req.getString(1);
        String yearStr = req.getString(2);

        if (listId == null || listId.isBlank() || taskId == null || taskId.isBlank() || yearStr == null || yearStr.isBlank()) {
            sendErrorResponse(req.requestId(), "Invalid parameters", listId != null ? listId : "", taskId != null ? taskId : "", "");
            return;
        }

        int year;
        try {
            year = Integer.parseInt(yearStr.trim());
        } catch (Exception e) {
            sendErrorResponse(req.requestId(), "Invalid year", listId, taskId, yearStr);
            return;
        }
        year = clampYear(year);

        Object[] existing = tasks.getp(
                new ActualField(listId),
                new ActualField(taskId),
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(Integer.class),
                new FormalField(Integer.class),
            new FormalField(Integer.class),
            new FormalField(String.class),
            new FormalField(String.class));

        if (existing == null) {
            sendErrorResponse(req.requestId(), "Task not found", listId, taskId, "");
            return;
        }

        String title = (String) existing[2];
        String assignee = (String) existing[3];
        String status = (String) existing[4];
        String dueDate = (String) existing[5];
        int priority = (Integer) existing[6];
        int orderIndex = (Integer) existing[8];
        String location = (String) existing[9];
        String description = (String) existing[10];

        tasks.put(listId, taskId, title, assignee, status, dueDate, priority, year, orderIndex, location, description);
        sendOkResponse(req.requestId(), listId, taskId, String.valueOf(year), "OK");
        broadcastDataChange("task_year_set", listId, taskId, String.valueOf(year));
    }

    private void handleListLocationSet(Request req) throws InterruptedException {
        String listId = req.getString(0);
        String location = req.getString(1, "");
        if (listId == null || listId.isBlank()) {
            sendErrorResponse(req.requestId(), "Invalid parameters", listId != null ? listId : "", "", "");
            return;
        }

        Object[] existing = todoLists.getp(
                new ActualField(listId),
                new FormalField(String.class),
                new FormalField(Integer.class),
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(Integer.class),
                new FormalField(Integer.class),
                new FormalField(Integer.class),
                new FormalField(String.class),
                new FormalField(String.class));

        if (existing == null) {
            sendErrorResponse(req.requestId(), "List not found", listId, "", "");
            return;
        }

        String listName = (String) existing[1];
        int completion = (Integer) existing[2];
        String owner = (String) existing[3];
        String taskColumnsJson = (String) existing[4];
        int priority = (Integer) existing[5];
        int year = (Integer) existing[6];
        int orderIndex = (Integer) existing[7];
        String description = (String) existing[9];

        todoLists.put(listId, listName, completion, owner, taskColumnsJson, priority, year, orderIndex, location, description);
        sendOkResponse(req.requestId(), listId, location, "OK", "");
        broadcastDataChange("list_location_set", listId, location, "");
    }

    private void handleListDescriptionSet(Request req) throws InterruptedException {
        String listId = req.getString(0);
        String description = req.getString(1, "");
        if (listId == null || listId.isBlank()) {
            sendErrorResponse(req.requestId(), "Invalid parameters", listId != null ? listId : "", "", "");
            return;
        }

        Object[] existing = todoLists.getp(
                new ActualField(listId),
                new FormalField(String.class),
                new FormalField(Integer.class),
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(Integer.class),
                new FormalField(Integer.class),
                new FormalField(Integer.class),
                new FormalField(String.class),
                new FormalField(String.class));

        if (existing == null) {
            sendErrorResponse(req.requestId(), "List not found", listId, "", "");
            return;
        }

        String listName = (String) existing[1];
        int completion = (Integer) existing[2];
        String owner = (String) existing[3];
        String taskColumnsJson = (String) existing[4];
        int priority = (Integer) existing[5];
        int year = (Integer) existing[6];
        int orderIndex = (Integer) existing[7];
        String location = (String) existing[8];

        todoLists.put(listId, listName, completion, owner, taskColumnsJson, priority, year, orderIndex, location, description);
        sendOkResponse(req.requestId(), listId, "OK", "", "");
        broadcastDataChange("list_description_set", listId, "", "");
    }

    private void handleTaskLocationSet(Request req) throws InterruptedException {
        String listId = req.getString(0);
        String taskId = req.getString(1);
        String location = req.getString(2, "");

        if (listId == null || listId.isBlank() || taskId == null || taskId.isBlank()) {
            sendErrorResponse(req.requestId(), "Invalid parameters", listId != null ? listId : "", taskId != null ? taskId : "", "");
            return;
        }

        Object[] existing = tasks.getp(
                new ActualField(listId),
                new ActualField(taskId),
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(Integer.class),
                new FormalField(Integer.class),
                new FormalField(Integer.class),
                new FormalField(String.class),
                new FormalField(String.class));

        if (existing == null) {
            sendErrorResponse(req.requestId(), "Task not found", listId, taskId, "");
            return;
        }

        String title = (String) existing[2];
        String assignee = (String) existing[3];
        String status = (String) existing[4];
        String dueDate = (String) existing[5];
        int priority = (Integer) existing[6];
        int year = (Integer) existing[7];
        int orderIndex = (Integer) existing[8];
        String description = (String) existing[10];

        tasks.put(listId, taskId, title, assignee, status, dueDate, priority, year, orderIndex, location, description);
        sendOkResponse(req.requestId(), listId, taskId, location, "OK");
        broadcastDataChange("task_location_set", listId, taskId, location);
    }

    private void handleTaskDescriptionSet(Request req) throws InterruptedException {
        String listId = req.getString(0);
        String taskId = req.getString(1);
        String description = req.getString(2, "");

        if (listId == null || listId.isBlank() || taskId == null || taskId.isBlank()) {
            sendErrorResponse(req.requestId(), "Invalid parameters", listId != null ? listId : "", taskId != null ? taskId : "", "");
            return;
        }

        Object[] existing = tasks.getp(
                new ActualField(listId),
                new ActualField(taskId),
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(Integer.class),
                new FormalField(Integer.class),
                new FormalField(Integer.class),
                new FormalField(String.class),
                new FormalField(String.class));

        if (existing == null) {
            sendErrorResponse(req.requestId(), "Task not found", listId, taskId, "");
            return;
        }

        String title = (String) existing[2];
        String assignee = (String) existing[3];
        String status = (String) existing[4];
        String dueDate = (String) existing[5];
        int priority = (Integer) existing[6];
        int year = (Integer) existing[7];
        int orderIndex = (Integer) existing[8];
        String location = (String) existing[9];

        tasks.put(listId, taskId, title, assignee, status, dueDate, priority, year, orderIndex, location, description);
        sendOkResponse(req.requestId(), listId, taskId, "OK", "");
        broadcastDataChange("task_description_set", listId, taskId, "");
    }

    private void handleTaskDueDate(Request req) throws InterruptedException {
        String listId = req.getString(0);
        String taskId = req.getString(1);
        String newDueDate = req.getString(2, ""); // tillåt tomt för att rensa datum
    
        if (listId == null || taskId == null) {
            sendErrorResponse(req.requestId(), "Invalid parameters", "", "", "");
            return;
        }
    
        Object[] existing = tasks.getp(
                new ActualField(listId),
                new ActualField(taskId),
                new FormalField(String.class), // title
                new FormalField(String.class), // assignee
                new FormalField(String.class), // status
            new FormalField(String.class),  // dueDate
            new FormalField(Integer.class),  // priority
            new FormalField(Integer.class),  // year
            new FormalField(Integer.class),  // orderIndex
            new FormalField(String.class),  // location
            new FormalField(String.class)  // description
        );
    
        if (existing == null) {
            sendErrorResponse(req.requestId(), "Task not found", listId, taskId, "");
            return;
        }
    
        String title = (String) existing[2];
        String assignee = (String) existing[3];
        String status = (String) existing[4];
        int priority = (Integer) existing[6];
        int year = (Integer) existing[7];
        int orderIndex = (Integer) existing[8];
        String location = (String) existing[9];
        String description = (String) existing[10];
    
        // uppdatera bara dueDate
        tasks.put(listId, taskId, title, assignee, status, newDueDate, priority, year, orderIndex, location, description);
    
        sendOkResponse(req.requestId(), listId, taskId, newDueDate, "OK");
        broadcastDataChange("task_duedate", listId, taskId, newDueDate);
    
        System.out.println("Task due date updated: " + newDueDate);
    }
    

    private void handleTaskDelete(Request req) throws InterruptedException {
        String taskId = req.getString(1);

        if (taskId == null) {
            sendErrorResponse(req.requestId(), "Missing taskId", "", "", "");
            return;
        }

        Object[] removed = tasks.getp(
                new FormalField(String.class),
                new ActualField(taskId),
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(String.class),
            new FormalField(String.class),
            new FormalField(Integer.class),
            new FormalField(Integer.class),
            new FormalField(Integer.class),
            new FormalField(String.class),
            new FormalField(String.class));

        if (removed == null) {
            sendErrorResponse(req.requestId(), "Task not found", "", taskId, "");
            return;
        }
        
        String deletedListId = (String) removed[0];
        updateListCompletion(deletedListId);
        sendOkResponse(req.requestId(), "", taskId, "DELETED", "OK");
        broadcastDataChange("task_delete", deletedListId, taskId, "");
    }

    private void handleListDelete(Request req) throws InterruptedException {
        String listId = req.getString(0);

        if (listId == null) {
            sendErrorResponse(req.requestId(), "Missing listId", "", "", "");
            return;
        }
        
        Object[] removed = todoLists.getp(
                new ActualField(listId),
                new FormalField(String.class),
            new FormalField(Integer.class),
            new FormalField(String.class),
            new FormalField(String.class),
            new FormalField(Integer.class),
            new FormalField(Integer.class),
            new FormalField(Integer.class),
            new FormalField(String.class),
            new FormalField(String.class));
                
        if (removed == null) {
            sendErrorResponse(req.requestId(), "List not found", listId, "", "");
            return;
        }
        
        String removedName = (String) removed[1];
        System.out.println("List deleted: " + removedName);
        sendOkResponse(req.requestId(), listId, removedName, "DELETED", "OK");
        broadcastDataChange("list_delete", listId, removedName, "");
    }

    private void handleUserDelete(Request req) throws InterruptedException {
        String username = req.getString(0);
        if (username == null || username.isBlank()) {
            sendErrorResponse(req.requestId(), "Missing username", "", "", "");
            return;
        }

        Object[] user = users.queryp(new ActualField(username));
        if (user == null) {
            sendErrorResponse(req.requestId(), "User not found", username, "", "");
            return;
        }

        // Prevent deletion if user owns any lists
        List<Object[]> listTuples = todoLists.queryAll(
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(Integer.class),
                new FormalField(String.class),
            new FormalField(String.class),
            new FormalField(Integer.class),
            new FormalField(Integer.class),
            new FormalField(Integer.class),
            new FormalField(String.class),
            new FormalField(String.class));

        for (Object[] l : listTuples) {
            String owner = (String) l[3];
            if (username.equals(owner)) {
                sendErrorResponse(req.requestId(), "User is owner of one or more lists", username, "", "");
                return;
            }
        }

        // Remove user tuple
        users.getp(new ActualField(username));

        // Clear assignee on any tasks assigned to this user
        List<Object[]> taskTuples = tasks.queryAll(
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(String.class),
            new FormalField(String.class),
            new FormalField(Integer.class),
            new FormalField(Integer.class),
            new FormalField(Integer.class),
            new FormalField(String.class),
            new FormalField(String.class));

        for (Object[] t : taskTuples) {
            String listId = (String) t[0];
            String taskId = (String) t[1];
            String title = (String) t[2];
            String assignee = (String) t[3];
            String status = (String) t[4];
            String dueDate = (String) t[5];
            int priority = (Integer) t[6];
            int year = (Integer) t[7];
            int orderIndex = (Integer) t[8];
            String location = (String) t[9];
            String description = (String) t[10];

            if (username.equals(assignee)) {
                // Remove the old tuple and reinsert updated one
                Object[] removed = tasks.getp(
                        new ActualField(listId),
                        new ActualField(taskId),
                        new FormalField(String.class),
                        new FormalField(String.class),
                        new FormalField(String.class),
                        new FormalField(String.class),
                        new FormalField(Integer.class),
                        new FormalField(Integer.class),
                        new FormalField(Integer.class),
                        new FormalField(String.class),
                        new FormalField(String.class));
                if (removed != null) {
                    tasks.put(listId, taskId, title, "", status, dueDate, priority, year, orderIndex, location, description);
                }
            }
        }

        sendOkResponse(req.requestId(), username, "DELETED", "OK", "");
        broadcastDataChange("user_delete", username, "", "");
    }

    // Helper method to calculate and update list completion percentage
    private void updateListCompletion(String listId) {
        try {
            // Query all tasks for this list
            List<Object[]> tuples = tasks.queryAll(
                    new ActualField(listId),
                    new FormalField(String.class),
                    new FormalField(String.class),
                    new FormalField(String.class),
                    new FormalField(String.class),
                    new FormalField(String.class),
                    new FormalField(Integer.class),
                    new FormalField(Integer.class),
                    new FormalField(Integer.class),
                    new FormalField(String.class),
                    new FormalField(String.class));
            
            int completion = 0;
            if (!tuples.isEmpty()) {
                // Calculate average completion based on task statuses
                int totalCompletion = 0;
                int taskCount = 0;
                
                for (Object[] t : tuples) {
                    String statusStr = (String) t[4];
                    try {
                        dk.dtu.shared.TaskStatus status = dk.dtu.shared.TaskStatus.valueOf(statusStr);
                        totalCompletion += status.getCompletionPercentage();
                        taskCount++;
                    } catch (IllegalArgumentException e) {
                        System.err.println("Unknown task status: " + statusStr);
                    }
                }
                
                completion = taskCount > 0 ? totalCompletion / taskCount : 0;
            }
            
            // Update the list tuple with new completion percentage
            Object[] existingList = todoLists.getp(
                    new ActualField(listId),
                    new FormalField(String.class),
                    new FormalField(Integer.class),
                    new FormalField(String.class),
                    new FormalField(String.class),
                    new FormalField(Integer.class),
                    new FormalField(Integer.class),
                    new FormalField(Integer.class),
                    new FormalField(String.class),
                    new FormalField(String.class));
            
            if (existingList != null) {
                String listName = (String) existingList[1];
                String owner = (String) existingList[3];
                String taskColumnsJson = (String) existingList[4];
                int priority = (Integer) existingList[5];
                int year = (Integer) existingList[6];
                int orderIndex = (Integer) existingList[7];
                String location = (String) existingList[8];
                String description = (String) existingList[9];
                todoLists.put(listId, listName, completion, owner, taskColumnsJson, priority, year, orderIndex, location, description);
            }
        } catch (InterruptedException e) {
            System.err.println("Error updating list completion: " + e.getMessage());
        }
    }

    private void handleListOrderBulkSet(Request req) throws InterruptedException {
        String json = req.getString(0);
        if (json == null || json.isBlank()) {
            sendErrorResponse(req.requestId(), "Missing order payload", "", "", "");
            return;
        }

        List<String> ids;
        try {
            ids = GSON.fromJson(json, STRING_LIST_TYPE);
        } catch (Exception e) {
            sendErrorResponse(req.requestId(), "Invalid order payload", json, "", "");
            return;
        }
        if (ids == null || ids.isEmpty()) {
            sendErrorResponse(req.requestId(), "Empty order payload", "", "", "");
            return;
        }

        for (int i = 0; i < ids.size(); i++) {
            String listId = ids.get(i);
            if (listId == null || listId.isBlank()) continue;

            Object[] existing = todoLists.getp(
                    new ActualField(listId),
                    new FormalField(String.class),
                    new FormalField(Integer.class),
                    new FormalField(String.class),
                    new FormalField(String.class),
                    new FormalField(Integer.class),
                    new FormalField(Integer.class),
                    new FormalField(Integer.class),
                    new FormalField(String.class),
                    new FormalField(String.class));
            if (existing == null) continue;

            String listName = (String) existing[1];
            int completion = (Integer) existing[2];
            String owner = (String) existing[3];
            String taskColumnsJson = (String) existing[4];
            int priority = (Integer) existing[5];
            int year = (Integer) existing[6];
            String location = (String) existing[8];
            String description = (String) existing[9];

            todoLists.put(listId, listName, completion, owner, taskColumnsJson, priority, year, i, location, description);
        }

        sendOkResponse(req.requestId(), "OK", "", "", "");
        broadcastDataChange("list_order_bulk_set", "", "", "");
    }

    private void handleTaskOrderBulkSet(Request req) throws InterruptedException {
        String listId = req.getString(0);
        String json = req.getString(1);
        if (listId == null || listId.isBlank() || json == null || json.isBlank()) {
            sendErrorResponse(req.requestId(), "Missing parameters", listId != null ? listId : "", "", "");
            return;
        }

        List<String> ids;
        try {
            ids = GSON.fromJson(json, STRING_LIST_TYPE);
        } catch (Exception e) {
            sendErrorResponse(req.requestId(), "Invalid order payload", listId, json, "");
            return;
        }
        if (ids == null || ids.isEmpty()) {
            sendErrorResponse(req.requestId(), "Empty order payload", listId, "", "");
            return;
        }

        for (int i = 0; i < ids.size(); i++) {
            String taskId = ids.get(i);
            if (taskId == null || taskId.isBlank()) continue;

            Object[] existing = tasks.getp(
                    new ActualField(listId),
                    new ActualField(taskId),
                    new FormalField(String.class),
                    new FormalField(String.class),
                    new FormalField(String.class),
                    new FormalField(String.class),
                    new FormalField(Integer.class),
                    new FormalField(Integer.class),
                    new FormalField(Integer.class),
                    new FormalField(String.class),
                    new FormalField(String.class));
            if (existing == null) continue;

            String title = (String) existing[2];
            String assignee = (String) existing[3];
            String status = (String) existing[4];
            String dueDate = (String) existing[5];
            int priority = (Integer) existing[6];
            int year = (Integer) existing[7];
            String location = (String) existing[9];
            String description = (String) existing[10];

            tasks.put(listId, taskId, title, assignee, status, dueDate, priority, year, i, location, description);
        }

        sendOkResponse(req.requestId(), listId, "OK", "", "");
        broadcastDataChange("task_order_bulk_set", listId, "", "");
    }

    private int nextListOrderIndex() {
        try {
            List<Object[]> all = todoLists.queryAll(
                    new FormalField(String.class),
                    new FormalField(String.class),
                    new FormalField(Integer.class),
                    new FormalField(String.class),
                    new FormalField(String.class),
                    new FormalField(Integer.class),
                    new FormalField(Integer.class),
                    new FormalField(Integer.class),
                    new FormalField(String.class),
                    new FormalField(String.class));

            int max = -1;
            for (Object[] l : all) {
                int idx = (l[7] instanceof Integer i) ? i : DEFAULT_ORDER_INDEX;
                if (idx > max) max = idx;
            }
            return max + 1;
        } catch (InterruptedException e) {
            return 0;
        }
    }

    private int nextTaskOrderIndex(String listId) {
        try {
            List<Object[]> all = tasks.queryAll(
                    new ActualField(listId),
                    new FormalField(String.class),
                    new FormalField(String.class),
                    new FormalField(String.class),
                    new FormalField(String.class),
                    new FormalField(String.class),
                    new FormalField(Integer.class),
                    new FormalField(Integer.class),
                    new FormalField(Integer.class),
                    new FormalField(String.class),
                    new FormalField(String.class));
            int max = -1;
            for (Object[] t : all) {
                int idx = (t[8] instanceof Integer i) ? i : DEFAULT_ORDER_INDEX;
                if (idx > max) max = idx;
            }
            return max + 1;
        } catch (InterruptedException e) {
            return 0;
        }
    }

    private static int clampPriority(int value) {
        if (value < 1) return 1;
        if (value > 10) return 10;
        return value;
    }

    private static int clampYear(int value) {
        if (value < 0) return 0;
        if (value > 9999) return 9999;
        return value;
    }
    
    private void handleExportSession(Request req) throws InterruptedException {
        String filePath = req.getString(0);
        
        if (filePath == null || filePath.isBlank()) {
            sendErrorResponse(req.requestId(), "Invalid file path for export", "", "", "");
            return;
        }
        
        try {
            boolean success = persistenceService.exportSession(users, todoLists, tasks, filePath);
            if (success) {
                sendOkResponse(req.requestId(), "Session exported successfully", filePath, "", "");
            } else {
                sendErrorResponse(req.requestId(), "Failed to export session", filePath, "", "");
            }
        } catch (Exception e) {
            sendErrorResponse(req.requestId(), "Export error: " + e.getMessage(), filePath, "", "");
        }
    }
    
    private void handleImportSession(Request req) throws InterruptedException {
        String filePath = req.getString(0);
        String mode = req.getString(1); // "replace" or "merge"
        
        if (filePath == null || filePath.isBlank()) {
            sendErrorResponse(req.requestId(), "Invalid file path for import", "", "", "");
            return;
        }
        
        // Default to replace if mode not specified
        if (mode == null || mode.isBlank()) {
            mode = "replace";
        }
        
        try {
            boolean success;
            if ("merge".equalsIgnoreCase(mode)) {
                success = persistenceService.mergeSession(users, todoLists, tasks, filePath);
            } else {
                success = persistenceService.importSession(users, todoLists, tasks, filePath);
            }
            
            if (success) {
                // Broadcast that all data changed (force all clients to refresh)
                broadcastDataChange("IMPORT", "all", "", "");
                String action = "merge".equalsIgnoreCase(mode) ? "merged" : "imported";
                sendOkResponse(req.requestId(), "Session " + action + " successfully", filePath, "", "");
            } else {
                sendErrorResponse(req.requestId(), "Failed to " + mode + " session", filePath, "", "");
            }
        } catch (Exception e) {
            sendErrorResponse(req.requestId(), "Import error: " + e.getMessage(), filePath, "", "");
        }
    }
  
    private record Request(String cmd, String requestId, Object a1, Object a2, Object a3, Object a4) {
        
        static Request fromTuple(Object[] tuple) {
            if (tuple == null || tuple.length < TupleSpaces.ARITY) {
                throw new IllegalArgumentException("Request tuple must have arity " + TupleSpaces.ARITY);
            }
            return new Request(
                    (String) tuple[0],
                    (String) tuple[1],
                    tuple[2],
                    tuple[3],
                    tuple[4],
                    tuple[5]);
        }
        
        // String getters for arguments
        String getString(int index) {
            return getString(index, null);
        }
        
        String getString(int index, String defaultValue) {
            Object obj = switch (index) {
                case 0 -> a1;
                case 1 -> a2;
                case 2 -> a3;
                case 3 -> a4;
                default -> null;
            };
            return (obj instanceof String s) ? s : defaultValue;
        }
    }
}