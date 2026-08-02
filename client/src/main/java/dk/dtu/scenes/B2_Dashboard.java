package dk.dtu.scenes;

import atlantafx.base.theme.Styles;
import dk.dtu.CounterDialog;
import dk.dtu.DarkModeManager;
import dk.dtu.SceneNavigator;
import dk.dtu.methods.Counters;
import dk.dtu.methods.Dashboard;
import dk.dtu.methods.Dashboard.DashboardStats;
import dk.dtu.net.ApiModels.ListDto;
import dk.dtu.net.ApiModels.StateResponse;
import dk.dtu.net.ApiSession;
import dk.dtu.net.CounterDto;
import dk.dtu.ui.CounterTile;
import dk.dtu.ui.Icons;
import dk.dtu.ui.ProgressRing;
import dk.dtu.ui.StatTile;
import dk.dtu.ui.StatusMixBar;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The dashboard front page (issue #46): the first screen after login and
 * after a saved-token relaunch, before the lists view. Two halves:
 *
 * <ul>
 *   <li>6 live stats (+ an optional status mix) derived client-side from the
 *       same {@code GET /state} payload the lists/tasks views already use, via
 *       the pure {@link Dashboard#compute(List, String, Instant)}.</li>
 *   <li>the shared, manually maintained "fun counters", full add/edit/bump/
 *       reorder/delete, persisted server-side (so both account holders see the
 *       same values) via {@link Counters}.</li>
 * </ul>
 *
 * <p>Fail-safe: every stat tile update is individually guarded (one bad value
 * cannot blank the others or crash the screen), and this is the ONE screen
 * every login must land on, so it always offers a way out via "Open my lists"
 * and per-tile click-through.
 */
public class B2_Dashboard {

    private static final int RECENT_DAYS = Dashboard.DEFAULT_RECENT_DAYS;

    private final SceneNavigator navigator;

    private StatTile totalListsTile;
    private StatTile totalTasksTile;
    private StatTile overdueTile;
    private StatTile myOpenTile;
    private StatTile addedRecentlyTile;
    private ProgressRing completionRing;
    private Label completionCountLabel;
    private StatusMixBar statusMixBar;
    private boolean firstStatsLoad = true;

    private FlowPane counterGrid;
    private final Map<String, CounterTile> counterTilesById = new LinkedHashMap<>();
    private List<String> lastCounterOrder = List.of();
    private volatile boolean counterDialogOpen = false;
    private volatile long suppressCountersRefreshUntilMillis = 0;

    public B2_Dashboard(SceneNavigator navigator) {
        this.navigator = navigator;
    }

    public Scene createScene() {
        Label title = new Label("Dashboard");
        title.getStyleClass().add("dashboard-title");

        Label userLabel = new Label("Logged in as: " + navigator.getCurrentUser());
        userLabel.getStyleClass().add("dashboard-subtitle");

        VBox header = new VBox(6, title, userLabel);
        header.setAlignment(Pos.TOP_CENTER);

        Button openMyLists = new Button("Open my lists");
        openMyLists.setGraphic(Icons.of("fth-list", Icons.ROW));
        openMyLists.getStyleClass().addAll(Styles.ACCENT, Styles.LARGE, "dashboard-open-lists-button");
        openMyLists.setOnAction(e -> navigator.showMainMenu());

        FlowPane statGrid = buildStatGrid();

        Label statusMixLabel = new Label("Task status mix");
        statusMixLabel.getStyleClass().add("dashboard-section-label");
        statusMixBar = new StatusMixBar();
        statusMixBar.setPrefWidth(600);
        statusMixBar.setMinHeight(16);
        statusMixBar.setMaxHeight(16);
        VBox statusMixBox = new VBox(8, statusMixLabel, statusMixBar);
        statusMixBox.setMaxWidth(700);

        Label countersTitle = new Label("Fun counters");
        countersTitle.getStyleClass().add("dashboard-section-label");

        Button addCounter = new Button("+ Add counter");
        addCounter.getStyleClass().addAll(Styles.FLAT, "create-link");
        addCounter.setOnAction(e -> openAddCounterDialog());

        HBox countersHeader = new HBox(12, countersTitle, spacer(), addCounter);
        countersHeader.setAlignment(Pos.CENTER_LEFT);
        countersHeader.setMaxWidth(Double.MAX_VALUE);

        counterGrid = new FlowPane(14, 14);
        counterGrid.getStyleClass().add("dashboard-counter-grid");

        VBox content = new VBox(22, header, openMyLists, statGrid, statusMixBox, countersHeader, counterGrid);
        content.setPadding(new Insets(24));
        content.setAlignment(Pos.TOP_CENTER);
        content.getStyleClass().add("dashboard-root");

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("dashboard-scroll");

        load();

        return new Scene(scroll, 980, 680);
    }

    // -- layout -------------------------------------------------------------

    private FlowPane buildStatGrid() {
        totalListsTile = statTile("Total lists", "fth-list");
        totalTasksTile = statTile("Total tasks", "fth-check-square");
        overdueTile = statTile("Overdue tasks", "fth-alert-triangle");
        myOpenTile = statTile("My open tasks", "fth-user");
        addedRecentlyTile = statTile("Added last " + RECENT_DAYS + " days", "fth-clock");
        VBox completionTile = buildCompletionTile();

        FlowPane grid = new FlowPane(16, 16);
        grid.getStyleClass().add("dashboard-stat-grid");
        grid.getChildren().addAll(totalListsTile, totalTasksTile, completionTile,
                overdueTile, myOpenTile, addedRecentlyTile);

        int i = 0;
        for (var node : grid.getChildren()) {
            if (node instanceof StatTile st) {
                st.playEntrance(i);
            }
            i++;
        }
        return grid;
    }

    private StatTile statTile(String caption, String iconLiteral) {
        StatTile tile = new StatTile(caption);
        tile.setIcon(Icons.of(iconLiteral, Icons.SIDEBAR));
        tile.setOnActivated(() -> navigator.showMainMenu());
        return tile;
    }

    private VBox buildCompletionTile() {
        completionRing = new ProgressRing();
        completionCountLabel = new Label("-");
        completionCountLabel.getStyleClass().add("stat-tile-caption");
        Label caption = new Label("Done / completion");
        caption.getStyleClass().add("stat-tile-caption");

        VBox box = new VBox(8, completionRing, completionCountLabel, caption);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(18));
        box.setPrefWidth(190);
        box.setMinWidth(160);
        box.getStyleClass().addAll("login-box", "stat-tile", "stat-tile-clickable");
        box.setFocusTraversable(true);
        box.setOnMouseClicked(e -> navigator.showMainMenu());
        box.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER || e.getCode() == javafx.scene.input.KeyCode.SPACE) {
                navigator.showMainMenu();
            }
        });
        return box;
    }

    private static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    // -- loading + refresh ----------------------------------------------------

    private void load() {
        loadStats();
        loadCounters();
    }

    /** Called by {@link SceneNavigator}'s poll tick (every 4s while focused). */
    public void autoRefresh() {
        loadStats();
        if (!counterDialogOpen) {
            loadCounters();
        }
    }

    private void loadStats() {
        new Thread(() -> {
            try {
                StateResponse state = ApiSession.get().fetchState();
                List<ListDto> lists = (state != null) ? state.lists() : List.of();
                String uid = (state != null && state.user() != null) ? state.user().id() : null;
                DashboardStats stats = Dashboard.compute(lists, uid, Instant.now(), RECENT_DAYS);
                Platform.runLater(() -> applyStats(stats));
            } catch (Exception ex) {
                ex.printStackTrace();
                ApiSession.get().reportError(ex);
                Platform.runLater(this::markStatsUnavailable);
            }
        }, "dashboard-load-stats").start();
    }

    /** Every tile update is individually guarded: one bad value cannot blank the others. */
    private void applyStats(DashboardStats stats) {
        boolean first = firstStatsLoad;
        firstStatsLoad = false;

        guarded(() -> updateTile(totalListsTile, stats.totalLists(), first));
        guarded(() -> updateTile(totalTasksTile, stats.totalTasks(), first));
        guarded(() -> updateTile(overdueTile, stats.overdueTasks(), first));
        guarded(() -> updateTile(myOpenTile, stats.myOpenTasks(), first));
        guarded(() -> updateTile(addedRecentlyTile, stats.addedRecently(), first));
        guarded(() -> {
            if (first) {
                completionRing.animateToPercent(stats.completionPercent());
            } else {
                completionRing.setPercent(stats.completionPercent());
            }
        });
        guarded(() -> completionCountLabel.setText(stats.doneTasks() + " / " + stats.totalTasks() + " done"));
        guarded(() -> statusMixBar.setMix(stats.statusMix()));
    }

    private void markStatsUnavailable() {
        guarded(totalListsTile::setUnavailable);
        guarded(totalTasksTile::setUnavailable);
        guarded(overdueTile::setUnavailable);
        guarded(myOpenTile::setUnavailable);
        guarded(addedRecentlyTile::setUnavailable);
        guarded(() -> completionCountLabel.setText("-"));
    }

    private static void updateTile(StatTile tile, int value, boolean first) {
        if (first) {
            tile.animateToValue(value);
        } else {
            tile.setValue(value);
        }
    }

    private static void guarded(Runnable r) {
        try {
            r.run();
        } catch (Exception e) {
            System.err.println("[B2_Dashboard] a stat tile update failed (rendering a dash instead): " + e.getMessage());
        }
    }

    // -- counters -------------------------------------------------------------

    private void loadCounters() {
        Counters.loadCounters(this::applyCounters);
    }

    private void applyCounters(List<CounterDto> counters) {
        // Never let a racing response clobber an in-flight dialog edit or a
        // just-sent optimistic write.
        if (counterDialogOpen || System.currentTimeMillis() < suppressCountersRefreshUntilMillis) {
            return;
        }

        List<String> newOrder = new ArrayList<>();
        for (CounterDto c : counters) {
            if (c != null) {
                newOrder.add(c.id());
            }
        }

        if (newOrder.equals(lastCounterOrder)) {
            // Same id set and order: update tiles IN PLACE so hover state, an
            // in-flight drag, and keyboard focus all survive the poll tick.
            for (CounterDto c : counters) {
                if (c == null) {
                    continue;
                }
                CounterTile tile = counterTilesById.get(c.id());
                if (tile != null) {
                    tile.updateFrom(c);
                    tile.reconcileValue(c.value());
                }
            }
            return;
        }

        rebuildCounterGrid(counters);
        lastCounterOrder = newOrder;
    }

    private void rebuildCounterGrid(List<CounterDto> counters) {
        counterGrid.getChildren().clear();
        counterTilesById.clear();
        for (CounterDto c : counters) {
            if (c == null) {
                continue;
            }
            CounterTile tile = new CounterTile(c, counterActions());
            counterTilesById.put(c.id(), tile);
            counterGrid.getChildren().add(tile);
        }
    }

    private CounterTile.Actions counterActions() {
        return new CounterTile.Actions() {
            @Override
            public void bump(String counterId, int delta) {
                suppressCountersRefreshFor(2500);
                new Thread(() -> {
                    try {
                        CounterDto updated = Counters.bump(counterId, delta);
                        Platform.runLater(() -> {
                            CounterTile tile = counterTilesById.get(counterId);
                            if (tile != null && updated != null) {
                                tile.reconcileValue(updated.value());
                            }
                        });
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        ApiSession.get().reportError(ex);
                    }
                }, "counter-bump").start();
            }

            @Override
            public void edit(CounterDto counter) {
                openEditCounterDialog(counter);
            }

            @Override
            public void delete(CounterDto counter) {
                confirmAndDelete(counter);
            }

            @Override
            public void reorder(String draggedId, String ontoCounterId) {
                reorderCounters(draggedId, ontoCounterId);
            }
        };
    }

    private void openAddCounterDialog() {
        counterDialogOpen = true;
        CounterDialog dialog = new CounterDialog(DarkModeManager.windowOf(counterGrid), null);
        dialog.showAndWait().ifPresentOrElse(
                result -> {
                    counterDialogOpen = false;
                    new Thread(() -> {
                        try {
                            Counters.createCounter(result.label(), result.description(), result.value(), result.icon());
                            Platform.runLater(this::loadCounters);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                            ApiSession.get().reportError(ex);
                        }
                    }, "counter-create").start();
                },
                () -> counterDialogOpen = false);
    }

    private void openEditCounterDialog(CounterDto counter) {
        counterDialogOpen = true;
        CounterDialog dialog = new CounterDialog(DarkModeManager.windowOf(counterGrid), counter);
        dialog.showAndWait().ifPresentOrElse(
                result -> {
                    counterDialogOpen = false;
                    new Thread(() -> {
                        try {
                            Counters.updateAll(counter.id(), result.label(), result.description(),
                                    result.value(), result.icon());
                            Platform.runLater(this::loadCounters);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                            ApiSession.get().reportError(ex);
                        }
                    }, "counter-edit").start();
                },
                () -> counterDialogOpen = false);
    }

    private void confirmAndDelete(CounterDto counter) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        DarkModeManager.prepareDialog(alert, DarkModeManager.windowOf(counterGrid));
        alert.setTitle("Delete counter");
        alert.setHeaderText("Delete \"" + counter.label() + "\"?");
        alert.setContentText("This removes it for both accounts and cannot be undone.");
        alert.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                suppressCountersRefreshFor(2500);
                new Thread(() -> {
                    try {
                        Counters.deleteCounter(counter.id());
                        Platform.runLater(this::loadCounters);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        ApiSession.get().reportError(ex);
                    }
                }, "counter-delete").start();
            }
        });
    }

    private void reorderCounters(String draggedId, String targetId) {
        if (draggedId == null || targetId == null || draggedId.equals(targetId)) {
            return;
        }
        List<String> order = new ArrayList<>(counterTilesById.keySet());
        if (!order.remove(draggedId)) {
            return;
        }
        int targetIndex = order.indexOf(targetId);
        if (targetIndex < 0) {
            targetIndex = order.size();
        }
        order.add(targetIndex, draggedId);

        // Optimistic reorder: the tiles already exist, just re-add them to the
        // FlowPane (and the tracking map) in the new order.
        Map<String, CounterTile> reordered = new LinkedHashMap<>();
        counterGrid.getChildren().clear();
        for (String id : order) {
            CounterTile tile = counterTilesById.get(id);
            if (tile != null) {
                reordered.put(id, tile);
                counterGrid.getChildren().add(tile);
            }
        }
        counterTilesById.clear();
        counterTilesById.putAll(reordered);
        lastCounterOrder = order;

        suppressCountersRefreshFor(3000);
        new Thread(() -> {
            try {
                Counters.setCounterOrderBulk(order);
            } catch (Exception ex) {
                ex.printStackTrace();
                ApiSession.get().reportError(ex);
            }
        }, "counter-reorder").start();
    }

    private void suppressCountersRefreshFor(long millis) {
        suppressCountersRefreshUntilMillis = System.currentTimeMillis() + millis;
    }
}
