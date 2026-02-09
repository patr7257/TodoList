package dk.dtu;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dk.dtu.shared.Config;
import dk.dtu.shared.Defaults;
import dk.dtu.shared.models.SessionData;
import dk.dtu.shared.models.TaskData;
import dk.dtu.shared.models.TodoListData;
import dk.dtu.shared.models.UserData;
import org.jspace.FormalField;
import org.jspace.Space;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for persisting and loading session data to/from JSON files.
 * Uses Gson for JSON serialization.
 * Data is stored in a configurable directory (defaults to user home/.todolist-data/)
 */
public class PersistenceService {
    private static final String SESSION_FILE = "session.json";
    
    private final Gson gson;
    private final Path dataDirectory;
    private final Path sessionFile;

    /**
     * Create a PersistenceService with default data directory from Config
     */
    public PersistenceService() {
        this(Config.getDataDirectory());
    }

    /**
     * Create a PersistenceService with custom data directory
     * @param dataDirectoryPath Path to data directory
     */
    public PersistenceService(String dataDirectoryPath) {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.dataDirectory = Paths.get(dataDirectoryPath);
        this.sessionFile = dataDirectory.resolve(SESSION_FILE);
        
        // Ensure data directory exists
        try {
            Files.createDirectories(dataDirectory);
            System.out.println("Data directory: " + dataDirectory.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to create data directory: " + e.getMessage());
        }
    }

    /**
     * Save all session data to file
     * @param users Space containing user data
     * @param todoLists Space containing todo lists
     * @param tasks Space containing tasks
     * @return true if save was successful
     */
    public boolean saveSession(Space users, Space todoLists, Space tasks) {
        try {
            SessionData session = new SessionData();
            
            // Extract users
            List<Object[]> userTuples = users.queryAll(new FormalField(String.class));
            List<UserData> userData = new ArrayList<>();
            for (Object[] tuple : userTuples) {
                userData.add(new UserData((String) tuple[0]));
            }
            session.setUsers(userData);
            
            // Extract todo lists
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
            List<TodoListData> listData = new ArrayList<>();
            for (Object[] tuple : listTuples) {
                listData.add(new TodoListData(
                        (String) tuple[0],
                        (String) tuple[1],
                    (Integer) tuple[2],
                    (String) tuple[3],
                    (String) tuple[4],
                    (Integer) tuple[5],
                    (Integer) tuple[6],
                    (Integer) tuple[7],
                    (String) tuple[8],
                    (String) tuple[9]
                ));
            }
            session.setTodoLists(listData);
            
            // Extract tasks
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
            List<TaskData> taskData = new ArrayList<>();
            for (Object[] tuple : taskTuples) {
                taskData.add(new TaskData(
                        (String) tuple[0],
                        (String) tuple[1],
                        (String) tuple[2],
                        (String) tuple[3],
                        (String) tuple[4],
                    (String) tuple[5],
                    (Integer) tuple[6],
                    (Integer) tuple[7],
                    (Integer) tuple[8],
                    (String) tuple[9],
                    (String) tuple[10]
                ));
            }
            session.setTasks(taskData);
            
            session.setLastSaved(System.currentTimeMillis());
            
            // Write to file
            try (FileWriter writer = new FileWriter(sessionFile.toFile())) {
                gson.toJson(session, writer);
            }
            
            System.out.println("Session saved: " + userTuples.size() + " users, " 
                    + listTuples.size() + " lists, " + taskTuples.size() + " tasks");
            return true;
            
        } catch (InterruptedException | IOException e) {
            System.err.println("Failed to save session: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Load session data from file and populate spaces
     * @param users Space to populate with users
     * @param todoLists Space to populate with todo lists
     * @param tasks Space to populate with tasks
     * @return true if load was successful
     */
    public boolean loadSession(Space users, Space todoLists, Space tasks) {
        if (!Files.exists(sessionFile)) {
            System.out.println("No saved session found. Starting with fresh data.");
            return false;
        }

        try (FileReader reader = new FileReader(sessionFile.toFile())) {
            SessionData session = gson.fromJson(reader, SessionData.class);
            
            if (session == null) {
                System.out.println("Session file is empty or invalid.");
                return false;
            }
            
            // Load users
            for (UserData user : session.getUsers()) {
                users.put(user.getUsername());
            }
            
            // Load todo lists
            int listIdx = 0;
            for (TodoListData list : session.getTodoLists()) {
                String owner = list.getOwner() != null ? list.getOwner() : "";
                String taskColumnsJson = list.getTaskColumnsJson() != null ? list.getTaskColumnsJson() : "";
                int priority = list.getPriority() != null ? list.getPriority() : Defaults.PRIORITY;
                int year = list.getYear() != null ? list.getYear() : Defaults.YEAR;
                int orderIndex = list.getOrderIndex() != null ? list.getOrderIndex() : listIdx;
                String location = list.getLocation() != null ? list.getLocation() : "";
                String description = list.getDescription() != null ? list.getDescription() : "";
                todoLists.put(list.getListId(), list.getListName(), list.getCompletionPercentage(), owner, taskColumnsJson, priority, year, orderIndex, location, description);
                listIdx++;
            }
            
            // Load tasks
            int taskIdx = 0;
            for (TaskData task : session.getTasks()) {
                int priority = task.getPriority() != null ? task.getPriority() : Defaults.PRIORITY;
                int year = task.getYear() != null ? task.getYear() : Defaults.YEAR;
                int orderIndex = task.getOrderIndex() != null ? task.getOrderIndex() : taskIdx;
                String location = task.getLocation() != null ? task.getLocation() : "";
                String description = task.getDescription() != null ? task.getDescription() : "";
                tasks.put(task.getListId(), task.getTaskId(), task.getTitle(),
                    task.getAssignee(), task.getStatus(), task.getDueDate(), priority, year, orderIndex, location, description);
                taskIdx++;
            }
            
            System.out.println("Session loaded: " + session.getUsers().size() + " users, " 
                    + session.getTodoLists().size() + " lists, " + session.getTasks().size() + " tasks");
            System.out.println("Last saved: " + new java.util.Date(session.getLastSaved()));
            return true;
            
        } catch (InterruptedException | IOException e) {
            System.err.println("Failed to load session: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Check if a saved session exists
     * @return true if session file exists
     */
    public boolean hasExistingSession() {
        return Files.exists(sessionFile);
    }

    /**
     * Delete the session file
     * @return true if deletion was successful
     */
    public boolean deleteSession() {
        try {
            if (Files.exists(sessionFile)) {
                Files.delete(sessionFile);
                System.out.println("Session file deleted.");
                return true;
            }
            return false;
        } catch (IOException e) {
            System.err.println("Failed to delete session: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get the data directory path
     * @return Path to data directory
     */
    public Path getDataDirectory() {
        return dataDirectory;
    }
    
    /**
     * Export session data to a custom JSON file
     * @param users Space containing user data
     * @param todoLists Space containing todo lists
     * @param tasks Space containing tasks
     * @param exportFilePath Path to export file
     * @return true if export was successful
     */
    public boolean exportSession(Space users, Space todoLists, Space tasks, String exportFilePath) {
        try {
            SessionData session = new SessionData();
            
            // Extract users
            List<Object[]> userTuples = users.queryAll(new FormalField(String.class));
            List<UserData> userData = new ArrayList<>();
            for (Object[] tuple : userTuples) {
                userData.add(new UserData((String) tuple[0]));
            }
            session.setUsers(userData);
            
            // Extract todo lists
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
            List<TodoListData> listData = new ArrayList<>();
            for (Object[] tuple : listTuples) {
                listData.add(new TodoListData(
                        (String) tuple[0],
                        (String) tuple[1],
                    (Integer) tuple[2],
                    (String) tuple[3],
                    (String) tuple[4],
                    (Integer) tuple[5],
                    (Integer) tuple[6],
                    (Integer) tuple[7],
                    (String) tuple[8],
                    (String) tuple[9]
                ));
            }
            session.setTodoLists(listData);
            
            // Extract tasks
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
            List<TaskData> taskData = new ArrayList<>();
            for (Object[] tuple : taskTuples) {
                taskData.add(new TaskData(
                        (String) tuple[0],
                        (String) tuple[1],
                        (String) tuple[2],
                        (String) tuple[3],
                        (String) tuple[4],
                    (String) tuple[5],
                    (Integer) tuple[6],
                    (Integer) tuple[7],
                    (Integer) tuple[8],
                    (String) tuple[9],
                    (String) tuple[10]
                ));
            }
            session.setTasks(taskData);
            
            session.setLastSaved(System.currentTimeMillis());
            
            // Write to custom file path
            Path exportPath = Paths.get(exportFilePath);
            Files.createDirectories(exportPath.getParent());
            
            try (FileWriter writer = new FileWriter(exportPath.toFile())) {
                gson.toJson(session, writer);
            }
            
            System.out.println("Session exported to: " + exportFilePath);
            System.out.println("  " + userTuples.size() + " users, " 
                    + listTuples.size() + " lists, " + taskTuples.size() + " tasks");
            return true;
            
        } catch (InterruptedException | IOException e) {
            System.err.println("Failed to export session: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Import session data from a custom JSON file and replace existing data
     * @param users Space to populate with users
     * @param todoLists Space to populate with todo lists
     * @param tasks Space to populate with tasks
     * @param importFilePath Path to import file
     * @return true if import was successful
     */
    public boolean importSession(Space users, Space todoLists, Space tasks, String importFilePath) {
        Path importPath = Paths.get(importFilePath);
        
        if (!Files.exists(importPath)) {
            System.out.println("Import file not found: " + importFilePath);
            return false;
        }

        try (FileReader reader = new FileReader(importPath.toFile())) {
            SessionData session = gson.fromJson(reader, SessionData.class);
            
            if (session == null) {
                System.out.println("Import file is empty or invalid.");
                return false;
            }
            
            // Clear existing data
            clearSpace(users);
            clearSpace(todoLists);
            clearSpace(tasks);
            
            // Load users
            for (UserData user : session.getUsers()) {
                users.put(user.getUsername());
            }
            
            // Load todo lists
            int listIdx = 0;
            for (TodoListData list : session.getTodoLists()) {
                String owner = list.getOwner() != null ? list.getOwner() : "";
                String taskColumnsJson = list.getTaskColumnsJson() != null ? list.getTaskColumnsJson() : "";
                int priority = list.getPriority() != null ? list.getPriority() : Defaults.PRIORITY;
                int year = list.getYear() != null ? list.getYear() : Defaults.YEAR;
                int orderIndex = list.getOrderIndex() != null ? list.getOrderIndex() : listIdx;
                String location = list.getLocation() != null ? list.getLocation() : "";
                String description = list.getDescription() != null ? list.getDescription() : "";
                todoLists.put(list.getListId(), list.getListName(), list.getCompletionPercentage(), owner, taskColumnsJson, priority, year, orderIndex, location, description);
                listIdx++;
            }
            
            // Load tasks
            int taskIdx = 0;
            for (TaskData task : session.getTasks()) {
                int priority = task.getPriority() != null ? task.getPriority() : Defaults.PRIORITY;
                int year = task.getYear() != null ? task.getYear() : Defaults.YEAR;
                int orderIndex = task.getOrderIndex() != null ? task.getOrderIndex() : taskIdx;
                String location = task.getLocation() != null ? task.getLocation() : "";
                String description = task.getDescription() != null ? task.getDescription() : "";
                tasks.put(task.getListId(), task.getTaskId(), task.getTitle(),
                    task.getAssignee(), task.getStatus(), task.getDueDate(), priority, year, orderIndex, location, description);
                taskIdx++;
            }
            
            System.out.println("Session imported from: " + importFilePath);
            System.out.println("  " + session.getUsers().size() + " users, " 
                    + session.getTodoLists().size() + " lists, " + session.getTasks().size() + " tasks");
            
            // Auto-save after import
            saveSession(users, todoLists, tasks);
            
            return true;
            
        } catch (InterruptedException | IOException e) {
            System.err.println("Failed to import session: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Merge session data from a custom JSON file with existing data (no duplicates)
     * @param users Space containing existing users
     * @param todoLists Space containing existing todo lists
     * @param tasks Space containing existing tasks
     * @param importFilePath Path to import file
     * @return true if merge was successful
     */
    public boolean mergeSession(Space users, Space todoLists, Space tasks, String importFilePath) {
        Path importPath = Paths.get(importFilePath);
        
        if (!Files.exists(importPath)) {
            System.out.println("Import file not found: " + importFilePath);
            return false;
        }

        try (FileReader reader = new FileReader(importPath.toFile())) {
            SessionData session = gson.fromJson(reader, SessionData.class);
            
            if (session == null) {
                System.out.println("Import file is empty or invalid.");
                return false;
            }
            
            // Get existing users to check for duplicates
            java.util.Set<String> existingUsers = new java.util.HashSet<>();
            List<Object[]> existingUserTuples = users.queryAll(new FormalField(String.class));
            if (existingUserTuples != null) {
                for (Object[] tuple : existingUserTuples) {
                    existingUsers.add((String) tuple[0]);
                }
            }
            
            // Get existing list IDs to check for duplicates
            java.util.Set<String> existingListIds = new java.util.HashSet<>();
            List<Object[]> existingListTuples = todoLists.queryAll(
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
            if (existingListTuples != null) {
                for (Object[] tuple : existingListTuples) {
                    existingListIds.add((String) tuple[0]);
                }
            }
            
            // Get existing task IDs (listId + taskId combination) to check for duplicates
            java.util.Set<String> existingTaskKeys = new java.util.HashSet<>();
            List<Object[]> existingTaskTuples = tasks.queryAll(
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
            if (existingTaskTuples != null) {
                for (Object[] tuple : existingTaskTuples) {
                    String listId = (String) tuple[0];
                    String taskId = (String) tuple[1];
                    existingTaskKeys.add(listId + ":" + taskId);
                }
            }
            
            int addedUsers = 0;
            int addedLists = 0;
            int addedTasks = 0;
            int listIdx = existingListIds.size();
            int taskIdx = existingTaskKeys.size();
            
            // Merge users (skip duplicates)
            for (UserData user : session.getUsers()) {
                if (!existingUsers.contains(user.getUsername())) {
                    users.put(user.getUsername());
                    addedUsers++;
                }
            }
            
            // Merge todo lists (skip duplicates)
            for (TodoListData list : session.getTodoLists()) {
                if (!existingListIds.contains(list.getListId())) {
                    String owner = list.getOwner() != null ? list.getOwner() : "";
                    String taskColumnsJson = list.getTaskColumnsJson() != null ? list.getTaskColumnsJson() : "";
                    int priority = list.getPriority() != null ? list.getPriority() : Defaults.PRIORITY;
                    int year = list.getYear() != null ? list.getYear() : Defaults.YEAR;
                    int orderIndex = list.getOrderIndex() != null ? list.getOrderIndex() : listIdx;
                    String location = list.getLocation() != null ? list.getLocation() : "";
                    String description = list.getDescription() != null ? list.getDescription() : "";
                    todoLists.put(list.getListId(), list.getListName(), list.getCompletionPercentage(), owner, taskColumnsJson, priority, year, orderIndex, location, description);
                    addedLists++;
                    listIdx++;
                }
            }
            
            // Merge tasks (skip duplicates)
            for (TaskData task : session.getTasks()) {
                String taskKey = task.getListId() + ":" + task.getTaskId();
                if (!existingTaskKeys.contains(taskKey)) {
                    int priority = task.getPriority() != null ? task.getPriority() : Defaults.PRIORITY;
                    int year = task.getYear() != null ? task.getYear() : Defaults.YEAR;
                    int orderIndex = task.getOrderIndex() != null ? task.getOrderIndex() : taskIdx;
                    String location = task.getLocation() != null ? task.getLocation() : "";
                    String description = task.getDescription() != null ? task.getDescription() : "";
                    tasks.put(task.getListId(), task.getTaskId(), task.getTitle(),
                        task.getAssignee(), task.getStatus(), task.getDueDate(), priority, year, orderIndex, location, description);
                    addedTasks++;
                    taskIdx++;
                }
            }
            
            System.out.println("Session merged from: " + importFilePath);
            System.out.println("  Added: " + addedUsers + " users, " + addedLists + " lists, " + addedTasks + " tasks");
            System.out.println("  Skipped: " + (session.getUsers().size() - addedUsers) + " users, " 
                    + (session.getTodoLists().size() - addedLists) + " lists, " 
                    + (session.getTasks().size() - addedTasks) + " tasks (duplicates)");
            
            // Auto-save after merge
            saveSession(users, todoLists, tasks);
            
            return true;
            
        } catch (InterruptedException | IOException e) {
            System.err.println("Failed to merge session: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Clear all tuples from a space
     */
    private void clearSpace(Space space) throws InterruptedException {
        // Keep removing tuples until there are none left
        // We need to handle different tuple structures, so we try multiple field counts
        boolean foundAny = true;
        while (foundAny) {
            foundAny = false;
            
            // Try single field (users)
            Object[] tuple1 = space.getp(new FormalField(Object.class));
            if (tuple1 != null) {
                foundAny = true;
                continue;
            }
            
            // Try 10 fields (todoLists)
            Object[] tuple10 = space.getp(
                new FormalField(Object.class), new FormalField(Object.class),
                new FormalField(Object.class), new FormalField(Object.class),
                new FormalField(Object.class), new FormalField(Object.class),
                new FormalField(Object.class), new FormalField(Object.class),
                new FormalField(Object.class), new FormalField(Object.class));
            if (tuple10 != null) {
                foundAny = true;
                continue;
            }
            
            // Try 11 fields (tasks)
            Object[] tuple11 = space.getp(
                new FormalField(Object.class), new FormalField(Object.class),
                new FormalField(Object.class), new FormalField(Object.class),
                new FormalField(Object.class), new FormalField(Object.class),
                new FormalField(Object.class), new FormalField(Object.class),
                new FormalField(Object.class), new FormalField(Object.class),
                new FormalField(Object.class));
            if (tuple11 != null) {
                foundAny = true;
                continue;
            }
        }
    }
}
