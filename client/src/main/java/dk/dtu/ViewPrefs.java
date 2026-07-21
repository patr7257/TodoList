package dk.dtu;

import com.google.gson.Gson;
import dk.dtu.net.ApiModels.CurrentUser;
import dk.dtu.net.ApiSession;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.prefs.Preferences;

/**
 * Local, per-user persistence of desktop table/view state (column order and
 * visibility, column widths, sort, and filters) so the app opens exactly as the
 * user left it.
 *
 * <p>State lives under {@code userRoot().node("dk/dtu/viewstate/<userKey>")} so
 * two users on the same machine keep separate layouts. The user key is the
 * signed-in user's id (falling back to email, then {@code "anon"}). Each "view"
 * (e.g. {@code "lists"}, {@code "tasks"}, or the per-list column choice
 * {@code "tasks.cols:<listId>"}) is stored as a compact JSON string.
 *
 * <p>This is the single mechanism for view-state persistence: the older
 * per-class column-visibility preferences now route through here so there is no
 * second parallel store. All reads are defensive: malformed or stale state is
 * ignored and callers fall back to defaults, never throwing.
 */
public final class ViewPrefs {

    private static final Gson GSON = new Gson();
    private static final String ROOT = "dk/dtu/viewstate";
    private static final String ANON = "anon";
    private static final int MAX_SEGMENT = 80; // Preferences.MAX_NAME_LENGTH

    private ViewPrefs() {}

    // -------------------------------------------------------------------------
    // Serializable state model
    // -------------------------------------------------------------------------

    /**
     * The persisted view state. All fields are optional; a fresh (all-null /
     * empty) instance means "use current defaults".
     */
    public static final class ViewState {
        /** Ordered list of visible column ids (order == column order). */
        public List<String> columns = new ArrayList<>();
        /** Column id -> pixel width, for resizable columns only. */
        public Map<String, Double> widths = new LinkedHashMap<>();
        /** Active sort column id, or null for no sort. */
        public String sortColumn;
        /** Sort direction when {@link #sortColumn} is set. */
        public boolean sortAscending = true;
        /** Free-form filter name -> value (string-encoded). */
        public Map<String, String> filters = new LinkedHashMap<>();

        /** Defensive copy that drops any column ids not in {@code knownIds}. */
        public ViewState sanitized(Collection<String> knownIds) {
            Set<String> known = new LinkedHashSet<>(knownIds == null ? List.of() : knownIds);
            ViewState out = new ViewState();

            if (columns != null) {
                for (String id : columns) {
                    if (id != null && known.contains(id) && !out.columns.contains(id)) {
                        out.columns.add(id);
                    }
                }
            }
            if (widths != null) {
                for (Map.Entry<String, Double> e : widths.entrySet()) {
                    if (e.getKey() != null && known.contains(e.getKey())
                            && e.getValue() != null && e.getValue() > 0 && !e.getValue().isNaN()) {
                        out.widths.put(e.getKey(), e.getValue());
                    }
                }
            }
            out.sortColumn = (sortColumn != null && known.contains(sortColumn)) ? sortColumn : null;
            out.sortAscending = sortAscending;
            if (filters != null) {
                out.filters.putAll(filters);
            }
            return out;
        }
    }

    // -------------------------------------------------------------------------
    // User key
    // -------------------------------------------------------------------------

    /** Stable key for the signed-in user: id, else email, else {@code "anon"}. */
    public static String currentUserKey() {
        try {
            CurrentUser u = ApiSession.get().currentUser();
            if (u != null) {
                if (u.id() != null && !u.id().isBlank()) {
                    return u.id().trim();
                }
                if (u.email() != null && !u.email().isBlank()) {
                    return u.email().trim();
                }
            }
        } catch (Throwable ignored) {
            // fall through to anonymous
        }
        return ANON;
    }

    /** Reduce an arbitrary key to a safe, bounded Preferences node/key segment. */
    static String sanitizeSegment(String raw) {
        if (raw == null || raw.isBlank()) {
            return ANON;
        }
        String cleaned = raw.trim().replaceAll("[^A-Za-z0-9._:-]", "_");
        if (cleaned.length() > MAX_SEGMENT) {
            cleaned = "h" + Integer.toHexString(raw.hashCode());
        }
        return cleaned.isBlank() ? ANON : cleaned;
    }

    private static Preferences node(String userKey) {
        return Preferences.userRoot().node(ROOT + "/" + sanitizeSegment(userKey));
    }

    // -------------------------------------------------------------------------
    // Raw string storage
    // -------------------------------------------------------------------------

    public static String getString(String userKey, String viewId, String fallback) {
        try {
            return node(userKey).get(sanitizeSegment(viewId), fallback);
        } catch (Throwable t) {
            return fallback;
        }
    }

    public static void putString(String userKey, String viewId, String value) {
        try {
            if (value == null) {
                node(userKey).remove(sanitizeSegment(viewId));
            } else {
                node(userKey).put(sanitizeSegment(viewId), value);
            }
        } catch (Throwable ignored) {
            // never let a persistence failure break the UI
        }
    }

    // current-user convenience
    public static String getString(String viewId, String fallback) {
        return getString(currentUserKey(), viewId, fallback);
    }

    public static void putString(String viewId, String value) {
        putString(currentUserKey(), viewId, value);
    }

    // -------------------------------------------------------------------------
    // ViewState storage
    // -------------------------------------------------------------------------

    public static ViewState load(String userKey, String viewId) {
        return fromJson(getString(userKey, viewId, null));
    }

    public static void save(String userKey, String viewId, ViewState state) {
        putString(userKey, viewId, toJson(state));
    }

    // current-user convenience
    public static ViewState load(String viewId) {
        return load(currentUserKey(), viewId);
    }

    public static void save(String viewId, ViewState state) {
        save(currentUserKey(), viewId, state);
    }

    // -------------------------------------------------------------------------
    // Serialization (pure, null-safe)
    // -------------------------------------------------------------------------

    public static String toJson(ViewState state) {
        try {
            return GSON.toJson(state == null ? new ViewState() : state);
        } catch (Throwable t) {
            return "{}";
        }
    }

    /** Parse stored JSON into a {@link ViewState}; never null, never throws. */
    public static ViewState fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new ViewState();
        }
        try {
            ViewState parsed = GSON.fromJson(json, ViewState.class);
            if (parsed == null) {
                return new ViewState();
            }
            if (parsed.columns == null) parsed.columns = new ArrayList<>();
            if (parsed.widths == null) parsed.widths = new LinkedHashMap<>();
            if (parsed.filters == null) parsed.filters = new LinkedHashMap<>();
            return parsed;
        } catch (Throwable t) {
            return new ViewState();
        }
    }
}
