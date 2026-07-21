package dk.dtu.ui;

import dk.dtu.collumns.Column;
import dk.dtu.collumns.ColumnCell;
import dk.dtu.collumns.ColumnCellContext;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.paint.Color;

import javafx.animation.PauseTransition;
import javafx.collections.ListChangeListener;
import javafx.util.Duration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Builds a real JavaFX {@link TableView} from the existing {@link Column} list,
 * so both the lists view and the tasks view get native, resizable, auto-fitting
 * columns (with vertical dividers, a single scrollbar and reliable centering)
 * while reusing every existing column class unchanged.
 *
 * The column classes keep building their own always-visible cell controls; this
 * adapter wraps each {@code Column.createCell(...)} inside a TableCell and each
 * row's reorder/tint/open/context behavior inside a TableRow.
 */
public final class Tables {

    private Tables() {}

    /** Optional per-table behaviors, all nullable. */
    public static final class Config<T> {
        private Function<T, String> idOf;
        private Consumer<List<T>> persistOrder;
        private Function<T, String> tintStyle;
        private BooleanSupplier tintEnabled;
        private Consumer<T> onOpen;
        private Function<T, ContextMenu> contextMenu;
        private Runnable refresh;

        /** id extractor used to match a dragged row (required to enable drag-reorder). */
        public Config<T> idOf(Function<T, String> f) { this.idOf = f; return this; }
        /** called with the full re-ordered item list after a successful drag-drop. */
        public Config<T> persistOrder(Consumer<List<T>> c) { this.persistOrder = c; return this; }
        /** returns an inline -fx style string used to tint a row, or "" for none. */
        public Config<T> tintStyle(Function<T, String> f) { this.tintStyle = f; return this; }
        /** gate deciding whether tinting is active right now. */
        public Config<T> tintEnabled(BooleanSupplier b) { this.tintEnabled = b; return this; }
        /** primary double-click action on a non-empty row. */
        public Config<T> onOpen(Consumer<T> c) { this.onOpen = c; return this; }
        /** per-row context menu factory. */
        public Config<T> contextMenu(Function<T, ContextMenu> f) { this.contextMenu = f; return this; }
        /** refresh passed through to cells (e.g. delete triggers a reload). */
        public Config<T> refresh(Runnable r) { this.refresh = r; return this; }
    }

    public static <T> Config<T> config() { return new Config<>(); }

    public static <T> TableView<T> build(List<Column<T>> columns, ObservableList<T> items, Config<T> cfg) {
        TableView<T> table = new TableView<>(items);
        table.getStyleClass().add("app-table");
        // Columns keep their own width and stay drag-resizable; a horizontal
        // scrollbar appears when the columns need more room than the viewport.
        // (The bottom "Auto-fit columns" button sizes each column to its content.)
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.getSelectionModel().setCellSelectionEnabled(false);

        for (Column<T> column : columns) {
            table.getColumns().add(buildColumn(column, cfg));
        }

        table.setRowFactory(tv -> buildRow(table, items, cfg));
        return table;
    }

