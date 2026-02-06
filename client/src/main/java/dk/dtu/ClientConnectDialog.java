package dk.dtu;

import dk.dtu.shared.Config;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple startup dialog for picking which server to connect to.
 * Options:
 * - Localhost (127.0.0.1)
 * - Scan (discover servers on LAN by checking the configured port)
 * - Manual (type an IP)
 */
public final class ClientConnectDialog {

    public record ConnectionSettings(String serverIp, int port) {}

    private ClientConnectDialog() {}

    public static ConnectionSettings show(Stage owner) {
        Stage stage = new Stage();
        stage.setTitle("Connect to TodoList Server");
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);

        ToggleGroup group = new ToggleGroup();
        RadioButton localhostMode = new RadioButton("Localhost");
        RadioButton scanMode = new RadioButton("Scan");
        RadioButton manualMode = new RadioButton("Manual");
        localhostMode.setToggleGroup(group);
        scanMode.setToggleGroup(group);
        manualMode.setToggleGroup(group);
        localhostMode.setSelected(true);

        ComboBox<String> discoveredCombo = new ComboBox<>();
        discoveredCombo.setEditable(false);
        discoveredCombo.setPrefWidth(240);
        discoveredCombo.setDisable(true);

        Button scanButton = new Button("Rescan");
        scanButton.setDisable(true);

        TextField manualIpField = new TextField();
        manualIpField.setPromptText("e.g. 192.168.0.168");
        manualIpField.setPrefWidth(240);
        manualIpField.setDisable(true);

        TextField portField = new TextField(Integer.toString(Config.getPort()));
        portField.setPrefWidth(90);
        portField.textProperty().addListener((obs, oldV, newV) -> {
            if (newV == null) return;
            if (!newV.matches("\\d*")) {
                portField.setText(newV.replaceAll("[^\\d]", ""));
            }
        });

        Label statusLabel = new Label("");
        Label connectPreview = new Label("");
        connectPreview.setStyle("-fx-font-family: 'Consolas'; -fx-text-fill: #444;");

        Button connectBtn = new Button("Connect");
        Button cancelBtn = new Button("Cancel");

        HBox modeRow = new HBox(12, new Label("Mode:"), localhostMode, scanMode, manualMode);
        modeRow.setAlignment(Pos.CENTER_LEFT);

        HBox hostRow = new HBox(10,
                new Label("Server:"),
                discoveredCombo,
                manualIpField,
                scanButton,
                new Label("Port:"),
                portField);
        hostRow.setAlignment(Pos.CENTER_LEFT);

        HBox buttonsRow = new HBox(10, connectBtn, cancelBtn);
        buttonsRow.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(10, modeRow, hostRow, statusLabel, connectPreview, buttonsRow);
        root.setPadding(new Insets(12));

        AtomicBoolean scanning = new AtomicBoolean(false);
        AtomicBoolean scanCancel = new AtomicBoolean(false);
        ExecutorService scanPool = Executors.newFixedThreadPool(64, r -> {
            Thread t = new Thread(r, "server-scan");
            t.setDaemon(true);
            return t;
        });

        Runnable updatePreview = () -> {
            String ip;
            if (localhostMode.isSelected()) {
                ip = "127.0.0.1";
            } else if (scanMode.isSelected()) {
                ip = discoveredCombo.getValue();
            } else {
                ip = manualIpField.getText() != null ? manualIpField.getText().trim() : "";
            }

            Integer port = parsePort(portField.getText());
            boolean ok = ip != null && !ip.isBlank() && port != null;

            connectBtn.setDisable(!ok);
            connectPreview.setText(ok ? ("tcp://" + ip + ":" + port + "/") : "");
        };

        Runnable updateModeUi = () -> {
            boolean isScan = scanMode.isSelected();
            boolean isManual = manualMode.isSelected();

            discoveredCombo.setDisable(!isScan);
            scanButton.setDisable(!isScan);
            manualIpField.setDisable(!isManual);

            if (isScan && (discoveredCombo.getItems().isEmpty() || discoveredCombo.getValue() == null)) {
                // Kick off an initial scan when entering scan mode.
                scanButton.fire();
            }

            updatePreview.run();
        };

