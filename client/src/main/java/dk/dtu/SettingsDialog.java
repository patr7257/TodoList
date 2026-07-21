package dk.dtu;

import atlantafx.base.theme.Styles;
import dk.dtu.methods.Users;
import dk.dtu.shared.Config;
import dk.dtu.update.AppVersion;
import dk.dtu.update.ReleaseInfo;
import dk.dtu.update.UpdateChecker;
import dk.dtu.update.UpdateFlow;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Settings dialog for managing application preferences including:
 * - User management (add, delete, view users, set main users)
 * - Display options (tinted rows)
 */
public class SettingsDialog extends Dialog<ButtonType> {
    
    private static final Preferences prefs = Preferences.userNodeForPackage(SettingsDialog.class);
    private static final String TINTED_ROWS_KEY = "tintedRowsEnabled";
    
    private final TabPane tabPane;
    private Runnable onSettingsChanged;
    
    // Pending changes (not yet applied)
    private CheckBox tintedRowsCheckBox;
    private RadioButton oneUserRadio;
    private RadioButton twoUsersRadio;
    private ComboBox<String> mainUser1Combo;
    private ComboBox<String> mainUser2Combo;
    private ColorPicker color1Picker;
    private ColorPicker color2Picker;
    
    private static final ButtonType APPLY = new ButtonType("Apply", ButtonBar.ButtonData.OK_DONE);
    
    public SettingsDialog() {
        setTitle("Settings");
        setHeaderText("Application Settings");
        setResizable(true);
        
        tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        
        // Create tabs
        Tab userTab = new Tab("User Management", createUserManagementPane());
        Tab displayTab = new Tab("Display Options", createDisplayOptionsPane());
        Tab updatesTab = new Tab("Updates", createUpdatesPane());

        tabPane.getTabs().addAll(userTab, displayTab, updatesTab);
        
        getDialogPane().setContent(tabPane);
        getDialogPane().getButtonTypes().addAll(APPLY, ButtonType.CLOSE);
        getDialogPane().setPrefSize(700, 500);
        attachBrandStylesheet(getDialogPane().getStylesheets());
        
        // Handle Apply button
        setResultConverter(buttonType -> {
            if (buttonType == APPLY) {
                applySettings();
            }
            return buttonType;
        });
    }
    
    /**
     * Apply all pending settings changes
     */
    private void applySettings() {
        // Apply display settings
        setTintedRowsEnabled(tintedRowsCheckBox.isSelected());
        
        // Apply user management settings
        int userCount = oneUserRadio.isSelected() ? 1 : 2;
        MainUserConfig.setMainUserCount(userCount);
        
        String user1 = mainUser1Combo.getValue();
        if (user1 != null && !user1.isEmpty()) {
            MainUserConfig.setMainUser1(user1);
        }
        
        if (userCount == 2) {
            String user2 = mainUser2Combo.getValue();
            if (user2 != null && !user2.isEmpty()) {
                MainUserConfig.setMainUser2(user2);
            }
        }
        
        // Apply colors
        if (color1Picker.getValue() != null) {
            MainUserConfig.setMainUser1Color(toHexString(color1Picker.getValue()));
        }
        if (color2Picker.getValue() != null) {
            MainUserConfig.setMainUser2Color(toHexString(color2Picker.getValue()));
        }
        
        notifySettingsChanged();
        showAlert("Settings Applied", 
                "All settings have been saved. Please restart or refresh your views to see the changes.");
    }
    
    /**
     * Attach the brand overlay stylesheet so app-specific style classes resolve
     * inside this dialog and any sub-windows it opens.
     */
    private void attachBrandStylesheet(java.util.List<String> stylesheets) {
        // Delegates so the dialog picks up the current light/dark warm tokens.
        DarkModeManager.applyBrand(stylesheets);
    }

    /**
     * Convert JavaFX Color to hex string
     */
    private String toHexString(Color color) {
        return String.format("#%02x%02x%02x",
            (int) (color.getRed() * 255),
            (int) (color.getGreen() * 255),
            (int) (color.getBlue() * 255));
    }
    
    /**
     * Set a callback to run when settings are changed
     */
    public void setOnSettingsChanged(Runnable callback) {
        this.onSettingsChanged = callback;
    }
    
    /**
     * Check if tinted rows are enabled
     */
    public static boolean isTintedRowsEnabled() {
        return prefs.getBoolean(TINTED_ROWS_KEY, false);
    }
    
    /**
     * Set whether tinted rows are enabled
     */
    public static void setTintedRowsEnabled(boolean enabled) {
        prefs.putBoolean(TINTED_ROWS_KEY, enabled);
    }
    
