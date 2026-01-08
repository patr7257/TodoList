package dk.dtu;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import dk.dtu.TupleSpaces;
import java.util.UUID;
import java.util.List;

import org.jspace.FormalField;
import org.jspace.RemoteSpace;

/**
 * JavaFX client
 * - Scenes + controllers (login, lists overview, tasks view)
 * - Navigation (in SceneNavigator)
 * - UI states (current user, selected list etc)
 */
public class ClientApp extends Application {

    private static final String HOST = "127.0.0.1";
    private static final int PORT = 9001;
    private static final String TODO_LISTS_URI = "tcp://" + HOST + ":" + PORT + "/todoLists?keep";
    private static final String COUNTER_URI = "tcp://" + HOST + ":" + PORT + "/counter?keep";
    private static final String REQUESTS_URI = "tcp://" + HOST + ":" + PORT + "/" + TupleSpaces.REQUESTS + "?keep";
    private final Label statusLabel = new Label("Not connected");
    private final ListView<String> listsView = new ListView<>();
    private final Button counterButton = new Button("Counter: ?");
    private final Button pingButton = new Button("Send request to server"); // NEW

    private volatile boolean running = true;

    public static void main(String[] args) throws InterruptedException {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Todo Lists");

        Button refreshButton = new Button("Refresh");
        refreshButton.setOnAction(e -> loadLists());

        counterButton.setOnAction(e -> incrementCounter());
        pingButton.setOnAction(e -> sendPing());

        VBox root = new VBox(10,
                statusLabel,
                counterButton,
                pingButton,
                refreshButton,
                new Label("Lists:"),
                listsView);
        root.setStyle("-fx-padding: 12;");

        primaryStage.setScene(new Scene(root, 420, 420));
        primaryStage.show();

        primaryStage.setOnCloseRequest(e -> running = false);

        loadLists();
        startCounterPolling();
    }

    private void startCounterPolling() {
        new Thread(() -> {
            while (running) {
                try {
                    int count = readCounter();
                    Platform.runLater(() -> counterButton.setText("Counter: " + count));
                    Thread.sleep(500);
                } catch (Exception ex) {
                    Platform.runLater(() -> counterButton.setText("Counter: (offline)"));
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }, "counter-poll").start();
    }

    private int readCounter() throws Exception {
        RemoteSpace counter = new RemoteSpace(COUNTER_URI);
        Object[] tuple = counter.query(new FormalField(Integer.class));
        return (Integer) tuple[0];
    }

    private void incrementCounter() {
        new Thread(() -> {
            try {
                RemoteSpace counter = new RemoteSpace(COUNTER_URI);
                // Atomic increment: remove tuple, increment, put back
                Object[] tuple = counter.get(new FormalField(Integer.class));
                int current = (Integer) tuple[0];
                counter.put(current + 1);
            } catch (Exception ex) {
                Platform.runLater(() -> setStatus("Counter failed: " + ex.getMessage()));
            }
        }, "counter-inc").start();
    }

    private void sendPing() {
        new Thread(() -> {
            try {
                RemoteSpace requests = new RemoteSpace(REQUESTS_URI);

                String requestId = UUID.randomUUID().toString();

                // No nulls - use "" for unused fields
                requests.put(
                        TupleSpaces.CMD_PING, // "ping"
                        requestId,
                        "", // a1
                        "", // a2
                        "", // a3
                        "" // a4
                );

                Platform.runLater(() -> setStatus("Ping sent, requestId=" + requestId));
            } catch (Exception ex) {
                Platform.runLater(() -> setStatus("Ping failed: " + ex.getMessage()));
            }
        }, "ping-thread").start();
    }

    private void loadLists() {
        setStatus("Connecting to " + TODO_LISTS_URI + " ...");
        new Thread(() -> {
            try {
                RemoteSpace todoLists = new RemoteSpace(TODO_LISTS_URI);

                // Server tuple format: (listId:String, listName:String)
                List<Object[]> tuples = todoLists.queryAll(
                        new FormalField(String.class),
                        new FormalField(String.class));

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
