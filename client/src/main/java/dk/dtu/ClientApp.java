package dk.dtu;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import org.jspace.FormalField;
import org.jspace.RemoteSpace;

import java.util.List;

/**
 * JavaFX client
 *  - Scenes + controllers (login, lists overview, tasks view)
 *  - Navigation (in SceneNavigator)
 *  - UI states (current user, selected list etc)
 */
public class ClientApp extends Application {

    private static final String HOST = "127.0.0.1";
    private static final int PORT = 9001;
    private static final String TODO_LISTS_URI = "tcp://" + HOST + ":" + PORT + "/todoLists?keep";

    private final Label statusLabel = new Label("Not connected");
    private final ListView<String> listsView = new ListView<>();

    public static void main(String[] args) throws InterruptedException {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Todo Lists");

        Button refreshButton = new Button("Refresh");
        refreshButton.setOnAction(e -> loadLists());

        VBox root = new VBox(10,
                statusLabel,
                refreshButton,
                new Label("Lists:"),
                listsView
        );
        root.setStyle("-fx-padding: 12;");

        primaryStage.setScene(new Scene(root, 420, 420));
        primaryStage.show();

        loadLists();
    }

    private void loadLists() {
        setStatus("Connecting to " + TODO_LISTS_URI + " ...");
        new Thread(() -> {
            try {
                RemoteSpace todoLists = new RemoteSpace(TODO_LISTS_URI);
                
                // Server tuple format: (listId:String, listName:String)
                List<Object[]> tuples = todoLists.queryAll(
                        new FormalField(String.class),
                        new FormalField(String.class)
                );

                Platform.runLater(() -> {
                    listsView.getItems().clear();
                    for (Object[] t : tuples) {
                        String id = (String) t[0];
                        String name = (String) t[1];
                        listsView.getItems().add(id + " - " + name);
                    }
                    setStatus("Loaded " + tuples.size() + " lists");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> setStatus("Failed: " + ex.getMessage()));
            }
        }, "load-lists").start();
    }

    private void setStatus(String text) {
        statusLabel.setText(text);
    }
}
