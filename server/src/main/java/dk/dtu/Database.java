package dk.dtu;

import org.jspace.FormalField;
import org.jspace.Space;

// Simple in-memory database loader for preset users, todo lists, and tasks
public class Database {

    // Default visible task columns for new lists (JSON array string)
    private static final String DEFAULT_TASK_COLUMNS_JSON = "[\"reorder\",\"title\",\"status\",\"dueDate\",\"owner\",\"delete\"]";

    private static final int DEFAULT_PRIORITY = 5;
    private static final int DEFAULT_YEAR = 0;

    // Initialize a database with preset users, todo lists, and tasks
    public static void loadDatabase(Space users, Space todoLists, Space tasks) throws InterruptedException {
        
        // LOAD USERS  
        users.put("Alberto");
        users.put("Johan");
        users.put("Kajsa");
        users.put("Lizette");
        users.put("Patrick");
        System.out.println("Loaded 5 users");

        // LOAD TODO LISTS with completion percentage
        // Lists: (listId, listName, completionPercentage, owner, taskColumnsJson, priority, year, orderIndex, location, description)
        todoLists.put("l1", "Shopping", 20, "Patrick", DEFAULT_TASK_COLUMNS_JSON, DEFAULT_PRIORITY, DEFAULT_YEAR, 0, "", "");
        todoLists.put("l2", "School", 40, "Alberto", DEFAULT_TASK_COLUMNS_JSON, DEFAULT_PRIORITY, DEFAULT_YEAR, 1, "", "");
        todoLists.put("l3", "Work", 60, "Lizette", DEFAULT_TASK_COLUMNS_JSON, DEFAULT_PRIORITY, DEFAULT_YEAR, 2, "", "");
        todoLists.put("l4", "Chores", 90, "Kajsa", DEFAULT_TASK_COLUMNS_JSON, DEFAULT_PRIORITY, DEFAULT_YEAR, 3, "", "");
        todoLists.put("l5", "Trips", 50, "Johan", DEFAULT_TASK_COLUMNS_JSON, DEFAULT_PRIORITY, DEFAULT_YEAR, 4, "", "");

        // LOAD TASKS  (listId, taskId, title, assignee, status, dueDate, priority, year, orderIndex, location, description)
        int taskCount = 0;

        // Shopping tasks (l1) - 3 tasks for ~20% completion
        tasks.put("l1", "tl1_1", "Buy groceries", "Alberto", "NOT_STARTED", "2026-01-15", DEFAULT_PRIORITY, DEFAULT_YEAR, 0, "", "");
        tasks.put("l1", "tl1_2", "Get milk and eggs", "Lizette", "NOT_STARTED", "2026-01-17", DEFAULT_PRIORITY, DEFAULT_YEAR, 1, "", "");
        tasks.put("l1", "tl1_3", "Purchase new shoes", "Kajsa", "IN_PROGRESS", "2026-01-20", DEFAULT_PRIORITY, DEFAULT_YEAR, 2, "", "");
        taskCount += 3;

        // School tasks (l2) - 4 tasks for ~40% completion
        tasks.put("l2", "tl2_1", "Submit math homework", "Alberto", "NOT_STARTED", "2026-01-25", DEFAULT_PRIORITY, DEFAULT_YEAR, 0, "", "");
        tasks.put("l2", "tl2_2", "Study for history exam", "Lizette", "IN_PROGRESS", "", DEFAULT_PRIORITY, DEFAULT_YEAR, 1, "", "");
        tasks.put("l2", "tl2_3", "Complete science project", "Kajsa", "DELAYED", "", DEFAULT_PRIORITY, DEFAULT_YEAR, 2, "", "");
        tasks.put("l2", "tl2_4", "Prepare presentation", "Patrick", "DONE", "2026-01-15", DEFAULT_PRIORITY, DEFAULT_YEAR, 3, "", "");
        taskCount += 4;

        // Work tasks (l3) - 6 tasks for ~60% completion
        tasks.put("l3", "tl3_1", "Finish quarterly report", "Alberto", "DONE", "2026-01-12", DEFAULT_PRIORITY, DEFAULT_YEAR, 0, "", "");
        tasks.put("l3", "tl3_2", "Attend team meeting", "Lizette", "DONE", "2026-01-16", DEFAULT_PRIORITY, DEFAULT_YEAR, 1, "", "");
        tasks.put("l3", "tl3_3", "Review code changes", "Kajsa", "DONE", "2026-01-14", DEFAULT_PRIORITY, DEFAULT_YEAR, 2, "", "");
        tasks.put("l3", "tl3_4", "Update documentation", "Johan", "IN_PROGRESS", "", DEFAULT_PRIORITY, DEFAULT_YEAR, 3, "", "");
        tasks.put("l3", "tl3_5", "Email client feedback", "Patrick", "NOT_STARTED", "2026-01-10", DEFAULT_PRIORITY, DEFAULT_YEAR, 4, "", "");
        tasks.put("l3", "tl3_6", "Prepare budget report", "Alberto", "NOT_STARTED", "2026-01-22", DEFAULT_PRIORITY, DEFAULT_YEAR, 5, "", "");
        taskCount += 6;

        // Chores tasks (l4) - 8 tasks for ~90% completion
        tasks.put("l4", "tl4_1", "Vacuum living room", "Alberto", "DONE", "", DEFAULT_PRIORITY, DEFAULT_YEAR, 0, "", "");
        tasks.put("l4", "tl4_2", "Do laundry", "Lizette", "DONE", "", DEFAULT_PRIORITY, DEFAULT_YEAR, 1, "", "");
        tasks.put("l4", "tl4_3", "Wash dishes", "Kajsa", "DONE", "", DEFAULT_PRIORITY, DEFAULT_YEAR, 2, "", "");
        tasks.put("l4", "tl4_4", "Take out trash", "Johan", "DONE", "", DEFAULT_PRIORITY, DEFAULT_YEAR, 3, "", "");
        tasks.put("l4", "tl4_5", "Water plants", "Patrick", "DONE", "", DEFAULT_PRIORITY, DEFAULT_YEAR, 4, "", "");
        tasks.put("l4", "tl4_6", "Clean windows", "Alberto", "DONE", "", DEFAULT_PRIORITY, DEFAULT_YEAR, 5, "", "");
        tasks.put("l4", "tl4_7", "Organize garage", "Lizette", "DONE", "", DEFAULT_PRIORITY, DEFAULT_YEAR, 6, "", "");
        tasks.put("l4", "tl4_8", "Grocery shopping", "Kajsa", "IN_PROGRESS", "", DEFAULT_PRIORITY, DEFAULT_YEAR, 7, "", "");
        taskCount += 8;

        // Trips tasks (l5) - 9 tasks for ~50% completion
        tasks.put("l5", "tl5_1", "Book flight tickets", "Alberto", "DONE", "", DEFAULT_PRIORITY, DEFAULT_YEAR, 0, "", "");
        tasks.put("l5", "tl5_2", "Reserve hotel room", "Lizette", "DONE", "", DEFAULT_PRIORITY, DEFAULT_YEAR, 1, "", "");
        tasks.put("l5", "tl5_3", "Plan itinerary", "Kajsa", "DONE", "", DEFAULT_PRIORITY, DEFAULT_YEAR, 2, "", "");
        tasks.put("l5", "tl5_4", "Pack luggage", "Johan", "IN_PROGRESS", "", DEFAULT_PRIORITY, DEFAULT_YEAR, 3, "", "");
        tasks.put("l5", "tl5_5", "Check passport validity", "Patrick", "IN_PROGRESS", "", DEFAULT_PRIORITY, DEFAULT_YEAR, 4, "", "");
        tasks.put("l5", "tl5_6", "Buy travel insurance", "Alberto", "NOT_STARTED", "", DEFAULT_PRIORITY, DEFAULT_YEAR, 5, "", "");
        tasks.put("l5", "tl5_7", "Exchange currency", "Lizette", "NOT_STARTED", "", DEFAULT_PRIORITY, DEFAULT_YEAR, 6, "", "");
        tasks.put("l5", "tl5_8", "Download maps offline", "Kajsa", "NOT_STARTED", "", DEFAULT_PRIORITY, DEFAULT_YEAR, 7, "", "");
        tasks.put("l5", "tl5_9", "Notify credit card company", "Johan", "NOT_STARTED", "", DEFAULT_PRIORITY, DEFAULT_YEAR, 8, "", "");
        taskCount += 9;

        System.out.println("Loaded 5 lists with " + taskCount + " tasks");
    }

    // Returns the current count of todo lists in the system
    public static int getTodoListCount(Space todoLists) throws InterruptedException {
        return todoLists.queryAll(
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(Integer.class),
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(Integer.class),
            new FormalField(Integer.class),
                new FormalField(Integer.class),
                new FormalField(String.class),
                new FormalField(String.class)).size();
    }
}