package dk.dtu;

import dk.dtu.shared.Config;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.*;

public final class ClientConnectDialog {

    public record ConnectionSettings(String serverIp, int port) {}

    private ClientConnectDialog() {}

    public static ConnectionSettings show(Stage owner) {
        Stage stage = new Stage();
        stage.setTitle("Connect to TodoList Server");
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);

        // Mode selection (match server dialog)
        ToggleGroup group = new ToggleGroup();
        RadioButton localhostMode = new RadioButton("Localhost");
        RadioButton scanMode = new RadioButton("Network");
        RadioButton manualMode = new RadioButton("Manual");
        localhostMode.setToggleGroup(group);
        scanMode.setToggleGroup(group);
        manualMode.setToggleGroup(group);
        localhostMode.setSelected(true);

        // IP selection (match server dialog)
        ComboBox<String> ipCombo = new ComboBox<>();
        ipCombo.setEditable(false);
        ipCombo.setPrefWidth(240);
        ipCombo.getItems().add("127.0.0.1");
        ipCombo.getSelectionModel().selectFirst();

        Button scanButton = new Button("Rescan");
        scanButton.getStyleClass().add("secondary-button");
        scanButton.setDisable(true);

        TextField manualIpField = new TextField();
        manualIpField.setPromptText("e.g. 192.168.1.25");
        manualIpField.setPrefWidth(240);
        manualIpField.setDisable(true);

        // Port field (same behavior as server)
        TextField portField = new TextField(Integer.toString(Config.getPort()));
        portField.setPrefWidth(90);
        portField.textProperty().addListener((obs, oldV, newV) -> {
            if (newV == null) return;
            if (!newV.matches("\\d*")) {
                portField.setText(newV.replaceAll("[^\\d]", ""));
            }
        });

        Label statusLabel = new Label("");
        statusLabel.getStyleClass().add("status-label");

        Label connectPreview = new Label("");
        connectPreview.getStyleClass().add("connect-info-label");

        Button connectBtn = new Button("Connect");
        connectBtn.getStyleClass().add("success-button");

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("secondary-button");

        // Layout (match server dialog structure)
        HBox modeRow = new HBox(15, new Label("Mode:"), localhostMode, scanMode, manualMode);
        modeRow.setAlignment(Pos.CENTER_LEFT);

        HBox hostRow = new HBox(10,
                new Label("Server IP:"),
                ipCombo,
                manualIpField,
                scanButton,
                new Label("Port:"),
                portField);
        hostRow.setAlignment(Pos.CENTER_LEFT);

        HBox buttonsRow = new HBox(12, connectBtn, cancelBtn);
        buttonsRow.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(15, modeRow, hostRow, statusLabel, connectPreview, buttonsRow);

        // IMPORTANT:
        // - Do NOT add padding here (config-panel CSS already has padding)
        // - Make panel stretch to fill the window
        root.getStyleClass().add("config-panel");
        root.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        // Outer background layer (grey)
        StackPane container = new StackPane(root);
        container.setStyle("-fx-background-color: #e8ebf0;");
        container.setPadding(Insets.EMPTY); // remove outer whitespace
        StackPane.setAlignment(root, Pos.CENTER);

        Runnable updatePreview = () -> {
            String ip;
            if (localhostMode.isSelected()) {
                ip = "127.0.0.1";
            } else if (scanMode.isSelected()) {
                ip = ipCombo.getValue();
            } else {
                ip = manualIpField.getText() != null ? manualIpField.getText().trim() : "";
            }

            Integer port = parsePort(portField.getText());
            boolean ok = ip != null && !ip.isBlank() && port != null;

            connectBtn.setDisable(!ok);
            connectPreview.setText(ok ? ("Connect to: tcp://" + ip + ":" + port + "/") : "");
        };

        Runnable updateModeUi = () -> {
            boolean isScan = scanMode.isSelected();
            boolean isManual = manualMode.isSelected();

            ipCombo.setDisable(isManual);
            scanButton.setDisable(!isScan);
            manualIpField.setDisable(!isManual);

            if (isScan && ipCombo.getItems().size() <= 1) {
                scanButton.fire();
            }

            updatePreview.run();
        };