    private VBox createUserManagementPane() {
        VBox container = new VBox(20);
        container.setPadding(new Insets(20));
        
        // Main users configuration section
        Label mainUsersLabel = new Label("Main Users (shown as big buttons on login screen)");
        mainUsersLabel.getStyleClass().add("settings-section-title");
        
        // Number of main users
        oneUserRadio = new RadioButton("One main user");
        twoUsersRadio = new RadioButton("Two main users");
        ToggleGroup userCountGroup = new ToggleGroup();
        oneUserRadio.setToggleGroup(userCountGroup);
        twoUsersRadio.setToggleGroup(userCountGroup);
        
        if (MainUserConfig.getMainUserCount() == 1) {
            oneUserRadio.setSelected(true);
        } else {
            twoUsersRadio.setSelected(true);
        }
        
        HBox userCountBox = new HBox(15, oneUserRadio, twoUsersRadio);
        
        // Main user selection
        mainUser1Combo = new ComboBox<>();
        mainUser1Combo.setPromptText("Select first main user");
        mainUser1Combo.setPrefWidth(200);
        
        color1Picker = new ColorPicker();
        color1Picker.setValue(Color.web(MainUserConfig.getMainUser1Color()));
        color1Picker.setPrefWidth(100);
        
        HBox user1Box = new HBox(10, mainUser1Combo, color1Picker);
        user1Box.setAlignment(Pos.CENTER_LEFT);
        
        mainUser2Combo = new ComboBox<>();
        mainUser2Combo.setPromptText("Select second main user");
        mainUser2Combo.setPrefWidth(200);
        
        color2Picker = new ColorPicker();
        color2Picker.setValue(Color.web(MainUserConfig.getMainUser2Color()));
        color2Picker.setPrefWidth(100);
        
        HBox user2Box = new HBox(10, mainUser2Combo, color2Picker);
        
        // Load users into combos (without stars for cleaner UI)
        loadUsersIntoCombo(mainUser1Combo);
        loadUsersIntoCombo(mainUser2Combo);
        
        // Set current values
        mainUser1Combo.setValue(MainUserConfig.getMainUser1());
        mainUser2Combo.setValue(MainUserConfig.getMainUser2());
        
        // Enable/disable second combo based on selection
        mainUser2Combo.setDisable(MainUserConfig.getMainUserCount() == 1);
        color2Picker.setDisable(MainUserConfig.getMainUserCount() == 1);
        
        oneUserRadio.setOnAction(e -> {
            mainUser2Combo.setDisable(true);
            color2Picker.setDisable(true);
        });
        
        twoUsersRadio.setOnAction(e -> {
            mainUser2Combo.setDisable(false);
            color2Picker.setDisable(false);
        });
        
        VBox mainUsersBox = new VBox(10);
        mainUsersBox.getChildren().addAll(
            mainUsersLabel,
            userCountBox,
            new Label("First main user:"),
            user1Box,
            new Label("Second main user (if enabled):"),
            user2Box
        );
        mainUsersBox.getStyleClass().add("settings-panel");

        // User management button
        Button seeAllUsersButton = new Button("See All Users");
        seeAllUsersButton.setPrefWidth(200);
        seeAllUsersButton.setPrefHeight(50);
        seeAllUsersButton.getStyleClass().add(Styles.ACCENT);
        seeAllUsersButton.setOnAction(e -> showAllUsersDialog(mainUser1Combo, mainUser2Combo));
        
        VBox buttonContainer = new VBox(seeAllUsersButton);
        buttonContainer.setAlignment(Pos.CENTER);
        buttonContainer.setPadding(new Insets(20));
        
        container.getChildren().addAll(
            mainUsersBox,
            new Separator(),
            buttonContainer
        );
        
        return container;
    }
    
    private VBox createDisplayOptionsPane() {
        VBox container = new VBox(20);
        container.setPadding(new Insets(20));
        
        Label title = new Label("Display Settings");
        title.getStyleClass().add("settings-section-title");

        // Info label
        Label infoLabel = new Label("⚠ Changes will be applied when you click the 'Apply' button below.");
        infoLabel.getStyleClass().add("settings-warning");
        infoLabel.setWrapText(true);
        
        // Tinted rows toggle
        tintedRowsCheckBox = new CheckBox("Enable tinted task rows (tasks colored by status)");
        tintedRowsCheckBox.setSelected(isTintedRowsEnabled());
        
        Label tintedRowsDesc = new Label(
            "When enabled, task rows will have a stronger background tint matching their status color."
        );
        tintedRowsDesc.getStyleClass().add("settings-note");
        tintedRowsDesc.setWrapText(true);

        VBox tintedRowsBox = new VBox(8, tintedRowsCheckBox, tintedRowsDesc);
        tintedRowsBox.getStyleClass().add("settings-panel");
        
        container.getChildren().addAll(infoLabel, title, tintedRowsBox);
        
        return container;
    }
    
