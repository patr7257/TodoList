package dk.dtu.ui;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.util.Duration;

/**
 * A compact circular completion indicator: two overlaid {@link Arc}s (a
 * full-circle track and a completion sweep) plus a centered percent label.
 * Colored via the same completion-band tokens the lists table's progress bars
 * already use ({@code -band-high/mid/low/none}), so it reads as the same
 * family. Pure JavaFX shapes + CSS, no chart library, no new dependency.
 */
public class ProgressRing extends StackPane {

    private static final double RADIUS = 34;
    private static final double STROKE_WIDTH = 8;

    private final Arc fill;
    private final Label percentLabel;
    private int currentPercent = 0;

    public ProgressRing() {
        Arc track = new Arc(0, 0, RADIUS, RADIUS, 90, 360);
        track.setType(ArcType.OPEN);
        track.setFill(null);
        track.getStyleClass().add("progress-ring-track");
        track.setStrokeWidth(STROKE_WIDTH);
        track.setStrokeLineCap(StrokeLineCap.ROUND);

        fill = new Arc(0, 0, RADIUS, RADIUS, 90, 0);
        fill.setType(ArcType.OPEN);
        fill.setFill(null);
        fill.getStyleClass().add("progress-ring-fill");
        fill.setStrokeWidth(STROKE_WIDTH);
        fill.setStrokeLineCap(StrokeLineCap.ROUND);

        percentLabel = new Label("0%");
        percentLabel.getStyleClass().add("progress-ring-label");

        double size = (RADIUS + STROKE_WIDTH) * 2;
        setPrefSize(size, size);
        setMinSize(size, size);
        setAlignment(Pos.CENTER);
        getChildren().addAll(track, fill, percentLabel);
        getStyleClass().add("progress-ring");
        updateBand(0);
    }

    /** Sets the percent instantly (no animation); clamps to [0, 100]; null-safe, never throws. */
    public void setPercent(Integer percent) {
        try {
            int p = clamp(percent);
            currentPercent = p;
            fill.setLength(-3.6 * p);
            percentLabel.setText(p + "%");
            updateBand(p);
        } catch (Exception e) {
            percentLabel.setText("-");
        }
    }

    /** Animates from the current percent to the new one (a gentle count-up feel). */
    public void animateToPercent(Integer percent) {
        try {
            int target = clamp(percent);
            int start = currentPercent;
            Timeline tl = new Timeline();
            int steps = 24;
            for (int i = 0; i <= steps; i++) {
                int value = Math.round(start + (target - start) * (i / (float) steps));
                tl.getKeyFrames().add(new KeyFrame(Duration.millis(i * 12.0), e -> setPercent(value)));
            }
            tl.play();
        } catch (Exception e) {
            setPercent(percent);
        }
    }

    private void updateBand(int percent) {
        getStyleClass().removeAll("completion-high", "completion-mid", "completion-low", "completion-none");
        String band = percent >= 80 ? "completion-high"
                : percent >= 50 ? "completion-mid"
                : percent >= 30 ? "completion-low"
                : "completion-none";
        getStyleClass().add(band);
    }

    private static int clamp(Integer percent) {
        if (percent == null) {
            return 0;
        }
        return Math.max(0, Math.min(100, percent));
    }
}