        scanButton.setOnAction(e -> {
            statusLabel.setText("Scanning network interfaces...");
            List<String> ips = scanLocalIPv4s();
            if (ips.isEmpty()) {
                statusLabel.setText("No network interfaces found");
                return;
            }

            if (!ips.contains("127.0.0.1")) {
                ips.add(0, "127.0.0.1");
            }

            ipCombo.getItems().setAll(ips);

            String recommended = ips.stream()
                    .filter(x -> !Objects.equals(x, "127.0.0.1"))
                    .findFirst()
                    .orElse("127.0.0.1");
            ipCombo.getSelectionModel().select(recommended);

            statusLabel.setText("Found " + ips.size() + " network interface(s). Recommended: " + recommended);
            updatePreview.run();
        });

        group.selectedToggleProperty().addListener((obs, oldT, newT) -> updateModeUi.run());
        ipCombo.valueProperty().addListener((obs, o, n) -> updatePreview.run());
        manualIpField.textProperty().addListener((obs, o, n) -> updatePreview.run());
        portField.textProperty().addListener((obs, o, n) -> updatePreview.run());

        final ConnectionSettings[] result = new ConnectionSettings[1];

        connectBtn.setOnAction(e -> {
            String ip;
            if (localhostMode.isSelected()) {
                ip = "127.0.0.1";
            } else if (scanMode.isSelected()) {
                ip = ipCombo.getValue();
            } else {
                ip = manualIpField.getText() != null ? manualIpField.getText().trim() : "";
            }

            Integer port = parsePort(portField.getText());
            if (ip == null || ip.isBlank() || port == null) return;

            result[0] = new ConnectionSettings(ip.trim(), port);
            stage.close();
        });

        cancelBtn.setOnAction(e -> {
            result[0] = null;
            stage.close();
        });

        stage.setOnCloseRequest(e -> result[0] = null);

        Scene scene = new Scene(container, 900, 300);

        // Apply client stylesheet
        try {
            scene.getStylesheets().add(ClientConnectDialog.class.getResource("/common.css").toExternalForm());
        } catch (Exception ex) {
            System.out.println("Warning: Could not load common.css");
        }

        stage.setScene(scene);
        stage.setMinWidth(900);
        stage.setMinHeight(300);

        updateModeUi.run();
        stage.showAndWait();

        return result[0];
    }

    private static Integer parsePort(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            int port = Integer.parseInt(text.trim());
            return (port >= 1 && port <= 65535) ? port : null;
        } catch (Exception e) {
            return null;
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

        candidates.sort((a, b) -> Integer.compare(rankInterface(b), rankInterface(a)));

        List<String> results = new ArrayList<>();
        for (IpCandidate c : candidates) {
            if (!results.contains(c.ip)) results.add(c.ip);
        }
        return results;
    }

    private static int rankInterface(IpCandidate c) {
        String n = (c.ifaceName + " " + c.ifaceDisplayName).toLowerCase();

        int score = 0;

        if (n.contains("wi-fi") || n.contains("wifi") || n.contains("wireless") || n.contains("wlan")) score += 100;
        if (n.contains("ethernet") || n.contains("lan")) score += 80;

        if (n.contains("virtual") || n.contains("vmware") || n.contains("vbox") || n.contains("virtualbox")) score -= 120;
        if (n.contains("host-only") || n.contains("hostonly")) score -= 120;
        if (n.contains("tunnel") || n.contains("teredo") || n.contains("isatap")) score -= 150;
        if (n.contains("hyper-v") || n.contains("hyperv")) score -= 120;
        if (n.contains("vpn") || n.contains("wireguard") || n.contains("tailscale") || n.contains("hamachi")) score -= 60;

        return score;
    }

    private static class IpCandidate {
        final String ip;
        final String ifaceName;
        final String ifaceDisplayName;

        IpCandidate(String ip, String ifaceName, String ifaceDisplayName) {
            this.ip = ip;
            this.ifaceName = ifaceName != null ? ifaceName : "";
            this.ifaceDisplayName = ifaceDisplayName != null ? ifaceDisplayName : "";
        }
    }
}