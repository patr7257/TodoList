package dk.dtu.model;

import java.io.Serializable;
import java.util.List;

// Class for Projects / To-Do Lists
public final class TodoList implements Serializable {
        private final String id;
        private final String name;
        private final List<Task> tasks;

        public TodoList(String id, String name, List<Task> tasks) {
                this.id = id;
                this.name = name;
                this.tasks = tasks;
        }
        
        public String getId() {
                return id;
        }

        public String getName() {
                return name;
        }

        public List<Task> getTasks() {
                return tasks;
        }
}