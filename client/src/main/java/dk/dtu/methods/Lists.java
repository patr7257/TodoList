package dk.dtu.methods;

import dk.dtu.ViewPrefs;
import dk.dtu.net.ApiModels.ListDto;
import dk.dtu.net.ApiModels.StateResponse;
import dk.dtu.net.ApiSession;
import javafx.application.Platform;
import javafx.scene.control.ListView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Service for todo-list operations, backed by the shared HTTP API.
 *
 * <p>All calls go through {@link ApiSession#client()}.
 *
 * <p>The desktop-superset list fields (owner, priority, year, location,
 * description) are persisted through PATCH /lists/{id}: their setters send a
 * single-field patch, and a null value clears the field. Per-list
 * visible-column choices ({@code taskColumnsJson}) are a view preference and
 * are persisted locally.
 */
public class Lists {

    private Lists() {}

    // Per-list visible-column choice is a local, per-user view preference stored
    // through ViewPrefs (the single view-state store) under this view id prefix.
    private static final String COLUMN_VIEW_PREFIX = "tasks.cols:";

    public static void loadTodoLists(Consumer<List<Helpers.ListEntry>> onLoaded) {
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

    public static void setListOrderBulk(List<String> orderedListIds) throws Exception {
        if (orderedListIds == null || orderedListIds.isEmpty()) {
            throw new IllegalArgumentException("List order cannot be empty");
        }
        for (int i = 0; i < orderedListIds.size(); i++) {
            ApiSession.get().client().updateList(orderedListIds.get(i), null, i);
        }
    }

    public static void setListPriority(String listId, int priority) throws Exception {
        requireId(listId);
        patchList(listId, "priority", priority);
    }

    public static void setListYear(String listId, int year) throws Exception {
        requireId(listId);
        patchList(listId, "year", year);
    }

    public static void setListLocation(String listId, String location) throws Exception {
        requireId(listId);
        // A blank/null location clears the field (sent as JSON null).
        patchList(listId, "location", (location == null || location.isBlank()) ? null : location.trim());
    }

    public static void setListDescription(String listId, String description) throws Exception {
        requireId(listId);
        // A blank/null description clears the field (sent as JSON null).
        patchList(listId, "description", (description == null || description.isBlank()) ? null : description.trim());
    }

    public static void renameTodoList(String listId, String newName) throws Exception {
        requireId(listId);
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("List name cannot be empty");
        }
        ApiSession.get().client().updateList(listId, newName.trim(), null);
    }

    /**
     * Sets (non-null {@code ownerId}) or clears (null {@code ownerId}) a
     * list's real owner reference. Always sends the resolved legacy
     * {@code owner} display name in the SAME request alongside
     * {@code ownerId}: a freshly auto-updated client can briefly talk to a
     * pre-#45 API (installers and the API release independently on every
     * merge to main), where an ownerId-only patch would produce an empty
     * column set and a 400.
     */
    public static void setListOwnerId(String listId, String ownerId) throws Exception {
        requireId(listId);
        String ownerName = (ownerId == null || ownerId.isBlank()) ? null : ApiSession.get().nameForId(ownerId);
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("ownerId", (ownerId == null || ownerId.isBlank()) ? null : ownerId);
        patch.put("owner", ownerName);
        ApiSession.get().client().updateList(listId, patch);
    }

    /** Per-list visible-column choice. Stored locally per user (view preference). */
    public static void setTaskColumnsForList(String listId, String taskColumnsJson) throws Exception {
        requireId(listId);
        ViewPrefs.putString(COLUMN_VIEW_PREFIX + listId, taskColumnsJson != null ? taskColumnsJson : "");
    }

    /**
     * Creates a list, optionally owned by a real user id (never a display
     * name -- see the "Also fix" note on C_MainMenu's create-list dialog). The
     * resolved owner name is sent on the create call itself; when an owner is
     * given, a follow-up PATCH then also sets {@code ownerId} (see
     * {@link #setListOwnerId}, which sends both keys together for the same
     * cross-version-compatibility reason). {@code createList(name, owner)}
     * only accepts the legacy free-text field, so this is the smallest change
     * that gets a real owner reference onto a newly created list.
     */
    public static void createTodoList(String listName, String ownerId) throws Exception {
        if (listName == null || listName.isBlank()) {
            throw new IllegalArgumentException("List name cannot be empty");
        }
        String ownerIdOrNull = (ownerId == null || ownerId.isBlank()) ? null : ownerId.trim();
        String ownerName = ownerIdOrNull != null ? ApiSession.get().nameForId(ownerIdOrNull) : null;

        ListDto created = ApiSession.get().client().createList(listName.trim(), ownerName);
        if (ownerIdOrNull != null && created != null && created.id() != null) {
            setListOwnerId(created.id(), ownerIdOrNull);
        }
    }

    public static void deleteTodoList(String listId) throws Exception {
        requireId(listId);
        ApiSession.get().client().deleteList(listId);
    }

    /**
     * The saved task-columns JSON for a single list. Reads the locally stored
     * view preference first; falls back to the value the API returns in state.
     */
    public static String getTaskColumnsJsonForList(String listId) throws Exception {
        requireId(listId);
        String local = ViewPrefs.getString(COLUMN_VIEW_PREFIX + listId, "");
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

    /**
     * PATCHes a single desktop-superset field on a list. A null value clears the
     * field (serialized as JSON null by the client).
     */
    private static void patchList(String listId, String field, Object value) throws Exception {
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put(field, value);
        ApiSession.get().client().updateList(listId, patch);
    }
}
