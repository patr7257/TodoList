package dk.dtu.methods;

import dk.dtu.shared.TupleSpaces;
import javafx.application.Platform;
import javafx.scene.control.ListView;
import org.jspace.FormalField;
import org.jspace.RemoteSpace;

import java.util.List;

// Service for todo list operations
public class Lists {

    private Lists() {}

    public static void loadTodoLists(ListView<Helpers.ListEntry> listsView, String todoListsUri) {
        new Thread(() -> {
            try {
                RemoteSpace todoLists = new RemoteSpace(todoListsUri);
                List<Object[]> tuples = todoLists.queryAll(
                        new FormalField(String.class),
                        new FormalField(String.class));

                Platform.runLater(() -> {
                    listsView.getItems().clear();
                    for (Object[] t : tuples) {
                        listsView.getItems().add(new Helpers.ListEntry((String) t[0], (String) t[1]));
                    }
                });
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }, "load-todo-lists").start();
    }

    public static void createTodoList(
            String requestsUri,
            String responsesUri,
            String listName) throws Exception {
        
        if (listName == null || listName.isBlank()) {
            throw new IllegalArgumentException("List name cannot be empty");
        }

        Helpers.sendAndWaitForResponse(
            requestsUri,
            responsesUri,
            TupleSpaces.CMD_LIST_CREATE,
            listName, "", "", "");
    }

    public static void deleteTodoList(
            String requestsUri,
            String responsesUri,
            String listId) throws Exception {
        
        if (listId == null || listId.isBlank()) {
            throw new IllegalArgumentException("List ID cannot be empty");
        }

        Helpers.sendAndWaitForResponse(
            requestsUri,
            responsesUri,
            TupleSpaces.CMD_LIST_DELETE,
            listId, "", "", "");
    }
}