        scanButton.setOnAction(e -> {
            if (scanning.get()) {
                scanCancel.set(true);
                return;
            }

            scanCancel.set(false);
            scanning.set(true);
            statusLabel.setText("Scanning LAN for servers on port " + portField.getText() + "...");
            scanButton.setText("Stop");
            discoveredCombo.getItems().clear();
            discoveredCombo.setValue(null);

            Integer port = parsePort(portField.getText());
            if (port == null) {
                statusLabel.setText("[ERR] Invalid port");
                scanning.set(false);
                scanButton.setText("Rescan");
                updatePreview.run();
                return;
            }

            new Thread(() -> {
                try {
                    List<String> candidates = buildScanCandidates();
                    if (candidates.isEmpty()) {
                        Platform.runLater(() -> statusLabel.setText("[ERR] No LAN IPv4 detected to scan"));
                        return;
                    }

                    AtomicInteger scanned = new AtomicInteger(0);
                    AtomicInteger found = new AtomicInteger(0);
                    CompletionService<Void> cs = new ExecutorCompletionService<>(scanPool);

                    int submitted = 0;
                    for (String host : candidates) {
                        if (scanCancel.get()) break;
                        cs.submit(() -> {
                            if (scanCancel.get()) return null;
                            boolean open = isPortOpen(host, port, Duration.ofMillis(140));
                            int done = scanned.incrementAndGet();
                            if (open) {
                                int nowFound = found.incrementAndGet();
                                Platform.runLater(() -> {
                                    if (!discoveredCombo.getItems().contains(host)) {
                                        discoveredCombo.getItems().add(host);
                                        discoveredCombo.getItems().sort(String.CASE_INSENSITIVE_ORDER);
                                        if (discoveredCombo.getValue() == null) {
                                            discoveredCombo.getSelectionModel().select(host);
                                        }
                                    }
                                    statusLabel.setText("Scanning... (" + done + "/" + candidates.size() + ")  Found: " + nowFound);
                                    updatePreview.run();
                                });
                            } else if (done % 25 == 0) {
                                Platform.runLater(() -> statusLabel.setText("Scanning... (" + done + "/" + candidates.size() + ")  Found: " + found.get()));
                            }
                            return null;
                        });
                        submitted++;
                    }

                    // Drain completions so the thread ends deterministically.
                    for (int i = 0; i < submitted; i++) {
                        if (scanCancel.get()) break;
                        try {
                            Future<Void> f = cs.poll(250, TimeUnit.MILLISECONDS);
                            if (f != null) f.get();
                        } catch (Exception ignored) {
                        }
                    }

                    Platform.runLater(() -> {
                        if (scanCancel.get()) {
                            statusLabel.setText("Scan stopped. Found: " + found.get());
                        } else {
                            statusLabel.setText("Scan complete. Found: " + found.get());
                        }
                    });
                } finally {
                    Platform.runLater(() -> {
                        scanning.set(false);
                        scanButton.setText("Rescan");
                        updatePreview.run();
                    });
                }
            }, "scan-orchestrator").start();
        });

        group.selectedToggleProperty().addListener((obs, oldT, newT) -> updateModeUi.run());
        discoveredCombo.valueProperty().addListener((obs, o, n) -> updatePreview.run());
        manualIpField.textProperty().addListener((obs, o, n) -> updatePreview.run());
        portField.textProperty().addListener((obs, o, n) -> updatePreview.run());

        final ConnectionSettings[] result = new ConnectionSettings[1];

        connectBtn.setOnAction(e -> {
            String ip;
            if (localhostMode.isSelected()) {
                ip = "127.0.0.1";
            } else if (scanMode.isSelected()) {
                ip = discoveredCombo.getValue();
            } else {
                ip = manualIpField.getText() != null ? manualIpField.getText().trim() : "";
            }
            Integer port = parsePort(portField.getText());
            if (ip == null || ip.isBlank() || port == null) {
                return;
            }
            result[0] = new ConnectionSettings(ip.trim(), port);
            stage.close();
        });

        cancelBtn.setOnAction(e -> {
            result[0] = null;
            stage.close();
        });

        stage.setOnCloseRequest(e -> {
            // Treat window close as cancel.
            result[0] = null;
        });

        Scene scene = new Scene(root, 860, 240);
        stage.setScene(scene);

        stage.setMinWidth(860);
        stage.setMinHeight(240);

        updateModeUi.run();
        stage.showAndWait();

        // Stop any in-progress scan
        scanCancel.set(true);
        scanPool.shutdownNow();

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

    private static boolean isPortOpen(String host, int port, Duration timeout) {
        try (Socket socket = new Socket()) {
            int ms = (int) Math.max(50, Math.min(5000, timeout != null ? timeout.toMillis() : 200));
            socket.connect(new InetSocketAddress(host, port), ms);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Build a reasonable list of candidate hosts to check.
     * We scan /24s for the best adapters first (Wi‑Fi/Ethernet prioritized).
     */
    private static List<String> buildScanCandidates() {
        List<LocalIfaceIp> localIps = scanLocalIPv4sWithIfaces();
        if (localIps.isEmpty()) return List.of();

        localIps.sort((a, b) -> Integer.compare(rankInterface(b), rankInterface(a)));

        // Deduplicate /24 prefixes while keeping priority order.
        LinkedHashSet<String> prefixes = new LinkedHashSet<>();
        for (LocalIfaceIp e : localIps) {
            String p = to24Prefix(e.ip);
            if (p != null) prefixes.add(p);
        }

        List<String> result = new ArrayList<>();
        for (String prefix : prefixes) {
            for (int i = 1; i <= 254; i++) {
                result.add(prefix + i);
            }
        }

        // Avoid checking ourselves first (optional), but still allow it.
        // result.removeIf(ip -> localIps.stream().anyMatch(l -> l.ip.equals(ip)));

        return result;
    }

    private static String to24Prefix(String ip) {
        if (ip == null) return null;
        String[] parts = ip.trim().split("\\.");
        if (parts.length != 4) return null;
        return parts[0] + "." + parts[1] + "." + parts[2] + ".";
    }

    private record LocalIfaceIp(String ip, String ifaceName, String ifaceDisplayName) {}

    private static List<LocalIfaceIp> scanLocalIPv4sWithIfaces() {
        List<LocalIfaceIp> results = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            if (ifaces == null) return results;

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
                        if (ip != null && !ip.isBlank()) {
                            results.add(new LocalIfaceIp(ip, ni.getName(), ni.getDisplayName()));
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return results;
    }

    private static int rankInterface(LocalIfaceIp c) {
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
}