    private VBox createUpdatesPane() {
        VBox container = new VBox(16);
        container.setPadding(new Insets(20));

        Label title = new Label("Software Updates");
        title.getStyleClass().add("settings-section-title");

        Label versionLabel = new Label("Current version: " + AppVersion.current());

        Button checkButton = new Button("Check for updates");
        checkButton.getStyleClass().add(Styles.ACCENT);

        // Inline status area (message plus an optional action button).
        Label statusLabel = new Label();
        statusLabel.setWrapText(true);
        Button actionButton = new Button();
        actionButton.setVisible(false);
        actionButton.setManaged(false);

        VBox statusBox = new VBox(8, statusLabel, actionButton);

        checkButton.setOnAction(e -> {
            checkButton.setDisable(true);
            statusLabel.setText("Checking...");
            actionButton.setVisible(false);
            actionButton.setManaged(false);

            new Thread(() -> {
                ReleaseInfo release = new UpdateChecker().findNewerRelease().orElse(null);
                Platform.runLater(() -> {
                    checkButton.setDisable(false);
                    if (release == null) {
                        statusLabel.setText("You are on the latest version (" + AppVersion.current() + ").");
                        actionButton.setVisible(false);
                        actionButton.setManaged(false);
                        return;
                    }

                    statusLabel.setText("Update available: v" + release.version());
                    actionButton.setVisible(true);
                    actionButton.setManaged(true);

                    if (release.hasInstallableAsset()) {
                        actionButton.setText("Update now");
                        actionButton.getStyleClass().setAll("button", Styles.ACCENT);
                        actionButton.setOnAction(ev -> {
                            actionButton.setDisable(true);
                            actionButton.setText("Downloading...");
                            UpdateFlow.downloadAndInstall(release, () -> {
                                actionButton.setDisable(false);
                                actionButton.setText("Update now");
                                statusLabel.setText("Update failed. Opened the releases page instead.");
                            });
                        });
                    } else {
                        actionButton.setText("Open releases page");
                        actionButton.getStyleClass().setAll("button");
                        actionButton.setOnAction(ev -> UpdateFlow.openReleasesPage(release.releasePageUrl()));
                    }
                });
            }, "settings-update-check").start();
        });

        VBox panel = new VBox(12, versionLabel, checkButton, statusBox);
        panel.getStyleClass().add("settings-panel");

        container.getChildren().addAll(title, panel);
        return container;
    }