    private static <T> TableColumn<T, T> buildColumn(Column<T> column, Config<T> cfg) {
        TableColumn<T, T> tc = new TableColumn<>(column.title());
        // The column id travels with the TableColumn so saved view state (widths,
        // sort, order) can be matched back to the right column across rebuilds.
        tc.setUserData(column.id());
        tc.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue()));

        boolean fixed = "reorder".equals(column.id()) || "delete".equals(column.id());
        tc.setPrefWidth(column.prefWidth());
        if (fixed) {
            tc.setMinWidth(column.prefWidth());
            tc.setMaxWidth(column.prefWidth());
            tc.setResizable(false);
            tc.setSortable(false);
        } else {
            tc.setMinWidth(60);
            tc.setResizable(true);
            if (column.comparator() != null) {
                tc.setComparator(column.comparator());
                tc.setSortable(true);
            } else {
                tc.setSortable(false);
            }
        }

        tc.setCellFactory(c -> new TableCell<>() {
            private ColumnCell<T> cell;
            {
                setAlignment(Pos.CENTER);
                getStyleClass().add("app-table-cell");
            }

            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                if (cell == null) {
                    cell = column.createCell(new ColumnCellContext<>(this::getItem, cfg.refresh));
                }
                cell.update(item);
                setGraphic(cell.node());
            }
        });
        return tc;
    }

    private static <T> TableRow<T> buildRow(TableView<T> table, ObservableList<T> items, Config<T> cfg) {
        TableRow<T> row = new TableRow<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                // Per-status row tint (tasks). Inline style mirrors the old ListCell behavior.
                if (!empty && item != null && cfg.tintStyle != null
                        && (cfg.tintEnabled == null || cfg.tintEnabled.getAsBoolean())) {
                    setStyle(cfg.tintStyle.apply(item));
                } else {
                    setStyle("");
                }
                if (!empty && item != null && cfg.contextMenu != null) {
                    setContextMenu(cfg.contextMenu.apply(item));
                } else {
                    setContextMenu(null);
                }
            }
        };

        if (cfg.onOpen != null) {
            row.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2 && !row.isEmpty()) {
                    cfg.onOpen.accept(row.getItem());
                }
            });
        }

        if (cfg.persistOrder != null && cfg.idOf != null) {
            wireReorder(table, items, cfg, row);
        }
        return row;
    }

    // Drag-reorder. Drag only starts when the gesture begins on a reorder handle
    // (a node carrying the "reorder-handle" style class), so text fields / combos
    // in other cells are unaffected. Highlight uses the "drag-over" style class.
    private static <T> void wireReorder(TableView<T> table, ObservableList<T> items, Config<T> cfg, TableRow<T> row) {
        row.setOnDragDetected(e -> {
            if (row.isEmpty() || row.getItem() == null) return;
            if (!startedOnReorderHandle(e.getPickResult().getIntersectedNode())) return;
            Dragboard db = row.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(cfg.idOf.apply(row.getItem()));
            db.setContent(content);
            SnapshotParameters sp = new SnapshotParameters();
            sp.setFill(Color.TRANSPARENT);
            WritableImage snap = row.snapshot(sp, null);
            db.setDragView(snap);
            row.setOpacity(0.4);
            e.consume();
        });

        row.setOnDragOver(e -> {
            if (e.getDragboard().hasString()) {
                e.acceptTransferModes(TransferMode.MOVE);
                e.consume();
            }
        });

        row.setOnDragEntered(e -> {
            if (e.getGestureSource() != row && e.getDragboard().hasString() && !row.isEmpty()) {
                if (!row.getStyleClass().contains("drag-over")) {
                    row.getStyleClass().add("drag-over");
                }
            }
        });

        row.setOnDragExited(e -> row.getStyleClass().remove("drag-over"));

        row.setOnDragDone(e -> {
            row.setOpacity(1.0);
            row.getStyleClass().remove("drag-over");
        });

        row.setOnDragDropped(e -> {
            Dragboard db = e.getDragboard();
            if (!db.hasString()) {
                e.setDropCompleted(false);
                return;
            }
            String draggedId = db.getString();
            int draggedIdx = indexOfId(items, draggedId, cfg.idOf);
            if (draggedIdx < 0) {
                e.setDropCompleted(false);
                return;
            }
            int targetIdx = row.isEmpty() ? items.size() : Math.max(0, row.getIndex());

            // A manual reorder invalidates any active column sort, so clear it and
            // reorder the underlying list directly.
            table.getSortOrder().clear();

            T dragged = items.remove(draggedIdx);
            if (targetIdx > items.size()) targetIdx = items.size();
            if (targetIdx > draggedIdx) targetIdx--;
            items.add(targetIdx, dragged);
            table.getSelectionModel().select(dragged);

            e.setDropCompleted(true);
            row.getStyleClass().remove("drag-over");
            cfg.persistOrder.accept(List.copyOf(items));
            e.consume();
        });
    }

    /**
     * Resize every resizable column to fit its content's optimal width (the same
     * behavior as double-clicking a column divider). Measures the real rendered
     * cells across all rows, so e.g. a year of "123456789" gets more room than
     * "2026". Uses the skin's own measurement via reflection; if that is
     * unavailable it is a silent no-op (columns just keep their current width).
     */
    public static void autoFitColumns(TableView<?> table) {
        if (table == null || table.getSkin() == null) return;
        try {
            // JavaFX 21 moved the content-measuring resize to the per-column header
            // (TableColumnHeader.resizeColumnToFitContent(int)). Navigate:
            // skin -> TableHeaderRow -> root NestedTableColumnHeader -> column headers.
            Object skin = table.getSkin();
            Object headerRow = invokeNoArg(skin, "getTableHeaderRow");
            if (headerRow == null) return;
            Object rootHeader = invokeNoArg(headerRow, "getRootHeader");
            if (rootHeader == null) return;
            resizeHeaders(rootHeader);
        } catch (Throwable t) {
            // Reflective access blocked or API changed: silent no-op.
        }
    }

    private static void resizeHeaders(Object header) throws Exception {
        Object childrenObj = invokeNoArg(header, "getColumnHeaders");
        if (childrenObj instanceof List<?> children && !children.isEmpty()) {
            for (Object child : children) {
                resizeHeaders(child); // nested header -> recurse
            }
            return;
        }
        // Leaf column header: measure all rows (-1) and set the width to fit.
        java.lang.reflect.Method m = findMethod(header.getClass(), "resizeColumnToFitContent", 1);
        if (m != null) {
            m.setAccessible(true);
            m.invoke(header, -1);
        }
    }

    private static Object invokeNoArg(Object target, String name) throws Exception {
        java.lang.reflect.Method m = findMethod(target.getClass(), name, 0);
        if (m == null) return null;
        m.setAccessible(true);
        return m.invoke(target);
    }

    private static java.lang.reflect.Method findMethod(Class<?> c, String name, int paramCount) {
        while (c != null) {
            for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == paramCount) {
                    return m;
                }
            }
            c = c.getSuperclass();
        }
        return null;
    }

    private static boolean startedOnReorderHandle(Node node) {
        while (node != null) {
            if (node.getStyleClass().contains("reorder-handle")) return true;
            node = node.getParent();
        }
        return false;
    }

    private static <T> int indexOfId(List<T> items, String id, Function<T, String> idOf) {
        for (int i = 0; i < items.size(); i++) {
            if (id.equals(idOf.apply(items.get(i)))) return i;
        }
        return -1;
    }

    // -------------------------------------------------------------------------
    // View-state helpers (persisted column widths, sort, and order)
    // -------------------------------------------------------------------------

    /** The column id carried in a TableColumn's user data, or null. */
    public static String columnId(TableColumn<?, ?> tc) {
        return (tc != null && tc.getUserData() instanceof String s) ? s : null;
    }

    /** Current column order, top-level columns only, by id (nulls skipped). */
    public static List<String> columnOrder(TableView<?> table) {
        List<String> ids = new java.util.ArrayList<>();
        if (table != null) {
            for (TableColumn<?, ?> tc : table.getColumns()) {
                String id = columnId(tc);
                if (id != null) ids.add(id);
            }
        }
        return ids;
    }

    /** Current widths of resizable columns, keyed by id. */
    public static Map<String, Double> columnWidths(TableView<?> table) {
        Map<String, Double> widths = new LinkedHashMap<>();
        if (table != null) {
            for (TableColumn<?, ?> tc : table.getColumns()) {
                String id = columnId(tc);
                if (id != null && tc.isResizable() && tc.getWidth() > 0) {
                    widths.put(id, tc.getWidth());
                }
            }
        }
        return widths;
    }

    /** The id of the primary sort column, or null when nothing is sorted. */
    public static String sortColumnId(TableView<?> table) {
        if (table == null || table.getSortOrder().isEmpty()) return null;
        return columnId(table.getSortOrder().get(0));
    }

    /** Whether the primary sort column is ascending (true when unsorted). */
    public static boolean sortAscending(TableView<?> table) {
        if (table == null || table.getSortOrder().isEmpty()) return true;
        return table.getSortOrder().get(0).getSortType() == TableColumn.SortType.ASCENDING;
    }

    /**
     * Restore saved widths + sort onto an already-built table. Unknown ids are
     * ignored. Call BEFORE attaching auto-save listeners so restoring does not
     * echo back as a save.
     */
    public static <S> void applyState(TableView<S> table, Map<String, Double> widths,
                                      String sortColumnId, boolean ascending) {
        if (table == null) return;
        try {
            if (widths != null && !widths.isEmpty()) {
                for (TableColumn<S, ?> tc : table.getColumns()) {
                    String id = columnId(tc);
                    Double w = (id == null) ? null : widths.get(id);
                    if (w != null && w > 0 && tc.isResizable()) {
                        tc.setPrefWidth(w);
                    }
                }
            }
            table.getSortOrder().clear();
            if (sortColumnId != null) {
                for (TableColumn<S, ?> tc : table.getColumns()) {
                    if (sortColumnId.equals(columnId(tc)) && tc.isSortable()) {
                        tc.setSortType(ascending ? TableColumn.SortType.ASCENDING
                                : TableColumn.SortType.DESCENDING);
                        table.getSortOrder().setAll(tc);
                        break;
                    }
                }
            }
        } catch (Throwable ignored) {
            // never let restore break the view
        }
    }

    /**
     * Persist changes automatically: column reorder (header drag) and sort
     * changes fire {@code onChange} immediately; width changes are debounced so a
     * drag-resize writes once it settles. Auto-fit, which sets widths, therefore
     * also persists. Attach AFTER {@link #applyState}.
     */
    public static void bindAutoSave(TableView<?> table, Runnable onChange) {
        if (table == null || onChange == null) return;

        PauseTransition widthDebounce = new PauseTransition(Duration.millis(400));
        widthDebounce.setOnFinished(e -> onChange.run());

        // A ListChangeListener<TableColumn<?,?>> is a valid ? super listener for
        // the column / sort-order lists, so no unchecked cast is needed.
        ListChangeListener<TableColumn<?, ?>> colListener = c -> onChange.run();
        table.getColumns().addListener(colListener);
        table.getSortOrder().addListener(colListener);

        for (TableColumn<?, ?> tc : table.getColumns()) {
            tc.widthProperty().addListener((obs, oldV, newV) -> widthDebounce.playFromStart());
            tc.sortTypeProperty().addListener((obs, oldV, newV) -> onChange.run());
        }
    }
}
