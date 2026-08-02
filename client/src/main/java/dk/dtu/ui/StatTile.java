package dk.dtu.ui;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * One dashboard stat card: an icon, a big number (or a dash on unavailable
 * data), and a caption. Reuses the existing card recipe ({@code .login-box} /
 * {@code .config-panel}) via the {@code stat-tile} style class so it reads as
 * the same family as the rest of the app. Every value setter is defensive: a
 * null value renders "-" rather than throwing, per the dashboard's fail-safe
 * requirement (this is the only screen every login lands on).
 */
public class StatTile extends VBox {

    private final Label valueLabel = new Label("-");
    private final Label captionLabel;
    private int displayedValue;
    private boolean hasValue;

    public StatTile(String caption) {
        captionLabel = new Label(caption == null ? "" : caption);
        captionLabel.getStyleClass().add("stat-tile-caption");
        valueLabel.getStyleClass().add("stat-tile-value");

        setSpacing(6);
        setPadding(new Insets(18));
        setAlignment(Pos.CENTER_LEFT);
        setPrefWidth(190);
        setMinWidth(160);
        getStyleClass().addAll("login-box", "stat-tile");

        getChildren().addAll(valueLabel, captionLabel);
        VBox.setVgrow(valueLabel, Priority.NEVER);

        // Hover lift: a small upward nudge, animated rather than an instant CSS
        // jump, so the tile grid feels alive without any new dependency.
        hoverProperty().addListener((obs, was, isNow) -> {
            javafx.animation.TranslateTransition tt =
                    new javafx.animation.TranslateTransition(Duration.millis(120), this);
            tt.setToY(isNow ? -4 : 0);
            tt.play();
        });
    }

    /** Sets an icon at the top of the tile (call once, before layout). */
    public void setIcon(javafx.scene.Node icon) {
        if (icon == null) {
            return;
        }
        HBox row = new HBox(icon);
        row.getStyleClass().add("stat-tile-icon-row");
        getChildren().add(0, row);
    }

    /** Marks the value as unavailable: renders a dash instead of a number. */
    public void setUnavailable() {
        hasValue = false;
        valueLabel.setText("-");
    }

    /** Sets the value instantly (no animation), clamped defensively; null-safe. */
    public void setValue(Integer value) {
        if (value == null) {
            setUnavailable();
            return;
        }
        hasValue = true;
        displayedValue = value;
        valueLabel.setText(String.valueOf(value));
    }

    /** Sets the value with a suffix (e.g. a percent sign), null-safe. */
    public void setValue(Integer value, String suffix) {
        if (value == null) {
            setUnavailable();
            return;
        }
        hasValue = true;
        displayedValue = value;
        valueLabel.setText(value + (suffix == null ? "" : suffix));
    }

    /** Animates a count-up from 0 (or the current value) to {@code target}; null-safe, never throws. */
    public void animateToValue(Integer target) {
        if (target == null) {
            setUnavailable();
            return;
        }
        try {
            int start = hasValue ? displayedValue : 0;
            int end = target;
            hasValue = true;
            Timeline tl = new Timeline();
            int steps = 20;
            for (int i = 0; i <= steps; i++) {
                int value = Math.round(start + (end - start) * (i / (float) steps));
                tl.getKeyFrames().add(new KeyFrame(Duration.millis(i * 14.0),
                        e -> valueLabel.setText(String.valueOf(value))));
            }
            tl.setOnFinished(e -> displayedValue = end);
            tl.play();
        } catch (Exception e) {
            // Animation is cosmetic only; fall back to an instant, correct value.
            setValue(target);
        }
    }

    /** Staggered fade + translate entrance; call once per tile with an increasing delay. */
    public void playEntrance(int index) {
        try {
            setOpacity(0);
            setTranslateY(12);
            javafx.animation.FadeTransition fade = new javafx.animation.FadeTransition(Duration.millis(260), this);
            fade.setFromValue(0);
            fade.setToValue(1);
            fade.setDelay(Duration.millis(index * 45.0));

            javafx.animation.TranslateTransition move = new javafx.animation.TranslateTransition(Duration.millis(260), this);
            move.setFromY(12);
            move.setToY(0);
            move.setDelay(Duration.millis(index * 45.0));

            fade.play();
            move.play();
        } catch (Exception ignored) {
            // Entrance animation is cosmetic; never block the tile from showing.
            setOpacity(1);
            setTranslateY(0);
        }
    }

    /** Makes the whole tile clickable through to another view (e.g. the lists view). */
    public void setOnActivated(Runnable action) {
        if (action == null) {
            return;
        }
        setOnMouseClicked(e -> action.run());
        setFocusTraversable(true);
        setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER || e.getCode() == javafx.scene.input.KeyCode.SPACE) {
                action.run();
            }
        });
        getStyleClass().add("stat-tile-clickable");
    }
}
