package dk.dtu.methods;

import dk.dtu.net.ApiModels.ListDto;
import dk.dtu.net.ApiModels.StateResponse;
import dk.dtu.net.ApiSession;
import javafx.application.Platform;
import javafx.scene.control.ListView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

/**
 * Service for todo-list operations, backed by the shared HTTP API.
 *
 * <p>The public method signatures are unchanged from the jSpace version (the
 * scenes and column cells still pass {@code requestsUri}/{@code responsesUri}
 * /{@code todoListsUri} strings); those transport arguments are now ignored and
 * the work goes through {@link ApiSession#client()}.
 *
 * <p>Note on the API's current shape: only list name and sort are writable. The
 * desktop-superset list fields (owner, priority, year, location, description)
 * are readable in GET /state but have no write endpoint yet, so their setters
 * throw {@link UnsupportedOperationException}. Per-list visible-column choices
 * ({@code taskColumnsJson}) are a view preference and are persisted locally.
 */
public class Lists {

    private Lists() {}

    private static final Preferences COLUMN_PREFS = Preferences.userNodeForPackage(Lists.class);
    private static final String COLUMN_KEY_PREFIX = "taskColumns.";

    public static void loadTodoLists(ListView<Helpers.ListEntry> listsView, String todoListsUri) {
        loadTodoLists(todoListsUri, entries -> listsView.getItems().setAll(entries));
    }

    public static void loadTodoLists(String todoListsUri, Consumer<List<Helpers.ListEntry>> onLoaded) {
        new Thread(() -> {
            try {
                StateResponse state = ApiSession.get().fetchState();
                List<Helpers.ListEntry> entries = new ArrayList<>();
                if (state != null && state.lists() != null) {
                    List<ListDto> lists = new ArrayList<>(state.lists());
                    lists.sort(Comparator.comparingInt(ListDto::sort));
                    for (ListDto l : lists) {
                        if (l != null) {
                            entries.add(Helpers.toListEntry(l));
                        }
                    }
                }
                Platform.runLater(() -> {
                    if (onLoaded != null) {
                        onLoaded.accept(entries);
                    }
                });
            } catch (Exception ex) {
                ex.printStackTrace();
                ApiSession.get().reportError(ex);
            }
        }, "load-todo-lists").start();
    }

    public static void setListOrderBulk(String requestsUri, String responsesUri, List<String> orderedListIds) throws Exception {
        if (orderedListIds == null || orderedListIds.isEmpty()) {
            throw new IllegalArgumentException("List order cannot be empty");
        }
        for (int i = 0; i < orderedListIds.size(); i++) {
            ApiSession.get().client().updateList(orderedListIds.get(i), null, i);
        }
    }

    public static void setListPriority(String requestsUri, String responsesUri, String listId, int priority) throws Exception {
        requireId(listId);
        throw unsupported("list priority");
    }

    public static void setListYear(String requestsUri, String responsesUri, String listId, int year) throws Exception {
        requireId(listId);
        throw unsupported("list year");
    }

    public static void setListLocation(String requestsUri, String responsesUri, String listId, String location) throws Exception {
        requireId(listId);
        throw unsupported("list location");
    }

    public static void setListDescription(String requestsUri, String responsesUri, String listId, String description) throws Exception {
        requireId(listId);
        throw unsupported("list description");
    }

    public static void renameTodoList(String requestsUri, String responsesUri, String listId, String newName) throws Exception {
        requireId(listId);
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("List name cannot be empty");
        }
        ApiSession.get().client().updateList(listId, newName.trim(), null);
    }

    public static void setListOwner(String requestsUri, String responsesUri, String listId, String owner) throws Exception {
        requireId(listId);
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("Owner cannot be empty");
        }
        throw unsupported("list owner");
    }

    public static void clearListOwner(String requestsUri, String responsesUri, String listId) throws Exception {
        requireId(listId);
        throw unsupported("list owner");
    }

    /** Per-list visible-column choice. Stored locally (view preference). */
    public static void setTaskColumnsForList(String requestsUri, String responsesUri, String listId, String taskColumnsJson) throws Exception {
        requireId(listId);
        COLUMN_PREFS.put(COLUMN_KEY_PREFIX + listId, taskColumnsJson != null ? taskColumnsJson : "");
    }

    public static void createTodoList(String requestsUri, String responsesUri, String listName, String owner) throws Exception {
        if (listName == null || listName.isBlank()) {
            throw new IllegalArgumentException("List name cannot be empty");
        }
        // The API's POST /lists accepts a name only; the owner is not writable yet.
        ApiSession.get().client().createList(listName.trim());
    }

    public static void deleteTodoList(String requestsUri, String responsesUri, String listId) throws Exception {
        requireId(listId);
        ApiSession.get().client().deleteList(listId);
    }

    /**
     * The saved task-columns JSON for a single list. Reads the locally stored
     * view preference first; falls back to the value the API returns in state.
     */
    public static String getTaskColumnsJsonForList(String todoListsUri, String listId) throws Exception {
        requireId(listId);
        String local = COLUMN_PREFS.get(COLUMN_KEY_PREFIX + listId, "");
        if (local != null && !local.isBlank()) {
            return local;
        }
        StateResponse state = ApiSession.get().fetchState();
        if (state != null && state.lists() != null) {
            for (ListDto l : state.lists()) {
                if (l != null && listId.equals(l.id()) && l.taskColumnsJson() != null) {
                    return l.taskColumnsJson();
                }
            }
        }
        return "";
    }

    private static void requireId(String listId) {
        if (listId == null || listId.isBlank()) {
            throw new IllegalArgumentException("List ID cannot be empty");
        }
    }

    private static UnsupportedOperationException unsupported(String field) {
        return new UnsupportedOperationException(
                "Editing " + field + " is not supported by the todo API yet.");
    }
}
