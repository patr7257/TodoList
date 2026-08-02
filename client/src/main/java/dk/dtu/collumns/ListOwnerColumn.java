package dk.dtu.collumns;

import dk.dtu.methods.Helpers;
import dk.dtu.methods.Lists;
import dk.dtu.methods.Users;
import dk.dtu.net.ApiModels.UserRef;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;

import java.util.Comparator;
import java.util.Objects;

/**
 * The lists table's "Owner" column. Binds a {@link ComboBox} to the real users
 * (id + name, via {@link Users#loadUserRefsIntoComboBox}) rather than to
 * display-name strings: selection is looked up and written by
 * {@link UserRef#id()} against {@link Helpers.ListEntry#ownerId}, never by the
 * displayed name, so a rename or a duplicate name can never misattribute
 * ownership. The " *" main-user star stays presentation-only (added by
 * {@link Users}'s cell rendering) and never round-trips through a write.
 */
public class ListOwnerColumn implements Column<Helpers.ListEntry> {

    private static final double CELL_HEIGHT = 35;

    @Override
    public String id() {
        return "owner";
    }

    @Override
    public String title() {
        return "Owner";
    }

    @Override
    public double prefWidth() {
        return 180;
    }

    @Override
    public Comparator<Helpers.ListEntry> comparator() {
        return Comparator.comparing(e -> e.owner != null ? e.owner : "", String.CASE_INSENSITIVE_ORDER);
    }

    @Override
    public Node createHeader(ColumnHeaderContext<Helpers.ListEntry> ctx) {
        return ColumnUtils.createSortableHeaderLabel(title(), prefWidth(), () -> ctx.onSortRequested().accept(this));
    }

    @Override
    public ColumnCell<Helpers.ListEntry> createCell(ColumnCellContext<Helpers.ListEntry> ctx) {
        ComboBox<UserRef> ownerCombo = new ComboBox<>();
        ownerCombo.setPrefWidth(prefWidth() - 10);
        ownerCombo.setMinWidth(prefWidth() - 10);
        ownerCombo.setMaxWidth(prefWidth() - 10);
        ownerCombo.setPromptText("Owner");
        ownerCombo.getStyleClass().add("task-col-owner");

        // loadUserRefsIntoComboBox wires the cell/button-cell renderers (name + star).
        Users.loadUserRefsIntoComboBox(ownerCombo, true);

        // Ensure the ComboBox's internal cells match the control height (prevents vertical text clipping),
        // once the button cell exists (loadUserRefsIntoComboBox sets it asynchronously off this thread).
        Platform.runLater(() -> {
            if (ownerCombo.getButtonCell() != null) {
                ownerCombo.getButtonCell().setMinHeight(CELL_HEIGHT);
                ownerCombo.getButtonCell().setPrefHeight(CELL_HEIGHT);
                ownerCombo.getButtonCell().setMaxHeight(CELL_HEIGHT);
                ownerCombo.getButtonCell().setStyle("-fx-padding: 0 8 0 8;");
            }
        });

        ownerCombo.setOnAction(evt -> {
            Helpers.ListEntry item = ctx.currentItem().get();
            if (item == null) return;

            UserRef selected = ownerCombo.getValue();
            if (selected == null) return;

            String newOwnerId = selected.id(); // null means the "no owner" sentinel
            if (Objects.equals(newOwnerId, item.ownerId)) return; // unchanged

            ownerCombo.setDisable(true);
            new Thread(() -> {
                try {
                    Lists.setListOwnerId(item.id, newOwnerId);
                    Platform.runLater(() -> {
                        ownerCombo.setDisable(false);
                        // Refresh removed to prevent row shuffling during editing
                        // if (ctx.refresh() != null) {
                        //     ctx.refresh().run();
                        // }
                    });
                } catch (Exception ex) {
                    ex.printStackTrace();
                    Platform.runLater(() -> ownerCombo.setDisable(false));
                }
            }, "list-owner-set").start();
        });

        return new ColumnCell<>() {
            @Override
            public Node node() {
                return ownerCombo;
            }

            @Override
            public void update(Helpers.ListEntry item) {
                if (item == null) {
                    ownerCombo.setValue(null);
                    return;
                }
                if (item.ownerId == null) {
                    ownerCombo.setValue(Users.NO_OWNER);
                    return;
                }
                for (UserRef candidate : ownerCombo.getItems()) {
                    if (candidate != null && item.ownerId.equals(candidate.id())) {
                        ownerCombo.setValue(candidate);
                        return;
                    }
                }
                // The owning user isn't in the (not-yet-loaded, or since-deleted)
                // combo items: show a synthetic entry from the row's own display
                // text rather than falling back to "no owner" (which would read
                // as clearing an owner that is, in fact, still set server-side).
                ownerCombo.setValue(new UserRef(item.ownerId, item.owner != null ? item.owner : ""));
            }
        };
    }
}
