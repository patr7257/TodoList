package dk.dtu.collumns;

import dk.dtu.methods.Helpers;
import javafx.scene.Node;
import javafx.scene.control.ProgressBar;

import java.util.Comparator;

public class ListCompletionColumn implements Column<Helpers.ListEntry> {

    @Override
    public String id() {
        return "completion";
    }

    @Override
    public String title() {
        return "Completion";
    }

    @Override
    public double prefWidth() {
        return 200;
    }

    @Override
    public Comparator<Helpers.ListEntry> comparator() {
        return Comparator.comparingInt(e -> e.completionPercentage);
    }

    @Override
    public Node createHeader(ColumnHeaderContext<Helpers.ListEntry> ctx) {
        return ColumnUtils.createSortableHeaderLabel(title(), prefWidth(), () -> ctx.onSortRequested().accept(this));
    }

    @Override
    public ColumnCell<Helpers.ListEntry> createCell(ColumnCellContext<Helpers.ListEntry> ctx) {
        ProgressBar progressBar = new ProgressBar();
        progressBar.setPrefWidth(prefWidth() - 10);
        progressBar.setMaxWidth(prefWidth() - 10);
        progressBar.setPrefHeight(20);

        return new ColumnCell<>() {
            @Override
            public Node node() {
                return progressBar;
            }

            @Override
            public void update(Helpers.ListEntry item) {
                if (item == null) {
                    progressBar.setProgress(0);
                    progressBar.setTooltip(null);
                    return;
                }

                double progress = item.completionPercentage / 100.0;
                progressBar.setProgress(progress);

                progressBar.getStyleClass().removeAll(
                        "completion-high", "completion-mid", "completion-low", "completion-none");
                String band;
                if (progress >= 0.8) {
                    band = "completion-high";
                } else if (progress >= 0.5) {
                    band = "completion-mid";
                } else if (progress >= 0.3) {
                    band = "completion-low";
                } else {
                    band = "completion-none";
                }
                progressBar.getStyleClass().add(band);
                progressBar.setTooltip(new javafx.scene.control.Tooltip(item.completionPercentage + "% complete"));
            }
        };
    }
}
