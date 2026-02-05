package dk.dtu.collumns;

import javafx.scene.Node;

import java.util.Comparator;

/**
 * UI column definition for a ListView row model.
 */
public interface Column<T> {
    String id();

    String title();

    double prefWidth();

    /**
     * Comparator for sorting by this column; may be null for non-sortable columns.
     */
    default Comparator<T> comparator() {
        return null;
    }

    Node createHeader(ColumnHeaderContext<T> ctx);

    ColumnCell<T> createCell(ColumnCellContext<T> ctx);
}
