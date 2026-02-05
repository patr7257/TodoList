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
        return 145;
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
        ListCell<Helpers.ListEntry> cell = ctx.cell();
        ComboBox<String> ownerCombo = new ComboBox<>();
        ownerCombo.setPrefWidth(prefWidth());
        ownerCombo.setMinWidth(prefWidth());
        ownerCombo.setMaxWidth(prefWidth());
        ownerCombo.setPromptText("Owner");

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

        Users.loadUsersIntoComboBox(ownerCombo, Config.getUsersUri(), true);

        ownerCombo.setOnAction(evt -> {
            Helpers.ListEntry item = cell.getItem();
            if (item == null) return;

            String newOwner = ownerCombo.getValue();
            if (newOwner == null) return;

            boolean wantsAll = ALL.equals(newOwner);
            boolean currentlyAll = (item.owner == null || item.owner.isBlank());
            if (wantsAll && currentlyAll) return;
            if (!wantsAll && newOwner.isBlank()) return;
            if (!wantsAll && newOwner.equals(item.owner)) return;

            ownerCombo.setDisable(true);
            new Thread(() -> {
                try {
                    if (wantsAll) {
                        Lists.clearListOwner(Config.getRequestsUri(), Config.getResponsesUri(), item.id);
                    } else {
                        Lists.setListOwner(Config.getRequestsUri(), Config.getResponsesUri(), item.id, newOwner);
                    }
                    Platform.runLater(() -> {
                        ownerCombo.setDisable(false);
                        if (ctx.refresh() != null) {
                            ctx.refresh().run();
                        }
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
                    ownerCombo.setValue(item.owner);
                } else {
                    ownerCombo.setValue(ALL);
                }
            }
        };
    }
}
