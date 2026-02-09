package dk.dtu;

import dk.dtu.shared.Defaults;
import org.jspace.FormalField;
import org.jspace.Space;

// Simple in-memory database loader for preset users, todo lists, and tasks
public class Database {

    // Initialize a database with preset users, todo lists, and tasks
    public static void loadDatabase(Space users, Space todoLists, Space tasks) throws InterruptedException {
        
        // LOAD USERS - including main users (Eline, Patrick) and test users
        users.put("Alice");
        users.put("Bob");
        users.put("Charlize");
        users.put("Eline");
        users.put("Patrick");
        System.out.println("Loaded 5 users: Alice, Bob, Charlize, Eline, Patrick");

        // LOAD TODO LISTS with completion percentage
        // Lists: (listId, listName, completionPercentage, owner, taskColumnsJson, priority, year, orderIndex, location, description)
        todoLists.put("l1", "Shopping", 20, "Patrick", Defaults.TASK_COLUMNS_JSON, Defaults.PRIORITY, Defaults.YEAR, 0, "", "");
        todoLists.put("l2", "School", 40, "Alice", Defaults.TASK_COLUMNS_JSON, Defaults.PRIORITY, Defaults.YEAR, 1, "", "");
        todoLists.put("l3", "Work", 60, "Eline", Defaults.TASK_COLUMNS_JSON, Defaults.PRIORITY, Defaults.YEAR, 2, "", "");
        todoLists.put("l4", "Chores", 90, "Bob", Defaults.TASK_COLUMNS_JSON, Defaults.PRIORITY, Defaults.YEAR, 3, "", "");
        todoLists.put("l5", "Trips", 50, "Charlize", Defaults.TASK_COLUMNS_JSON, Defaults.PRIORITY, Defaults.YEAR, 4, "", "");

        // LOAD TASKS  (listId, taskId, title, assignee, status, dueDate, priority, year, orderIndex, location, description)
        int taskCount = 0;

        // Shopping tasks (l1) - 3 tasks for ~20% completion
        tasks.put("l1", "tl1_1", "Buy groceries", "Alice", "NOT_STARTED", "2026-01-15", Defaults.PRIORITY, Defaults.YEAR, 0, "", "");
        tasks.put("l1", "tl1_2", "Get milk and eggs", "Eline", "NOT_STARTED", "2026-01-17", Defaults.PRIORITY, Defaults.YEAR, 1, "", "");
        tasks.put("l1", "tl1_3", "Purchase new shoes", "Bob", "IN_PROGRESS", "2026-01-20", Defaults.PRIORITY, Defaults.YEAR, 2, "", "");
        taskCount += 3;

        // School tasks (l2) - 4 tasks for ~40% completion
        tasks.put("l2", "tl2_1", "Submit math homework", "Alice", "NOT_STARTED", "2026-01-25", Defaults.PRIORITY, Defaults.YEAR, 0, "", "");
        tasks.put("l2", "tl2_2", "Study for history exam", "Eline", "IN_PROGRESS", "", Defaults.PRIORITY, Defaults.YEAR, 1, "", "");
        tasks.put("l2", "tl2_3", "Complete science project", "Bob", "DELAYED", "", Defaults.PRIORITY, Defaults.YEAR, 2, "", "");
        tasks.put("l2", "tl2_4", "Prepare presentation", "Patrick", "DONE", "2026-01-15", Defaults.PRIORITY, Defaults.YEAR, 3, "", "");
        taskCount += 4;

        // Work tasks (l3) - 6 tasks for ~60% completion
        tasks.put("l3", "tl3_1", "Finish quarterly report", "Alice", "DONE", "2026-01-12", Defaults.PRIORITY, Defaults.YEAR, 0, "", "");
        tasks.put("l3", "tl3_2", "Attend team meeting", "Eline", "DONE", "2026-01-16", Defaults.PRIORITY, Defaults.YEAR, 1, "", "");
        tasks.put("l3", "tl3_3", "Review code changes", "Bob", "DONE", "2026-01-14", Defaults.PRIORITY, Defaults.YEAR, 2, "", "");
        tasks.put("l3", "tl3_4", "Update documentation", "Charlize", "IN_PROGRESS", "", Defaults.PRIORITY, Defaults.YEAR, 3, "", "");
        tasks.put("l3", "tl3_5", "Email client feedback", "Patrick", "NOT_STARTED", "2026-01-10", Defaults.PRIORITY, Defaults.YEAR, 4, "", "");
        tasks.put("l3", "tl3_6", "Prepare budget report", "Alice", "NOT_STARTED", "2026-01-22", Defaults.PRIORITY, Defaults.YEAR, 5, "", "");
        taskCount += 6;

        // Chores tasks (l4) - 8 tasks for ~90% completion
        tasks.put("l4", "tl4_1", "Vacuum living room", "Alice", "DONE", "", Defaults.PRIORITY, Defaults.YEAR, 0, "", "");
        tasks.put("l4", "tl4_2", "Do laundry", "Eline", "DONE", "", Defaults.PRIORITY, Defaults.YEAR, 1, "", "");
        tasks.put("l4", "tl4_3", "Wash dishes", "Bob", "DONE", "", Defaults.PRIORITY, Defaults.YEAR, 2, "", "");
        tasks.put("l4", "tl4_4", "Take out trash", "Charlize", "DONE", "", Defaults.PRIORITY, Defaults.YEAR, 3, "", "");
        tasks.put("l4", "tl4_5", "Water plants", "Patrick", "DONE", "", Defaults.PRIORITY, Defaults.YEAR, 4, "", "");
        tasks.put("l4", "tl4_6", "Clean windows", "Alice", "DONE", "", Defaults.PRIORITY, Defaults.YEAR, 5, "", "");
        tasks.put("l4", "tl4_7", "Organize garage", "Eline", "DONE", "", Defaults.PRIORITY, Defaults.YEAR, 6, "", "");
        tasks.put("l4", "tl4_8", "Grocery shopping", "Bob", "IN_PROGRESS", "", Defaults.PRIORITY, Defaults.YEAR, 7, "", "");
        taskCount += 8;

        // Trips tasks (l5) - 9 tasks for ~50% completion
        tasks.put("l5", "tl5_1", "Book flight tickets", "Alice", "DONE", "", Defaults.PRIORITY, Defaults.YEAR, 0, "", "");
        tasks.put("l5", "tl5_2", "Reserve hotel room", "Eline", "DONE", "", Defaults.PRIORITY, Defaults.YEAR, 1, "", "");
        tasks.put("l5", "tl5_3", "Plan itinerary", "Bob", "DONE", "", Defaults.PRIORITY, Defaults.YEAR, 2, "", "");
        tasks.put("l5", "tl5_4", "Pack luggage", "Charlize", "IN_PROGRESS", "", Defaults.PRIORITY, Defaults.YEAR, 3, "", "");
        tasks.put("l5", "tl5_5", "Check passport validity", "Patrick", "IN_PROGRESS", "", Defaults.PRIORITY, Defaults.YEAR, 4, "", "");
        tasks.put("l5", "tl5_6", "Buy travel insurance", "Alice", "NOT_STARTED", "", Defaults.PRIORITY, Defaults.YEAR, 5, "", "");
        tasks.put("l5", "tl5_7", "Exchange currency", "Eline", "NOT_STARTED", "", Defaults.PRIORITY, Defaults.YEAR, 6, "", "");
        tasks.put("l5", "tl5_8", "Download maps offline", "Bob", "NOT_STARTED", "", Defaults.PRIORITY, Defaults.YEAR, 7, "", "");
        tasks.put("l5", "tl5_9", "Notify credit card company", "Charlize", "NOT_STARTED", "", Defaults.PRIORITY, Defaults.YEAR, 8, "", "");
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