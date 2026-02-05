package dk.dtu.collumns;

import javafx.scene.control.ListCell;

/**
 * Context passed to a column when creating a per-row cell.
 */
public record ColumnCellContext<T>(
        ListCell<T> cell,
        Runnable refresh
) {
}
