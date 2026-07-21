package dk.dtu.api.domain;

import java.util.List;

import dk.dtu.shared.TaskStatus;

/**
 * Completion-percentage derivation for a list, driven by the shared
 * {@link TaskStatus} mapping (NOT_STARTED=0, IN_PROGRESS/DELAYED/NEED_HELP=50,
 * DONE=100). A list's completion is the average of its items' status
 * percentages, rounded to the nearest whole percent. An empty list is 0.
 */
public final class Completion {

    private Completion() {
    }

    public static int forItems(List<ItemRow> items) {
        if (items == null || items.isEmpty()) {
            return 0;
        }
        long sum = 0;
        for (ItemRow item : items) {
            sum += percentFor(item.status());
        }
        return Math.round((float) sum / items.size());
    }

    /** Maps a status string to its completion percentage, unknown -> 0. */
    public static int percentFor(String status) {
        if (status == null) {
            return 0;
        }
        try {
            return TaskStatus.valueOf(status).getCompletionPercentage();
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }
}
