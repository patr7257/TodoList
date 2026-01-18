package dk.dtu;

import dk.dtu.shared.TupleSpaces;
import java.util.List;
import java.util.UUID;

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

    public ServerHandlerService(Space todoLists, Space counter, Space users, Space tasks, Space requests,
            Space responses, Space notifications) {
        this.todoLists = todoLists;
        this.counter = counter;
        this.users = users;
        this.tasks = tasks;
        this.requests = requests;
        this.responses = responses;
        this.notifications = notifications;
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
                    case TupleSpaces.CMD_LISTS_GET -> handleListsGet(req);
                    case TupleSpaces.CMD_TASKS_GET -> handleTasksGet(req);
                    case TupleSpaces.CMD_TASK_DUEDATE -> handleTaskDueDate(req);
                    case TupleSpaces.CMD_TASK_DELETE -> handleTaskDelete(req);
                    case TupleSpaces.CMD_LIST_DELETE -> handleListDelete(req);
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
    }
    
    private void handlePing(Request req) throws InterruptedException {
        System.out.println("Received ping from client.");
        sendOkResponse(req.requestId(), "pong", "", "", "");
    }

    private void handleClientConnect(Request req) {
        System.out.println("A new client connected to the server");
    }

    private void handleClientDisconnect(Request req) {
        String username = req.getString(0);
        System.out.println("Client disconnected from server: " + username);
    }
    
    private void handleUserLogin(Request req) {
        String username = req.getString(0);
        System.out.println("New user logged in: " + username);
    }
    
    private void handleUserLogout(Request req) {
        String username = req.getString(0);
        System.out.println("User logged out: " + username);
    }
    
    private void handleListsGet(Request req) throws InterruptedException {
        List<Object[]> all = todoLists.queryAll(
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(Integer.class));

        for (Object[] l : all) {
            String listId = (String) l[0];
            String listName = (String) l[1];
            int completion = (Integer) l[2];
            sendOkResponse(req.requestId(), listId, listName, String.valueOf(completion), "");
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
                new FormalField(String.class));

        for (Object[] t : tuples) {
            String tListId = (String) t[0];
            if (!tListId.equals(listId)) continue;

            String taskId = (String) t[1];
            String title = (String) t[2];
            String assignee = (String) t[3];
            String status = (String) t[4];
            String dueDate = (String) t[5];

            sendOkResponse(req.requestId(), listId, taskId, title + "\t" + assignee, status + "\t" + dueDate);
        }
        sendOkResponse(req.requestId(), "END", "", "", "");
    }
    
    private void handleListCreate(Request req) throws InterruptedException {
        String providedName = req.getString(0);
        
        final String listId;
        final String listName;
        synchronized (todoLists) {
            int count = Database.getTodoListCount(todoLists);
            listId = "l" + (count + 1);
            listName = (providedName != null && !providedName.isBlank())
                    ? providedName
                    : "New List " + (count + 1);

            todoLists.put(listId, listName, 0);  // Start with 0% completion
            counter.getp(new FormalField(Integer.class));
            counter.put(count + 1);
        }

        System.out.println("List created: " + listName);
        sendOkResponse(req.requestId(), listId, listName, "", "");
        broadcastDataChange("list_create", listId, listName, "");
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
            tasks.put(listId, taskId, title, assignee, status, dueDate);
            System.out.println("Task added: " + title);
            updateListCompletion(listId);
            sendOkResponse(req.requestId(), listId, taskId, title, status);
            broadcastDataChange("task_add", listId, title, dueDate);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
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
                new FormalField(String.class));
        
        if (existing == null) {
            sendErrorResponse(req.requestId(), "Task not found", listId, taskId, "");
            return;
        }

        String title = (String) existing[2];
        String assignee = (String) existing[3];
        String dueDate = (String) existing[5];

        tasks.put(listId, taskId, title, assignee, newStatus, dueDate);
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
                    new FormalField(String.class));
    
            if (existing == null) {
                sendErrorResponse(req.requestId(), "Task not found", listId, taskId, "");
                return;
            }
    
            String title = (String) existing[2];
            String status = (String) existing[4];
            String dueDate = (String) existing[5];
    
            tasks.put(listId, taskId, title, newAssignee, status, dueDate);
            sendOkResponse(req.requestId(), listId, taskId, newAssignee, "OK");
            broadcastDataChange("task_assign", listId, taskId, newAssignee);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
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
                new FormalField(String.class)  // dueDate
        );
    
        if (existing == null) {
            sendErrorResponse(req.requestId(), "Task not found", listId, taskId, "");
            return;
        }
    
        String title = (String) existing[2];
        String assignee = (String) existing[3];
        String status = (String) existing[4];
    
        // uppdatera bara dueDate
        tasks.put(listId, taskId, title, assignee, status, newDueDate);
    
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
                new FormalField(Integer.class));
                
        if (removed == null) {
            sendErrorResponse(req.requestId(), "List not found", listId, "", "");
            return;
        }
        
        String removedName = (String) removed[1];
        System.out.println("List deleted: " + removedName);
        sendOkResponse(req.requestId(), listId, removedName, "DELETED", "OK");
        broadcastDataChange("list_delete", listId, removedName, "");
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
                    new FormalField(Integer.class));
            
            if (existingList != null) {
                String listName = (String) existingList[1];
                todoLists.put(listId, listName, completion);
            }
        } catch (InterruptedException e) {
            System.err.println("Error updating list completion: " + e.getMessage());
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