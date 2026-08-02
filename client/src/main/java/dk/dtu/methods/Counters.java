package dk.dtu.methods;

import dk.dtu.net.ApiSession;
import dk.dtu.net.CounterDto;
import javafx.application.Platform;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Service for the shared "fun counters" (Total Flights, Total Ships, Tour de
 * Brede walks, ...), backed by the shared HTTP API and visible to both account
 * holders (unlike {@link dk.dtu.ViewPrefs}, which is local-only). All calls go
 * through {@link ApiSession#client()}, mirroring {@link Lists}'s style: async
 * loads via a callback marshalled onto the FX thread, synchronous throwing
 * writes for the caller to run off-thread.
 */
public final class Counters {

    private Counters() {
    }

    /** Loads all counters (already ordered by sort, then created_at) async. */
    public static void loadCounters(Consumer<List<CounterDto>> onLoaded) {
        new Thread(() -> {
            try {
                List<CounterDto> counters = ApiSession.get().client().getCounters();
                Platform.runLater(() -> {
                    if (onLoaded != null) {
                        onLoaded.accept(counters != null ? counters : List.of());
                    }
                });
            } catch (Exception ex) {
                ex.printStackTrace();
                ApiSession.get().reportError(ex);
                Platform.runLater(() -> {
                    if (onLoaded != null) {
                        onLoaded.accept(List.of());
                    }
                });
            }
        }, "load-counters").start();
    }

    public static CounterDto createCounter(String label, String description, Integer value, String icon)
            throws Exception {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Counter label cannot be empty");
        }
        return ApiSession.get().client().createCounter(label.trim(),
                (description == null || description.isBlank()) ? null : description.trim(), value, icon);
    }

    public static CounterDto renameCounter(String id, String label) throws Exception {
        requireId(id);
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Counter label cannot be empty");
        }
        return patch(id, "label", label.trim());
    }

    public static CounterDto setDescription(String id, String description) throws Exception {
        requireId(id);
        // A blank/null description clears the field (sent as JSON null).
        return patch(id, "description", (description == null || description.isBlank()) ? null : description.trim());
    }

    public static CounterDto setIcon(String id, String icon) throws Exception {
        requireId(id);
        return patch(id, "icon", (icon == null || icon.isBlank()) ? null : icon.trim());
    }

    public static CounterDto setValue(String id, int value) throws Exception {
        requireId(id);
        return patch(id, "value", value);
    }

    /** Relative bump ({@code +1}, {@code -1}, ...); lands even under a racing concurrent bump. */
    public static CounterDto bump(String id, int delta) throws Exception {
        requireId(id);
        if (delta == 0) {
            throw new IllegalArgumentException("Bump delta cannot be zero");
        }
        return ApiSession.get().client().bumpCounter(id, delta);
    }

    /**
     * Combined edit-dialog save: one PATCH carrying label/description/value/icon
     * together, instead of four separate requests that could race with a
     * concurrent bump on the same counter.
     */
    public static CounterDto updateAll(String id, String label, String description, int value, String icon)
            throws Exception {
        requireId(id);
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Counter label cannot be empty");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("label", label.trim());
        body.put("description", (description == null || description.isBlank()) ? null : description.trim());
        body.put("value", value);
        body.put("icon", icon);
        return ApiSession.get().client().updateCounter(id, body);
    }

    public static CounterDto setSort(String id, int sort) throws Exception {
        requireId(id);
        return patch(id, "sort", sort);
    }

    /** One PATCH per row setting sort to its new 0-based index, mirroring {@link Lists#setListOrderBulk}. */
    public static void setCounterOrderBulk(List<String> orderedCounterIds) throws Exception {
        if (orderedCounterIds == null || orderedCounterIds.isEmpty()) {
            throw new IllegalArgumentException("Counter order cannot be empty");
        }
        for (int i = 0; i < orderedCounterIds.size(); i++) {
            setSort(orderedCounterIds.get(i), i);
        }
    }

    public static void deleteCounter(String id) throws Exception {
        requireId(id);
        ApiSession.get().client().deleteCounter(id);
    }

    private static void requireId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Counter ID cannot be empty");
        }
    }

    private static CounterDto patch(String id, String field, Object value) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(field, value);
        return ApiSession.get().client().updateCounter(id, body);
    }

    /** Defensive copy helper for callers that want a mutable snapshot. */
    public static List<CounterDto> copy(List<CounterDto> counters) {
        return counters == null ? new ArrayList<>() : new ArrayList<>(counters);
    }
}
