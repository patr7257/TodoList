package dk.dtu.ui;

import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.util.Map;

/**
 * A single-row per-status mix strip: one colored segment per task status,
 * each segment's width BOUND to a fraction of the container's width (so it
 * always sums visually to the whole, and resizes with the window instead of
 * needing a manual relayout). Colored with the existing {@code -status-*}
 * tokens so it reads as the same family as the status pills elsewhere.
 */
public class StatusMixBar extends HBox {

    private static final String[] STATUSES = {"NOT_STARTED", "IN_PROGRESS", "DELAYED", "NEED_HELP", "DONE"};

    public StatusMixBar() {
        getStyleClass().add("status-mix-bar");
        setFillHeight(true);
    }

    /**
     * Rebuilds the segments from a status -> count map. Missing/negative counts
     * are treated as 0; a null or all-zero map renders a single neutral segment
     * spanning the whole width rather than an empty (and confusing) bar.
     */
    public void setMix(Map<String, Integer> mix) {
        getChildren().clear();
        Map<String, Integer> safe = (mix == null) ? Map.of() : mix;

        int total = 0;
        for (String s : STATUSES) {
            total += countOf(safe, s);
        }

        if (total <= 0) {
            Region empty = new Region();
            empty.getStyleClass().add("status-mix-empty");
            HBox.setHgrow(empty, Priority.ALWAYS);
            empty.prefWidthProperty().bind(widthProperty());
            getChildren().add(empty);
            return;
        }

        for (String s : STATUSES) {
            int count = countOf(safe, s);
            if (count <= 0) {
                continue;
            }
            double fraction = count / (double) total;
            Region segment = new Region();
            segment.getStyleClass().addAll("status-mix-segment", "status-mix-" + s);
            segment.setMinWidth(0);
            segment.prefWidthProperty().bind(widthProperty().multiply(fraction));
            Tooltip.install(segment, new Tooltip(prettyStatus(s) + ": " + count));
            getChildren().add(segment);
        }
    }

    private static int countOf(Map<String, Integer> mix, String status) {
        Integer v = mix.get(status);
        return (v == null) ? 0 : Math.max(0, v);
    }

    private static String prettyStatus(String status) {
        return switch (status) {
            case "NOT_STARTED" -> "Not started";
            case "IN_PROGRESS" -> "In progress";
            case "DELAYED" -> "Delayed";
            case "NEED_HELP" -> "Needs help";
            case "DONE" -> "Done";
            default -> status;
        };
    }
}
