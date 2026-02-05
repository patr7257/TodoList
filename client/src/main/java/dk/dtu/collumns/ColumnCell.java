package dk.dtu.collumns;

import javafx.scene.Node;

public interface ColumnCell<T> {
    Node node();

    void update(T item);
}
