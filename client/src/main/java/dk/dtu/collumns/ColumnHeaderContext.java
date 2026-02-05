package dk.dtu.collumns;

import javafx.scene.control.ListView;

import java.util.function.Consumer;

/**
 * Context passed to a column when creating its header node.
 */
public record ColumnHeaderContext<T>(
        ListView<T> listView,
        Consumer<Column<T>> onSortRequested
) {
}
