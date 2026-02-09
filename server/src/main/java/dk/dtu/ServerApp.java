package dk.dtu;

import dk.dtu.shared.Config;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;

public class ServerApp extends Application {

    private static final class IpCandidate {
        final String ip;
        final String ifaceName;
        final String ifaceDisplayName;

        private IpCandidate(String ip, String ifaceName, String ifaceDisplayName) {
            this.ip = ip;
            this.ifaceName = ifaceName != null ? ifaceName : "";
            this.ifaceDisplayName = ifaceDisplayName != null ? ifaceDisplayName : "";
        }
    }

    private ServerEngine engine;
    private UiConsoleRedirect consoleRedirect;

    private final ListView<String> logList = new ListView<>();
    private final Label statusLabel = new Label("Not running");
    private final Label connectInfoLabel = new Label("");

    private final ComboBox<String> ipCombo = new ComboBox<>();
    private final TextField manualIpField = new TextField();
    private final TextField portField = new TextField("9001");

    private final RadioButton localhostMode = new RadioButton("Localhost");
    private final RadioButton scanMode = new RadioButton("Scan");
    private final RadioButton manualMode = new RadioButton("Manual");

    private final Button scanButton = new Button("Rescan");
    private final Button startButton = new Button("Start server");
    private final Button stopButton = new Button("Stop");
    private final Button copyConnectInfoButton = new Button("Copy connect info");

    @Override
    public void start(Stage stage) {
        stage.setTitle("TodoList Server");
        
        // Set window size to match client
        stage.setWidth(970);
        stage.setHeight(600);
        stage.setMinWidth(800);
        stage.setMinHeight(500);

        // Show simple welcome screen
        showWelcomeScreen(stage);

        stage.setOnCloseRequest(evt -> {
            try {
                onStop();
            } finally {
                if (consoleRedirect != null) {
                    consoleRedirect.close();
                    consoleRedirect = null;
                }
            }
        });

        stage.show();
    }
    
