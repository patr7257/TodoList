package dk.dtu.collumns;

import java.util.function.Supplier;

/**
 * Context passed to a column when creating a per-row cell.
 *
 * {@code currentItem} returns the row item currently bound to the cell at call
 * time (cells are recycled across rows, so read it inside event handlers rather
 * than capturing a value). {@code refresh} reloads the backing data.
 *
 * This is control-agnostic (a Supplier instead of a concrete ListCell/TableCell)
 * so the same column classes drive either a ListView row or a TableView cell.
 */
public record ColumnCellContext<T>(
        Supplier<T> currentItem,
        Runnable refresh
) {
}
