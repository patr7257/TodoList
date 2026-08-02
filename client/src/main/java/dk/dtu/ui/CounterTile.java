package dk.dtu.ui;

import atlantafx.base.theme.Styles;
import dk.dtu.net.CounterDto;
import javafx.animation.ScaleTransition;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * One "fun counter" tile (Total Flights, Total Ships, Tour de Brede walks,
 * ...): icon, label, a big value with +/- bump buttons, and hover-revealed
 * edit/delete actions. Draggable for reorder, reusing the same
 * {@code ClipboardContent}-index drag pattern already used by
 * {@code C_MainMenu.showListColumnDialog} (adapted here to drag-by-id between
 * FlowPane tiles rather than ListView rows), and the shared {@code .drag-over}
 * style class so it reads as the same interaction family.
 *
 * <p>Hover/edit/delete actions are revealed via an OPACITY binding only (never
 * {@code setVisible(false)}), so the buttons stay keyboard-focusable even
 * before they are visually revealed.
 */
public class CounterTile extends VBox {

    /** Callbacks the dashboard wires up; this tile never talks to the network directly. */
    public interface Actions {
        /** A user-initiated relative bump ({@code +1}, {@code -1}, ...). */
        void bump(String counterId, int delta);
        /** Open the edit dialog for this counter. */
        void edit(CounterDto counter);
        /** Delete this counter (the caller is responsible for any confirmation). */
        void delete(CounterDto counter);
        /** The user dropped {@code draggedId}'s tile onto this counter's tile. */
        void reorder(String draggedId, String ontoCounterId);
    }

    private final Actions actions;
    private CounterDto counter;
    private int displayedValue;
    private boolean hasDisplayedValue;

    private final Label valueLabel = new Label("-");
    private final Label titleLabel = new Label();
    private final Label descriptionLabel = new Label();

    public CounterTile(CounterDto counter, Actions actions) {
        this.counter = counter;
        this.actions = actions;

        getStyleClass().addAll("login-box", "counter-tile");
        setSpacing(8);
        setPadding(new Insets(16));
        setAlignment(Pos.TOP_LEFT);
        setPrefWidth(220);
        setMinWidth(200);

        javafx.scene.Node icon = Icons.safe(counter == null ? null : counter.icon(), 22);
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        if (icon != null) {
            header.getChildren().add(icon);
        }
        titleLabel.getStyleClass().add("counter-tile-title");
        header.getChildren().add(titleLabel);

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        header.getChildren().add(headerSpacer);

        HBox actionsRow = buildActionsRow();
        header.getChildren().add(actionsRow);

        valueLabel.getStyleClass().add("counter-tile-value");

        Button minus = bumpButton("fth-minus", -1);
        Button plus = bumpButton("fth-plus", 1);
        HBox bumpRow = new HBox(10, minus, valueLabel, plus);
        bumpRow.setAlignment(Pos.CENTER_LEFT);

        descriptionLabel.getStyleClass().add("counter-tile-description");
        descriptionLabel.setWrapText(true);

        getChildren().addAll(header, bumpRow, descriptionLabel);

        bindHoverReveal(actionsRow, minus, plus);
        setupDragAndDrop();
        applyCounter(counter, false);

        // Hover lift: the same animated nudge used by StatTile, so the fun
        // counters and stat tiles read as one consistent, alive tile family.
        hoverProperty().addListener((obs, was, isNow) -> {
            javafx.animation.TranslateTransition tt =
                    new javafx.animation.TranslateTransition(Duration.millis(120), this);
            tt.setToY(isNow ? -4 : 0);
            tt.play();
        });
    }

    /** The wrapped counter's id, or null if none. */
    public String getCounterId() {
        return counter == null ? null : counter.id();
    }

    /**
     * Refreshes label/description/icon/sort from a freshly fetched row, IN
     * PLACE (no node rebuild), so hover state, an in-flight drag, and keyboard
     * focus survive a poll tick. The displayed value is reconciled separately
     * via {@link #reconcileValue(int)} so an optimistic bump is not clobbered by
     * a racing poll response.
     */
    public void updateFrom(CounterDto updated) {
        this.counter = updated;
        applyCounter(updated, true);
    }

    /** Reconciles the displayed value to the server's, with a scale pop if it actually changed. */
    public void reconcileValue(int serverValue) {
        if (!hasDisplayedValue || serverValue != displayedValue) {
            setDisplayedValue(serverValue, true);
        }
    }

    /** Optimistic local bump: update instantly, then the caller reconciles later. */
    private void onBumpClicked(int delta) {
        int next = (hasDisplayedValue ? displayedValue : 0) + delta;
        setDisplayedValue(next, true);
        if (actions != null && counter != null) {
            actions.bump(counter.id(), delta);
        }
    }