    private void showWelcomeScreen(Stage stage) {
        Label title = new Label("TodoList Server");
        title.setStyle("-fx-font-size: 42px; -fx-font-weight: 700; -fx-text-fill: #333333;");

        Label subtitle = new Label("Server Application for TodoList Management System");
        subtitle.setStyle("-fx-font-size: 18px; -fx-text-fill: #4a4a4a; -fx-font-weight: 500;");
        
        Label description = new Label("This is the server component. Start the server to allow clients to connect.");
        description.setStyle("-fx-font-size: 14px; -fx-text-fill: #666666;");
        description.setWrapText(true);
        description.setMaxWidth(500);

        Button continueButton = new Button("Start Configuration");
        continueButton.getStyleClass().add("primary-button");
        continueButton.setDefaultButton(true);
        continueButton.setOnAction(e -> {
            ServerConfigDialog.ServerConfig defaultConfig = new ServerConfigDialog.ServerConfig("127.0.0.1", 9001, "localhost");
            showServerControlPanel(stage, defaultConfig);
        });

        VBox root = new VBox(20, title, subtitle, description, continueButton);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40, 20, 40, 20));
        root.setStyle("-fx-background-color: #e8ebf0;");

        Scene scene = new Scene(root, 970, 600);
        
        // Apply stylesheet
        try {
            scene.getStylesheets().add(getClass().getResource("/server.css").toExternalForm());
        } catch (Exception e) {
            System.out.println("Warning: Could not load server.css");
        }

        stage.setScene(scene);
    }
    
    private void showServerControlPanel(Stage stage, ServerConfigDialog.ServerConfig initialConfig) {

        logList.setFocusTraversable(false);

        ToggleGroup group = new ToggleGroup();
        localhostMode.setToggleGroup(group);
        scanMode.setToggleGroup(group);
        manualMode.setToggleGroup(group);
        localhostMode.setSelected(true);

        ipCombo.setEditable(false);
        ipCombo.setPrefWidth(220);
        ipCombo.getItems().add("127.0.0.1");
        ipCombo.getSelectionModel().selectFirst();

        manualIpField.setPromptText("e.g. 192.168.1.25");
        manualIpField.setPrefWidth(220);
        manualIpField.setDisable(true);

        portField.setPrefWidth(90);
        portField.textProperty().addListener((obs, oldV, newV) -> {
            if (newV == null) return;
            if (!newV.matches("\\d*")) {
                portField.setText(newV.replaceAll("[^\\d]", ""));
            }
        });

        scanButton.setOnAction(e -> rescanIps(true));
        scanButton.getStyleClass().add("secondary-button");

        startButton.setOnAction(e -> onStart());
        startButton.getStyleClass().add("success-button");
        
        stopButton.setOnAction(e -> onStop());
        stopButton.setDisable(true);
        stopButton.getStyleClass().add("danger-button");

        copyConnectInfoButton.setOnAction(e -> copyConnectInfo());
        copyConnectInfoButton.setDisable(true);
        copyConnectInfoButton.getStyleClass().add("secondary-button");

        group.selectedToggleProperty().addListener((obs, oldT, newT) -> {
            updateModeUi();
            if (scanMode.isSelected()) {
                // When switching into Scan mode, force-select the recommended (Wi‑Fi/LAN) address.
                rescanIps(true);
            }
            updateConnectInfoPreview();
        });

        ipCombo.valueProperty().addListener((obs, o, n) -> updateConnectInfoPreview());
        manualIpField.textProperty().addListener((obs, o, n) -> updateConnectInfoPreview());
        portField.textProperty().addListener((obs, o, n) -> updateConnectInfoPreview());

        HBox modeRow = new HBox(12, new Label("Mode:"), localhostMode, scanMode, manualMode);
        modeRow.setAlignment(Pos.CENTER_LEFT);

        HBox ipRow = new HBox(10,
                new Label("Server IP:"),
                ipCombo,
                manualIpField,
                scanButton,
                new Label("Port:"),
                portField
        );
        ipRow.setAlignment(Pos.CENTER_LEFT);

        HBox buttonsRow = new HBox(12, startButton, stopButton, copyConnectInfoButton);
        buttonsRow.setAlignment(Pos.CENTER_LEFT);

        statusLabel.getStyleClass().add("status-label");
        connectInfoLabel.getStyleClass().add("connect-info-label");

        VBox top = new VBox(12, modeRow, ipRow, buttonsRow, statusLabel, connectInfoLabel);
        top.setPadding(new Insets(15));
        top.setStyle("-fx-background-color: white; -fx-border-color: #d0d0d0; -fx-border-width: 0 0 1.5 0;");

        VBox.setVgrow(logList, Priority.ALWAYS);
        BorderPane root = new BorderPane();
        root.setTop(top);
        root.setCenter(logList);
        root.setStyle("-fx-background-color: #e8ebf0;");
        BorderPane.setMargin(logList, new Insets(12, 12, 12, 12));

        // Set initial config values
        if ("localhost".equals(initialConfig.mode())) {
            localhostMode.setSelected(true);
            ipCombo.getSelectionModel().select("127.0.0.1");
        } else if ("scan".equals(initialConfig.mode())) {
            scanMode.setSelected(true);
            rescanIps(false);
            ipCombo.getSelectionModel().select(initialConfig.ip());
        } else {
            manualMode.setSelected(true);
            manualIpField.setText(initialConfig.ip());
        }
        portField.setText(String.valueOf(initialConfig.port()));

        updateModeUi();
        updateConnectInfoPreview();

        Scene scene = new Scene(root, 970, 600);
        
        // Apply stylesheet
        try {
            scene.getStylesheets().add(getClass().getResource("/server.css").toExternalForm());
        } catch (Exception e) {
            System.out.println("Warning: Could not load server.css");
        }
        
        stage.setScene(scene);

        stage.setOnCloseRequest(evt -> {
            try {
                onStop();
            } finally {
                if (consoleRedirect != null) {
                    consoleRedirect.close();
                    consoleRedirect = null;
                }
            }
        });

        stage.show();
    }

    private void updateModeUi() {
        boolean isManual = manualMode.isSelected();
        manualIpField.setDisable(!isManual);
        ipCombo.setDisable(isManual);

        boolean showScan = scanMode.isSelected();
        scanButton.setDisable(!showScan);

        if (localhostMode.isSelected()) {
            ipCombo.getSelectionModel().select("127.0.0.1");
        }

        if (isManual) {
            if (manualIpField.getText() == null || manualIpField.getText().isBlank()) {
                manualIpField.setText("127.0.0.1");
            }
        }
    }

    private void onStart() {
        if (engine != null && engine.isRunning()) {
            appendLog("Already running");
            return;
        }

        String ip = getSelectedIp();
        Integer port = parsePort();
        if (ip == null || ip.isBlank()) {
            appendLog("[ERR] Invalid IP");
            return;
        }
        if (port == null) {
            appendLog("[ERR] Invalid port");
            return;
        }

        // Bind behavior:
        // - Localhost: bind to 127.0.0.1 (only local connections)
        // - Scan/Manual: bind to 0.0.0.0 (accept LAN connections), but advertise chosen IP
        String bindHost = localhostMode.isSelected() ? "127.0.0.1" : "0.0.0.0";

        System.setProperty("todolist.server.ip", ip);
        System.setProperty("todolist.bind.host", bindHost);
        System.setProperty("todolist.port", String.valueOf(port));

        if (consoleRedirect == null) {
            consoleRedirect = new UiConsoleRedirect(this::appendLog);
        }

        try {
            engine = ServerEngine.start();
            statusLabel.setText("Running");
            connectInfoLabel.setText("Clients connect to: " + Config.getClientBaseUri());

            startButton.setDisable(true);
            stopButton.setDisable(false);
            copyConnectInfoButton.setDisable(false);

        } catch (Exception ex) {
            appendLog("[ERR] Failed to start: " + ex.getMessage());
            ex.printStackTrace();
            statusLabel.setText("Start failed");
        }
    }

    private void onStop() {
        if (engine != null) {
            try {
                engine.stop();
            } catch (Exception e) {
                appendLog("[ERR] Stop failed: " + e.getMessage());
            }
            engine = null;
        }

        statusLabel.setText("Not running");
        startButton.setDisable(false);
        stopButton.setDisable(true);
        copyConnectInfoButton.setDisable(true);
    }

    private void updateConnectInfoPreview() {
        String ip = getSelectedIp();
        Integer port = parsePort();
        if (ip == null || ip.isBlank() || port == null) {
            connectInfoLabel.setText("Clients connect to: (invalid config)");
            return;
        }
        connectInfoLabel.setText("Clients connect to: tcp://" + ip + ":" + port + "/");
    }

    private void copyConnectInfo() {
        String baseUri = Config.getClientBaseUri();
        ClipboardContent content = new ClipboardContent();
        content.putString(baseUri);
        Clipboard.getSystemClipboard().setContent(content);
        appendLog("Copied: " + baseUri);
    }

    private void appendLog(String line) {
        if (line == null) return;
        logList.getItems().add(line);
        logList.scrollTo(logList.getItems().size() - 1);
    }

    private void rescanIps(boolean forceSelectRecommended) {
        List<String> ips = scanLocalIPv4s();
        if (ips.isEmpty()) {
            appendLog("[ERR] No LAN IPv4 addresses found");
            return;
        }

        // Ensure localhost is always available in the dropdown.
        if (!ips.contains("127.0.0.1")) {
            ips.add(0, "127.0.0.1");
        }

        String prev = ipCombo.getValue();
        ipCombo.getItems().setAll(ips);

        if (forceSelectRecommended) {
            // pick first non-localhost
            String recommended = ips.stream().filter(x -> !Objects.equals(x, "127.0.0.1")).findFirst().orElse("127.0.0.1");
            ipCombo.getSelectionModel().select(recommended);
            appendLog("Recommended IP: " + recommended);
        } else if (prev != null && ips.contains(prev)) {
            ipCombo.getSelectionModel().select(prev);
        } else {
            ipCombo.getSelectionModel().selectFirst();
        }
    }

    private static List<String> scanLocalIPv4s() {
        List<IpCandidate> candidates = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            if (ifaces == null) return List.of();

            while (ifaces.hasMoreElements()) {
                NetworkInterface ni = ifaces.nextElement();
                try {
                    if (!ni.isUp() || ni.isLoopback()) continue;
                } catch (Exception ignored) {
                    continue;
                }

                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address) {
                        String ip = addr.getHostAddress();
                        if (ip == null || ip.isBlank()) continue;
                        candidates.add(new IpCandidate(ip, ni.getName(), ni.getDisplayName()));
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // Prefer Wi‑Fi / real LAN adapters, de-prioritize virtual/host-only/tunnel.
        candidates.sort((a, b) -> Integer.compare(rankInterface(b), rankInterface(a)));

        List<String> results = new ArrayList<>();
        for (IpCandidate c : candidates) {
            if (!results.contains(c.ip)) {
                results.add(c.ip);
            }
        }
        return results;
    }

    private static int rankInterface(IpCandidate c) {
        String n = (c.ifaceName + " " + c.ifaceDisplayName).toLowerCase();

        int score = 0;

        // Positive signals
        if (n.contains("wi-fi") || n.contains("wifi") || n.contains("wireless") || n.contains("wlan")) score += 100;
        if (n.contains("ethernet") || n.contains("lan")) score += 80;

        // Negative signals (virtual/host-only/VPN/tunnel)
        if (n.contains("virtual") || n.contains("vmware") || n.contains("vbox") || n.contains("virtualbox")) score -= 120;
        if (n.contains("host-only") || n.contains("hostonly")) score -= 120;
        if (n.contains("tunnel") || n.contains("teredo") || n.contains("isatap")) score -= 150;
        if (n.contains("hyper-v") || n.contains("hyperv")) score -= 120;
        if (n.contains("vpn") || n.contains("wireguard") || n.contains("tailscale") || n.contains("hamachi")) score -= 60;

        return score;
    }

    private String getSelectedIp() {
        if (manualMode.isSelected()) {
            return manualIpField.getText() != null ? manualIpField.getText().trim() : "";
        }
        return ipCombo.getValue() != null ? ipCombo.getValue().trim() : "";
    }

    private Integer parsePort() {
        String text = portField.getText();
        if (text == null || text.isBlank()) return null;
        try {
            int port = Integer.parseInt(text.trim());
            if (port < 1 || port > 65535) return null;
            return port;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
