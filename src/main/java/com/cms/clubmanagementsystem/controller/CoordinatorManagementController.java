package com.cms.clubmanagementsystem.controller;

import com.cms.clubmanagementsystem.utils.DatabaseConnector;
import com.cms.clubmanagementsystem.utils.PasswordService;
import com.cms.clubmanagementsystem.utils.SessionManager;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import org.mindrot.jbcrypt.BCrypt;

import java.security.SecureRandom;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.UUID;
import java.util.regex.Pattern;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

public class CoordinatorManagementController {

    @FXML private TableView<User> coordinatorsTable;
    @FXML private TableColumn<User, String> nameColumn;
    @FXML private TableColumn<User, String> emailColumn;
    @FXML private TableColumn<User, String> statusColumn;
    @FXML private Button activateButton;
    @FXML private Button deactivateButton;
    @FXML private Button createButton;
    @FXML private Button refreshButton;
    @FXML private TableColumn<User, String> usernameColumn;
    @FXML private TableColumn<User, String> phoneColumn;
    @FXML private TableColumn<User, String> createdColumn;
    @FXML private HBox statusContainer;

    private ObservableList<User> coordinators = FXCollections.observableArrayList();
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w-.]+@([\\w-]+\\.)+[\\w-]{2,4}$");

    @FXML
    public void initialize() {
        // NEW: Check if user has at least coordinator access
        if (!SessionManager.isCoordinator()) {
            showAlert(Alert.AlertType.ERROR, "Access Denied",
                    "You do not have coordinator permissions to access coordinator management.");
            // Disable the entire UI
            createButton.setDisable(true);
            activateButton.setDisable(true);
            deactivateButton.setDisable(true);
            refreshButton.setDisable(true);
            coordinatorsTable.setDisable(true);
            return;
        }

        setupTable();
        loadCoordinators();
        updateUIForPermissions();
        addPermissionStatusIndicator();
    }

    private void updateUIForPermissions() {
        boolean isActiveCoordinator = isCurrentUserActiveCoordinator();
        boolean isCoordinator = isCurrentUserCoordinator(); // NEW

        // Only active coordinators can create new coordinators
        createButton.setDisable(!isActiveCoordinator);

        // Only active coordinators can activate/deactivate other coordinators
        activateButton.setDisable(!isActiveCoordinator);
        deactivateButton.setDisable(!isActiveCoordinator);

        // NEW: Allow view access for any coordinator
        refreshButton.setDisable(!isCoordinator);
        coordinatorsTable.setDisable(!isCoordinator);

        // Add tooltips to explain why buttons are disabled
        if (!isActiveCoordinator) {
            Tooltip permissionTooltip = new Tooltip("Only active coordinators can perform this action");
            createButton.setTooltip(permissionTooltip);
            activateButton.setTooltip(permissionTooltip);
            deactivateButton.setTooltip(permissionTooltip);

            if (!isCoordinator) {
                Tooltip viewTooltip = new Tooltip("Coordinator access required to view coordinator management");
                refreshButton.setTooltip(viewTooltip);
                coordinatorsTable.setTooltip(viewTooltip);
            } else {
                refreshButton.setTooltip(null);
                coordinatorsTable.setTooltip(null);
            }
        } else {
            createButton.setTooltip(null);
            activateButton.setTooltip(null);
            deactivateButton.setTooltip(null);
            refreshButton.setTooltip(null);
            coordinatorsTable.setTooltip(null);
        }
    }

    private void setupTable() {
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("fullName"));
        emailColumn.setCellValueFactory(new PropertyValueFactory<>("email"));
        usernameColumn.setCellValueFactory(new PropertyValueFactory<>("username"));
        phoneColumn.setCellValueFactory(new PropertyValueFactory<>("phone"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("activeStatus"));
        createdColumn.setCellValueFactory(new PropertyValueFactory<>("createdAtFormatted"));

        coordinatorsTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSelection, newSelection) -> updateButtonStates(newSelection)
        );
    }

    private void loadCoordinators() {
        // NEW: Check if user has coordinator access
        if (!SessionManager.isCoordinator()) {
            showAlert(Alert.AlertType.ERROR, "Access Denied",
                    "You do not have permissions to view coordinators.");
            return;
        }

        coordinators.clear();
        String sql = """
    SELECT user_id, username, full_name, email, phone, is_active_coordinator, created_at 
    FROM users WHERE school_id = ? AND 
    role = 'club_coordinator' ORDER BY is_active_coordinator DESC, full_name""";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, SessionManager.getCurrentSchoolId());
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                User user = new User(
                        (UUID) rs.getObject("user_id"),
                        rs.getString("username"),
                        rs.getString("full_name"),
                        rs.getString("email"),
                        rs.getString("phone"),
                        rs.getBoolean("is_active_coordinator"),
                        rs.getTimestamp("created_at")
                );
                coordinators.add(user);
            }

            coordinatorsTable.setItems(coordinators);
        } catch (Exception e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to load coordinators: " + e.getMessage());
        }
    }

    private boolean isCurrentUserActiveCoordinator() {
        return SessionManager.isActiveCoordinator();
    }

    // NEW: Add this method for completeness
    private boolean isCurrentUserCoordinator() {
        return SessionManager.isCoordinator();
    }

    private void addPermissionStatusIndicator() {
        boolean isActiveCoordinator = isCurrentUserActiveCoordinator();
        boolean isCoordinator = isCurrentUserCoordinator(); // NEW

        String statusText;
        if (isActiveCoordinator) {
            statusText = "Active Coordinator (Full Permissions)";
        } else if (isCoordinator) {
            statusText = "Inactive Coordinator (View Only)";
        } else {
            statusText = "No Coordinator Access";
        }

        Label permissionLabel = new Label(statusText);
        if (isActiveCoordinator) {
            permissionLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
        } else if (isCoordinator) {
            permissionLabel.setStyle("-fx-text-fill: orange; -fx-font-weight: bold;");
        } else {
            permissionLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
        }

        // Clear any existing labels and add the new one
        statusContainer.getChildren().clear();
        statusContainer.getChildren().add(permissionLabel);
    }

    private void updateButtonStates(User selectedUser) {
        if (selectedUser != null) {
            activateButton.setDisable(selectedUser.isActive());
            deactivateButton.setDisable(!selectedUser.isActive());
        } else {
            activateButton.setDisable(true);
            deactivateButton.setDisable(true);
        }
    }

    @FXML
    private void handleCreateCoordinator() {
        if (!isCurrentUserActiveCoordinator()) {
            showAlert(Alert.AlertType.ERROR, "Permission Denied",
                    "Only active coordinators can create new coordinators.");
            return;
        }

        String tempPassword = generateTemporaryPassword();

        Dialog<User> dialog = new Dialog<>();
        dialog.setTitle("Create New Coordinator");
        dialog.setHeaderText("Enter coordinator details. A temporary password will be generated.");

        ButtonType createButtonType = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

        // Form fields
        TextField fullNameField = new TextField();
        TextField emailField = new TextField();
        TextField usernameField = new TextField();
        TextField phoneField = new TextField();

        // Display temporary password (read-only)
        TextField tempPasswordField = new TextField(tempPassword);
        tempPasswordField.setEditable(false);
        tempPasswordField.setStyle("-fx-background-color: #f0f0f0;");

        // Create error labels
        Label fullNameError = createErrorLabel();
        Label emailError = createErrorLabel();
        Label usernameError = createErrorLabel();
        Label phoneError = createErrorLabel();

        // Add all components to grid
        grid.add(new Label("Full Name*:"), 0, 0);
        grid.add(fullNameField, 1, 0);
        grid.add(fullNameError, 2, 0);

        grid.add(new Label("Email*:"), 0, 1);
        grid.add(emailField, 1, 1);
        grid.add(emailError, 2, 1);

        grid.add(new Label("Username*:"), 0, 2);
        grid.add(usernameField, 1, 2);
        grid.add(usernameError, 2, 2);

        grid.add(new Label("Phone*:"), 0, 3);
        grid.add(phoneField, 1, 3);
        grid.add(phoneError, 2, 3);

        grid.add(new Label("Temporary Password:"), 0, 4);
        grid.add(tempPasswordField, 1, 4);
        grid.add(new Label("Share this with the coordinator"), 2, 4);

        dialog.getDialogPane().setContent(grid);

        // Get create button and disable it initially
        javafx.scene.Node createButtonNode = dialog.getDialogPane().lookupButton(createButtonType);
        createButtonNode.setDisable(true);

        // Add validation listeners
        ChangeListener<String> validationListener = (observable, oldValue, newValue) -> {
            boolean isValid = validateCreateForm(
                    fullNameField.getText().trim(),
                    emailField.getText().trim(),
                    usernameField.getText().trim(),
                    phoneField.getText().trim(),
                    fullNameError,
                    emailError,
                    usernameError,
                    phoneError
            );
            createButtonNode.setDisable(!isValid);
        };

        fullNameField.textProperty().addListener(validationListener);
        emailField.textProperty().addListener(validationListener);
        usernameField.textProperty().addListener(validationListener);
        phoneField.textProperty().addListener(validationListener);

        // Trigger initial validation
        validationListener.changed(null, null, null);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == createButtonType) {
                return new User(null, usernameField.getText(), fullNameField.getText(),
                        emailField.getText(), phoneField.getText(), false, null);
            }
            return null;
        });

        dialog.showAndWait().ifPresent(result -> {
            boolean success = createCoordinator(
                    fullNameField.getText().trim(),
                    emailField.getText().trim(),
                    usernameField.getText().trim(),
                    tempPassword,
                    phoneField.getText().trim()
            );
            if (success) {
                showAlert(Alert.AlertType.INFORMATION, "Success",
                        "Coordinator created successfully!\n\n" +
                                "Temporary Password: " + tempPassword + "\n" +
                                "Share this with the coordinator. They will be required to change it on first login.");
            } else {
                showAlert(Alert.AlertType.ERROR, "Error", "Failed to create coordinator. Please check the logs.");
            }
        });
    }

    private boolean validateCreateForm(String fullName, String email, String username, String phone,
                                       Label fullNameError, Label emailError, Label usernameError, Label phoneError) {

        boolean isValid = true;

        // Validate full name
        if (fullName.isEmpty()) {
            fullNameError.setText("Full name is required");
            isValid = false;
        } else if (fullName.length() < 2) {
            fullNameError.setText("Full name must be at least 2 characters");
            isValid = false;
        } else {
            fullNameError.setText("");
        }

        // Validate email
        if (email.isEmpty()) {
            emailError.setText("Email is required");
            isValid = false;
        } else if (!EMAIL_PATTERN.matcher(email).matches()) {
            emailError.setText("Invalid email format");
            isValid = false;
        } else if (!isEmailAvailable(email)) {
            emailError.setText("Email already exists");
            isValid = false;
        } else {
            emailError.setText("");
        }

        // Validate username
        if (username.isEmpty()) {
            usernameError.setText("Username is required");
            isValid = false;
        } else if (username.length() < 3) {
            usernameError.setText("Username must be at least 3 characters");
            isValid = false;
        } else if (!isUsernameAvailable(username)) {
            usernameError.setText("Username already exists");
            isValid = false;
        } else {
            usernameError.setText("");
        }

        // Validate phone
        if (phone.isEmpty()) {
            phoneError.setText("Phone is required");
            isValid = false;
        } else if (!isValidPhone(phone)) {
            phoneError.setText("Invalid phone format");
            isValid = false;
        } else {
            phoneError.setText("");
        }

        return isValid;
    }

    private Label createErrorLabel() {
        Label errorLabel = new Label();
        errorLabel.setTextFill(Color.RED);
        errorLabel.setStyle("-fx-font-size: 10px;");
        return errorLabel;
    }

    private boolean isValidPhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return false;
        }

        try {
            PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
            String cleanedPhone = phone.trim();

            Phonenumber.PhoneNumber phoneNumber = phoneUtil.parse(cleanedPhone, null);
            String regionCode = phoneUtil.getRegionCodeForNumber(phoneNumber);

            if (!"ZZ".equals(regionCode)) {
                return phoneUtil.isValidNumberForRegion(phoneNumber, regionCode);
            }

            return phoneUtil.isValidNumber(phoneNumber);

        } catch (NumberParseException e) {
            System.out.println("Phone number validation failed: " + e.getMessage());
            return false;
        }
    }

    private String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt(12));
    }

    private boolean createCoordinator(String fullName, String email, String username, String tempPassword, String phone) {
        if (!isUsernameAvailable(username) || !isEmailAvailable(email)) {
            return false;
        }

        boolean hasActiveCoordinator = hasActiveCoordinator();
        boolean makeActive = !hasActiveCoordinator;

        String sql = """
    INSERT INTO users (user_id, username, password_hash, school_id, role, full_name, email, phone, 
                      is_active_coordinator, first_login, created_at, updated_at)
    VALUES (?, ?, ?, ?, 'club_coordinator', ?, ?, ?, ?, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    """;

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            UUID userId = UUID.randomUUID();
            UUID schoolId = SessionManager.getCurrentSchoolId();

            if (schoolId == null) {
                showAlert(Alert.AlertType.ERROR, "Error", "No school session found. Please log in again.");
                return false;
            }

            String passwordHash = hashPassword(tempPassword);

            stmt.setObject(1, userId);
            stmt.setString(2, username.toLowerCase());
            stmt.setString(3, passwordHash);
            stmt.setObject(4, schoolId);
            stmt.setString(5, fullName);
            stmt.setString(6, email.toLowerCase());
            stmt.setString(7, phone);
            stmt.setBoolean(8, makeActive);

            int rowsAffected = stmt.executeUpdate();

            if (rowsAffected > 0) {
                // Store the temporary password for potential retrieval
                PasswordService.storeTempPassword(conn, userId, tempPassword);
                loadCoordinators();
                return true;
            }
            return false;

        } catch (SQLException e) {
            e.printStackTrace();
            handleDatabaseError(e);
            return false;
        }
    }

    private boolean hasActiveCoordinator() {
        String sql = "SELECT COUNT(*) FROM users WHERE school_id = ? AND role = 'club_coordinator' AND is_active_coordinator = TRUE";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            UUID schoolId = SessionManager.getCurrentSchoolId();
            if (schoolId == null) return false;

            stmt.setObject(1, schoolId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean isUsernameAvailable(String username) {
        UUID schoolId = SessionManager.getCurrentSchoolId();
        if (schoolId == null) {
            showAlert(Alert.AlertType.ERROR, "Error", "No school session found");
            return false;
        }

        String sql = "SELECT COUNT(*) FROM users WHERE username = ? AND school_id = ?";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username.toLowerCase());
            stmt.setObject(2, schoolId);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) == 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean isEmailAvailable(String email) {
        UUID schoolId = SessionManager.getCurrentSchoolId();
        if (schoolId == null) {
            showAlert(Alert.AlertType.ERROR, "Error", "No school session found");
            return false;
        }

        String sql = "SELECT COUNT(*) FROM users WHERE email = ? AND school_id = ?";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, email.toLowerCase());
            stmt.setObject(2, schoolId);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) == 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private String generateTemporaryPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        SecureRandom random = new SecureRandom();
        StringBuilder password = new StringBuilder();

        for (int i = 0; i < 8; i++) {
            password.append(chars.charAt(random.nextInt(chars.length())));
        }

        return password.toString();
    }

    private void handleDatabaseError(SQLException e) {
        String errorMessage;
        System.err.println("SQL Error: " + e.getMessage());
        System.err.println("SQL State: " + e.getSQLState());

        if (e.getMessage().contains("unique_lower_username")) {
            errorMessage = "Username already exists. Please choose a different username.";
        } else if (e.getMessage().contains("users_email_lower_idx")) {
            errorMessage = "Email already exists. Please use a different email address.";
        } else if (e.getMessage().contains("unique_phone")) {
            errorMessage = "Phone number already exists. Please use a different phone number.";
        } else {
            errorMessage = "Failed to create coordinator: " + e.getMessage();
        }
        showAlert(Alert.AlertType.ERROR, "Error", errorMessage);
    }

    @FXML
    private void handleActivateCoordinator() {
        if (!isCurrentUserActiveCoordinator()) {
            showAlert(Alert.AlertType.ERROR, "Permission Denied",
                    "Only active coordinators can activate other coordinators.");
            return;
        }

        User selected = coordinatorsTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            boolean success = updateCoordinatorStatus(selected.getId(), true);
            if (success) {
                showAlert(Alert.AlertType.INFORMATION, "Success", "Coordinator activated successfully");
                loadCoordinators();
            } else {
                showAlert(Alert.AlertType.ERROR, "Error", "Failed to activate coordinator");
            }
        }
    }

    @FXML
    private void handleDeactivateCoordinator() {
        if (!isCurrentUserActiveCoordinator()) {
            showAlert(Alert.AlertType.ERROR, "Permission Denied",
                    "Only active coordinators can deactivate other coordinators.");
            return;
        }

        User selected = coordinatorsTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            boolean success = updateCoordinatorStatus(selected.getId(), false);
            if (success) {
                showAlert(Alert.AlertType.INFORMATION, "Success", "Coordinator deactivated successfully");
                loadCoordinators();
            } else {
                showAlert(Alert.AlertType.ERROR, "Error", "Failed to deactivate coordinator");
            }
        }
    }

    @FXML
    private void handleRefresh() {
        loadCoordinators();
    }

    private boolean updateCoordinatorStatus(UUID userId, boolean makeActive) {
        try {
            String sql = "";
            UUID schoolId = SessionManager.getCurrentSchoolId();

            if (makeActive) {
                sql = """
                    BEGIN;
                    UPDATE users 
                    SET is_active_coordinator = FALSE 
                    WHERE school_id = ? 
                    AND role = 'club_coordinator'
                    AND is_active_coordinator = TRUE;
                    
                    UPDATE users 
                    SET is_active_coordinator = TRUE 
                    WHERE user_id = ? 
                    AND school_id = ? 
                    AND role = 'club_coordinator';
                    COMMIT;
                    """;

                try (Connection conn = DatabaseConnector.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {

                    stmt.setObject(1, schoolId);
                    stmt.setObject(2, userId);
                    stmt.setObject(3, schoolId);

                    stmt.executeUpdate();
                    return true;
                }
            } else {
                sql = "UPDATE users SET is_active_coordinator = FALSE WHERE user_id = ?";
                try (Connection conn = DatabaseConnector.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {

                    stmt.setObject(1, userId);
                    return stmt.executeUpdate() > 0;
                }
            }
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to update coordinator: " + e.getMessage());
            return false;
        }
    }

    private void showAlert(Alert.AlertType alertType, String title, String message) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // User model class
    public static class User {
        private final UUID id;
        private final String username;
        private final String fullName;
        private final String email;
        private final String phone;
        private final boolean active;
        private final Timestamp createdAt;

        public User(UUID id, String username, String fullName, String email, String phone, boolean active, Timestamp createdAt) {
            this.id = id;
            this.username = username;
            this.fullName = fullName;
            this.email = email;
            this.phone = phone;
            this.active = active;
            this.createdAt = createdAt;
        }

        public UUID getId() { return id; }
        public String getUsername() { return username; }
        public String getFullName() { return fullName; }
        public String getEmail() { return email; }
        public String getPhone() { return phone; }
        public boolean isActive() { return active; }
        public String getActiveStatus() { return active ? "Active" : "Inactive"; }
        public Timestamp getCreatedAt() { return createdAt; }
        public String getCreatedAtFormatted() {
            if (createdAt == null) return "N/A";
            return new SimpleDateFormat("MMM dd, yyyy").format(createdAt);
        }
    }
}