    private void loadUsersIntoListView(ListView<String> listView) {
        new Thread(() -> {
            try {
                List<String> users = Users.getUsersCached(Config.getUsersUri());

                List<String> usernames = new ArrayList<>();
                for (String username : users) {
                    // Add star to main users
                    if (MainUserConfig.isMainUser(username)) {
                        usernames.add(username + " *");
                    } else {
                        usernames.add(username);
                    }
                }

                Platform.runLater(() -> listView.getItems().setAll(usernames));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }, "load-users-list").start();
    }

    private void loadUsersIntoCombo(ComboBox<String> combo) {
        new Thread(() -> {
            try {
                List<String> usernames = new ArrayList<>(Users.getUsersCached(Config.getUsersUri()));

                String currentValue = combo.getValue();
                Platform.runLater(() -> {
                    combo.getItems().setAll(usernames);
                    if (currentValue != null && usernames.contains(currentValue)) {
                        combo.setValue(currentValue);
                    }
                });
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }, "load-users-combo").start();
    }
    
    private void showAddUserDialog(ListView<String> listView, ComboBox<String> combo1, ComboBox<String> combo2) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add User");
        dialog.setHeaderText("Create a new user");
        dialog.setContentText("Username (max 15 characters):");
        
        dialog.getEditor().textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.length() > 15) {
                dialog.getEditor().setText(oldVal);
            }
        });
        
        dialog.showAndWait().ifPresent(username -> {
            String trimmedUsername = username.trim();
            if (trimmedUsername.isEmpty()) {
                showAlert("Invalid Username", "Username cannot be empty.");
                return;
            }
            
            new Thread(() -> {
                try {
                    Users.createNewUser(trimmedUsername, Config.getUsersUri(), 
                        (message) -> Platform.runLater(() -> {
                            showAlert("Success", message);
                            loadUsersIntoListView(listView);
                            loadUsersIntoCombo(combo1);
                            loadUsersIntoCombo(combo2);
                        }),
                        (error) -> Platform.runLater(() -> showAlert("Error", error))
                    );
                } catch (Exception ex) {
                    Platform.runLater(() -> showAlert("Error", "Failed to create user: " + ex.getMessage()));
                }
            }, "add-user").start();
        });
    }
    
    private void showAllUsersDialog(ComboBox<String> mainUser1Combo, ComboBox<String> mainUser2Combo) {
        Stage userManagementStage = new Stage();
        userManagementStage.setTitle("User Management");
        userManagementStage.initModality(Modality.APPLICATION_MODAL);
        
        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        
        Label title = new Label("All System Users");
        title.getStyleClass().add("settings-section-title");
        
        ListView<String> usersListView = new ListView<>();
        usersListView.setPrefHeight(300);
        usersListView.setPrefWidth(400);
        VBox.setVgrow(usersListView, Priority.ALWAYS);
        
        // Load users
        loadUsersIntoListView(usersListView);
        
        // Buttons
        Button addUserButton = new Button("Add User");
        addUserButton.setPrefWidth(120);
        addUserButton.setOnAction(e -> {
            showAddUserDialog(usersListView, mainUser1Combo, mainUser2Combo);
            loadUsersIntoListView(usersListView);
        });
        
        Button deleteUserButton = new Button("Delete User");
        deleteUserButton.setPrefWidth(120);
        deleteUserButton.setOnAction(e -> {
            String selectedUser = usersListView.getSelectionModel().getSelectedItem();
            if (selectedUser != null) {
                showDeleteUserDialog(selectedUser, usersListView, mainUser1Combo, mainUser2Combo);
            } else {
                showAlert("No Selection", "Please select a user from the list above to delete.");
            }
        });
        
        Button refreshButton = new Button("Refresh");
        refreshButton.setPrefWidth(120);
        refreshButton.setOnAction(e -> {
            loadUsersIntoListView(usersListView);
            loadUsersIntoCombo(mainUser1Combo);
            loadUsersIntoCombo(mainUser2Combo);
        });
        
        Button closeButton = new Button("Close");
        closeButton.setPrefWidth(120);
        closeButton.setOnAction(e -> {
            loadUsersIntoCombo(mainUser1Combo);
            loadUsersIntoCombo(mainUser2Combo);
            userManagementStage.close();
        });
        
        HBox buttonBox = new HBox(10, addUserButton, deleteUserButton, refreshButton, closeButton);
        buttonBox.setAlignment(Pos.CENTER);
        
        root.getChildren().addAll(title, usersListView, buttonBox);
        
        Scene scene = new Scene(root, 500, 450);
        attachBrandStylesheet(scene.getStylesheets());
        userManagementStage.setScene(scene);
        userManagementStage.showAndWait();
    }
    
    private void showDeleteUserDialog(String displayUsername, ListView<String> listView, 
                                     ComboBox<String> combo1, ComboBox<String> combo2) {
        // Strip star if present
        String username = displayUsername.replace(" *", "");
        
        // Prevent deleting main users
        if (MainUserConfig.isMainUser(username)) {
            showAlert("Cannot Delete Main User", 
                "User '" + username + "' is currently configured as a main user. " +
                "Please change the main user settings first before attempting to delete this user.");
            return;
        }
        
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Delete");
        confirm.setHeaderText("Delete user '" + username + "'?");
        confirm.setContentText("This user will be permanently deleted if they don't own any lists or tasks.");
        
        confirm.showAndWait().ifPresent(response -> {
            if (response != ButtonType.OK) return;
            
            new Thread(() -> {
                try {
                    Users.deleteUser(Config.getRequestsUri(), Config.getResponsesUri(), username);
                    Platform.runLater(() -> {
                        showAlert("Success", "User '" + username + "' was successfully deleted.");
                        loadUsersIntoListView(listView);
                        loadUsersIntoCombo(combo1);
                        loadUsersIntoCombo(combo2);
                        notifySettingsChanged();
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> showAlert("Error", 
                        "Cannot delete user: " + ex.getMessage()));
                }
            }, "delete-user").start();
        });
    }
    
    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    private void notifySettingsChanged() {
        if (onSettingsChanged != null) {
            onSettingsChanged.run();
        }
    }
}
