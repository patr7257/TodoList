package dk.dtu.collumns;

import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Simple shared sort state for a ListView that uses Column comparators.
 */
public class SortState<T> {
    private Column<T> activeColumn;
    private boolean ascending = true;

    public void requestSort(Column<T> column, ListView<T> listView, List<Label> headers) {
        if (column == null) return;
        Comparator<T> cmp = column.comparator();
        if (cmp == null) return;

        if (activeColumn == column) {
            ascending = !ascending;
        } else {
            activeColumn = column;
            ascending = true;
        }

        FXCollections.sort(listView.getItems(), ascending ? cmp : cmp.reversed());

        if (headers != null && !headers.isEmpty()) {
            // Active header is the one belonging to this column
            Label active = null;
            for (Label header : headers) {
                if (header.getUserData() == column) {
                    active = header;
                    break;
                }
            }
            if (active != null) {
                ColumnUtils.setActiveHeader(active, headers);
            }
        }
    }

    public static <T> List<Label> extractHeaderLabels(List<Node> headerNodes) {
        List<Label> labels = new ArrayList<>();
        for (Node node : headerNodes) {
            if (node instanceof Label l) {
                labels.add(l);
            }
        }
        return labels;
    }
}