    /** First-load count-up from 0; every subsequent change is a quick scale pop instead. */
    public void animateInitialValue(int value) {
        if (hasDisplayedValue) {
            reconcileValue(value);
            return;
        }
        hasDisplayedValue = true;
        displayedValue = value;
        try {
            javafx.animation.Timeline tl = new javafx.animation.Timeline();
            int steps = 20;
            for (int i = 0; i <= steps; i++) {
                int shown = Math.round(value * (i / (float) steps));
                tl.getKeyFrames().add(new javafx.animation.KeyFrame(
                        Duration.millis(i * 14.0), e -> valueLabel.setText(String.valueOf(shown))));
            }
            tl.play();
        } catch (Exception e) {
            valueLabel.setText(String.valueOf(value));
        }
    }

    private void setDisplayedValue(int value, boolean pulse) {
        hasDisplayedValue = true;
        displayedValue = value;
        valueLabel.setText(String.valueOf(value));
        if (pulse) {
            pulse();
        }
    }

    private void pulse() {
        try {
            ScaleTransition st = new ScaleTransition(Duration.millis(160), valueLabel);
            st.setFromX(1.0);
            st.setFromY(1.0);
            st.setToX(1.18);
            st.setToY(1.18);
            st.setAutoReverse(true);
            st.setCycleCount(2);
            st.play();
        } catch (Exception ignored) {
            // Purely cosmetic; never let it break the value update.
        }
    }

    private void applyCounter(CounterDto c, boolean keepValue) {
        titleLabel.setText((c == null || c.label() == null) ? "-" : c.label());
        String description = (c == null) ? null : c.description();
        descriptionLabel.setText((description == null || description.isBlank()) ? "" : description);
        descriptionLabel.setManaged(description != null && !description.isBlank());
        descriptionLabel.setVisible(description != null && !description.isBlank());
        if (!keepValue && c != null) {
            animateInitialValue(c.value());
        }
    }

    private Button bumpButton(String iconLiteral, int delta) {
        Button b = new Button();
        b.setGraphic(Icons.of(iconLiteral, Icons.ROW));
        b.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, "counter-tile-bump-button");
        b.setTooltip(new Tooltip(delta > 0 ? "+1" : "-1"));
        b.setOnAction(e -> onBumpClicked(delta));
        return b;
    }

    private HBox buildActionsRow() {
        Button edit = new Button();
        edit.setGraphic(Icons.of("fth-edit-2", Icons.ROW));
        edit.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, "counter-tile-action-button");
        edit.setTooltip(new Tooltip("Edit"));
        edit.setOnAction(e -> {
            if (actions != null && counter != null) {
                actions.edit(counter);
            }
        });

        Button delete = new Button();
        delete.setGraphic(Icons.delete());
        delete.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, "counter-tile-action-button");
        delete.setTooltip(new Tooltip("Delete"));
        delete.setOnAction(e -> {
            if (actions != null && counter != null) {
                actions.delete(counter);
            }
        });

        HBox row = new HBox(4, edit, delete);
        row.setAlignment(Pos.CENTER_RIGHT);
        row.getStyleClass().add("counter-tile-actions");
        return row;
    }

    /**
     * Opacity-only hover/focus reveal: bound (not toggled via setVisible), so
     * the action and bump buttons stay keyboard-focusable at all times, just
     * visually faded until hovered or focused.
     */
    private void bindHoverReveal(HBox actionsRow, Button... alwaysVisible) {
        actionsRow.opacityProperty().bind(Bindings.when(
                hoverProperty()
                        .or(actionsRow.focusWithinProperty())
        ).then(1.0).otherwise(0.0));
        for (Button b : alwaysVisible) {
            b.setOpacity(1.0);
        }
    }

    private void setupDragAndDrop() {
        setOnDragDetected(evt -> {
            if (counter == null) {
                return;
            }
            Dragboard db = startDragAndDrop(TransferMode.MOVE);
            ClipboardContent cc = new ClipboardContent();
            cc.putString(counter.id());
            db.setContent(cc);
            try {
                db.setDragView(snapshot(null, null));
            } catch (Exception ignored) {
                // Drag image is cosmetic only.
            }
            evt.consume();
        });

        setOnDragOver(evt -> {
            if (evt.getGestureSource() != this && evt.getDragboard().hasString()) {
                evt.acceptTransferModes(TransferMode.MOVE);
            }
            evt.consume();
        });

        setOnDragEntered(evt -> {
            if (evt.getGestureSource() != this && evt.getDragboard().hasString()) {
                if (!getStyleClass().contains("drag-over")) {
                    getStyleClass().add("drag-over");
                }
            }
        });

        setOnDragExited(evt -> getStyleClass().remove("drag-over"));

        setOnDragDropped(evt -> {
            Dragboard db = evt.getDragboard();
            boolean success = false;
            if (db.hasString() && counter != null) {
                String draggedId = db.getString();
                if (draggedId != null && !draggedId.equals(counter.id()) && actions != null) {
                    actions.reorder(draggedId, counter.id());
                    success = true;
                }
            }
            evt.setDropCompleted(success);
            evt.consume();
        });

        setOnDragDone(evt -> getStyleClass().remove("drag-over"));
    }
}
