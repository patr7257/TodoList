package dk.dtu.collumns;

import dk.dtu.methods.Helpers;
import dk.dtu.methods.Lists;
import dk.dtu.methods.Users;
import dk.dtu.shared.Config;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;

import java.util.Comparator;

public class ListOwnerColumn implements Column<Helpers.ListEntry> {

    private static final String ALL = "All";
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
        ComboBox<String> ownerCombo = new ComboBox<>();
        ownerCombo.setPrefWidth(prefWidth() - 10);
        ownerCombo.setMinWidth(prefWidth() - 10);
        ownerCombo.setMaxWidth(prefWidth() - 10);
        ownerCombo.setPromptText("Owner");
        ownerCombo.getStyleClass().add("task-col-owner");

        ownerCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item);
                setAlignment(Pos.CENTER);
            }
        });
        ownerCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item);
                setAlignment(Pos.CENTER);
            }
        });

        // Ensure the ComboBox's internal cells match the control height (prevents vertical text clipping)
        ownerCombo.getButtonCell().setMinHeight(CELL_HEIGHT);
        ownerCombo.getButtonCell().setPrefHeight(CELL_HEIGHT);
        ownerCombo.getButtonCell().setMaxHeight(CELL_HEIGHT);
        ownerCombo.getButtonCell().setStyle("-fx-padding: 0 8 0 8;");

        Users.loadUsersIntoComboBox(ownerCombo, Config.getUsersUri(), true);

        ownerCombo.setOnAction(evt -> {
            Helpers.ListEntry item = ctx.currentItem().get();
            if (item == null) return;

            String newOwner = ownerCombo.getValue();
            if (newOwner == null) return;
            
            // Strip star from main users before using the value
            String cleanOwner = newOwner.replace(" *", "");

            boolean wantsAll = ALL.equals(cleanOwner);
            boolean currentlyAll = (item.owner == null || item.owner.isBlank());
            if (wantsAll && currentlyAll) return;
            if (!wantsAll && cleanOwner.isBlank()) return;
            if (!wantsAll && cleanOwner.equals(item.owner)) return;

            ownerCombo.setDisable(true);
            new Thread(() -> {
                try {
                    if (wantsAll) {
                        Lists.clearListOwner(Config.getRequestsUri(), Config.getResponsesUri(), item.id);
                    } else {
                        Lists.setListOwner(Config.getRequestsUri(), Config.getResponsesUri(), item.id, cleanOwner);
                    }
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
                if (item.owner != null && !item.owner.isBlank()) {
                    // Add star to main users for display
                    String displayOwner = item.owner;
                    if (dk.dtu.MainUserConfig.isMainUser(item.owner)) {
                        displayOwner = item.owner + " *";
                    }
                    ownerCombo.setValue(displayOwner);
                } else {
                    ownerCombo.setValue(ALL);
                }
            }
        };
    }
}
