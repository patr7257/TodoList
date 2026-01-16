package dk.dtu;

import org.jspace.FormalField;
import org.jspace.Space;

// Simple in-memory database loader for preset users, todo lists, and tasks
public class Database {

    // Initialize a database with preset users, todo lists, and tasks
    public static void loadDatabase(Space users, Space todoLists, Space tasks) throws InterruptedException {
        
        // LOAD USERS  
        users.put("Alberto");
        users.put("Johan");
        users.put("Kajsa");
        users.put("Lizette");
        users.put("Patrick");
        System.out.println("Loaded 5 users");

        // LOAD TODO LISTS  
        todoLists.put("l1", "Shopping");
        todoLists.put("l2", "School");
        todoLists.put("l3", "Work");
        todoLists.put("l4", "Chores");
        todoLists.put("l5", "Trips");

        // LOAD TASKS  
        int taskCount = 0;

        // Shopping tasks (l1)
        tasks.put("l1", "tl1_1", "Buy groceries", "Alberto", "NOT_STARTED", "");
        tasks.put("l1", "tl1_2", "Get milk and eggs", "Lizette", "IN_PROGRESS", "");
        tasks.put("l1", "tl1_3", "Purchase new shoes", "Kajsa", "DELAYED", "");
        tasks.put("l1", "tl1_4", "Order online items", "Johan", "NEED_HELP", "");
        tasks.put("l1", "tl1_5", "Pick up prescription", "Patrick", "DONE", "");
        taskCount += 5;

        // School tasks (l2)
        tasks.put("l2", "tl2_1", "Submit math homework", "Alberto", "NOT_STARTED", "");
        tasks.put("l2", "tl2_2", "Study for history exam", "Lizette", "IN_PROGRESS", "");
        tasks.put("l2", "tl2_3", "Complete science project", "Kajsa", "DELAYED", "");
        tasks.put("l2", "tl2_4", "Read chapter 5", "Johan", "NEED_HELP", "");
        tasks.put("l2", "tl2_5", "Prepare presentation", "Patrick", "DONE", "");
        taskCount += 5;

        // Work tasks (l3)
        tasks.put("l3", "tl3_1", "Finish quarterly report", "Alberto", "NOT_STARTED", "");
        tasks.put("l3", "tl3_2", "Attend team meeting", "Lizette", "IN_PROGRESS", "");
        tasks.put("l3", "tl3_3", "Review code changes", "Kajsa", "DELAYED", "");
        tasks.put("l3", "tl3_4", "Update documentation", "Johan", "NEED_HELP", "");
        tasks.put("l3", "tl3_5", "Email client feedback", "Patrick", "DONE", "");
        taskCount += 5;

        // Chores tasks (l4)
        tasks.put("l4", "tl4_1", "Vacuum living room", "Alberto", "NOT_STARTED", "");
        tasks.put("l4", "tl4_2", "Do laundry", "Lizette", "IN_PROGRESS", "");
        tasks.put("l4", "tl4_3", "Wash dishes", "Kajsa", "DELAYED", "");
        tasks.put("l4", "tl4_4", "Take out trash", "Johan", "NEED_HELP", "");
        tasks.put("l4", "tl4_5", "Water plants", "Patrick", "DONE", "");
        taskCount += 5;

        // Trips tasks (l5)
        tasks.put("l5", "tl5_1", "Book flight tickets", "Alberto", "NOT_STARTED", "");
        tasks.put("l5", "tl5_2", "Reserve hotel room", "Lizette", "IN_PROGRESS", "");
        tasks.put("l5", "tl5_3", "Plan itinerary", "Kajsa", "DELAYED", "");
        tasks.put("l5", "tl5_4", "Pack luggage", "Johan", "NEED_HELP", "");
        tasks.put("l5", "tl5_5", "Check passport validity", "Patrick", "DONE", "");
        taskCount += 5;

        System.out.println("Loaded 5 lists with " + taskCount + " tasks");
    }

    // Returns the current count of todo lists in the system
    public static int getTodoListCount(Space todoLists) throws InterruptedException {
        return todoLists.queryAll(
                new FormalField(String.class),
                new FormalField(String.class)).size();
    }
}