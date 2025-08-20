package com.cms.clubmanagementsystem.controller;

import com.cms.clubmanagementsystem.utils.DatabaseConnector;
import com.cms.clubmanagementsystem.utils.TenantContext;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import com.google.i18n.phonenumbers.NumberParseException;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.mindrot.jbcrypt.BCrypt;

import java.io.IOException;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RegisterController {
    // Form fields
    @FXML private TextField fullNameField;
    @FXML private TextField emailField;
    @FXML private TextField usernameField;
    @FXML private TextField phoneField;
    @FXML private ComboBox<String> roleComboBox;
    @FXML private ComboBox<String> schoolComboBox;
    @FXML private Label messageLabel;

    // Password fields
    @FXML private PasswordField passwordField;
    @FXML private TextField passwordVisibleField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private TextField confirmPasswordVisibleField;
    @FXML private CheckBox showPasswordCheckBox;

    //Select Club
    @FXML private ComboBox<String> clubComboBox;
    @FXML private VBox clubSelectionBox;

    private final Map<String, UUID> clubMap = new HashMap<>();

    private final Map<String, UUID> schoolMap = new HashMap<>();
    private final PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();

    @FXML
    public void initialize() {
        phoneField.setPromptText("International format (e.g., +1 212 555 1234)");
        clubSelectionBox.setVisible(false); // Hide initially
        loadRoles();
        loadSchools();
    }

    private void loadRoles() {
        try (Connection conn = DatabaseConnector.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT unnest(enum_range(NULL::user_role)) AS role")) {

            roleComboBox.getItems().clear();
            while (rs.next()) {
                roleComboBox.getItems().add(rs.getString("role"));
            }

            roleComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
                onRoleSelected(newVal);
            });

        } catch (SQLException e) {
            roleComboBox.getItems().addAll("coordinator", "teacher");
            e.printStackTrace();
        }
    }

    private void onRoleSelected(String role) {
        if ("teacher".equals(role)) {
            clubSelectionBox.setVisible(true);
            loadAvailableClubs();
        } else {
            clubSelectionBox.setVisible(false);
        }
    }

    private void loadAvailableClubs() {
        clubComboBox.getItems().clear();
        clubMap.clear();

        UUID schoolId = getSelectedSchoolId();
        if (schoolId == null) {
            showError("Please select a school first");
            return;
        }

        try (Connection conn = DatabaseConnector.getConnection()) {
            // Use the SECURITY DEFINER function instead of setting tenant context
            String sql = "SELECT * FROM get_school_clubs(?)";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, schoolId);
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    String clubName = rs.getString("club_name");
                    UUID clubId = (UUID) rs.getObject("club_id");
                    if (clubName != null && clubId != null) {
                        clubMap.put(clubName, clubId);
                        clubComboBox.getItems().add(clubName);
                    }
                }

                if (clubComboBox.getItems().isEmpty()) {
                    showError("No clubs available for this school");
                }
            }
        } catch (SQLException e) {
            showError("Failed to load clubs: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private UUID getSelectedSchoolId() {
        if (schoolComboBox.getValue() == null) {
            showError("Please select a school first");
            return null;
        }
        UUID schoolId = schoolMap.get(schoolComboBox.getValue());
        if (schoolId == null) {
            showError("Invalid school selection");
            return null;
        }
        return schoolId;
    }

    private void loadSchools() {
        schoolComboBox.getItems().clear();
        schoolMap.clear();

        try (Connection conn = DatabaseConnector.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT school_id, school_name FROM schools")) {

            while (rs.next()) {
                String schoolName = rs.getString("school_name");
                UUID schoolId = (UUID) rs.getObject("school_id");
                if (schoolName != null && schoolId != null) {
                    schoolMap.put(schoolName, schoolId);
                    schoolComboBox.getItems().add(schoolName);
                }
            }

            // Add listener for school changes to reload clubs
            schoolComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
                if ("teacher".equals(roleComboBox.getValue())) {
                    loadAvailableClubs();
                }
            });

        } catch (SQLException e) {
            showError("Failed to load schools");
            e.printStackTrace();
        }
    }

    @FXML
    private void handleShowPassword(ActionEvent event) {
        boolean show = showPasswordCheckBox.isSelected();

        if (show) {
            passwordVisibleField.setText(passwordField.getText());
            confirmPasswordVisibleField.setText(confirmPasswordField.getText());
        } else {
            passwordField.setText(passwordVisibleField.getText());
            confirmPasswordField.setText(confirmPasswordVisibleField.getText());
        }

        togglePasswordVisibility(show);
        (show ? passwordVisibleField : passwordField).requestFocus();
    }

    private void togglePasswordVisibility(boolean show) {
        passwordField.setVisible(!show);
        passwordField.setManaged(!show);
        passwordVisibleField.setVisible(show);
        passwordVisibleField.setManaged(show);

        confirmPasswordField.setVisible(!show);
        confirmPasswordField.setManaged(!show);
        confirmPasswordVisibleField.setVisible(show);
        confirmPasswordVisibleField.setManaged(show);
    }

    @FXML
    private void handleRegistration() {
        try {
            String password = getPassword();
            String confirmPassword = getConfirmPassword();

            if (!validateInputs(password, confirmPassword)) return;

            UUID schoolId = validateAndGetSchoolId();
            if (schoolId == null) return;

            String role = validateAndGetRole();
            if (role == null) return;

            registerUser(
                    fullNameField.getText().trim(),
                    emailField.getText().trim(),
                    usernameField.getText().trim().toLowerCase(),
                    password,
                    phoneField.getText().trim(),
                    schoolId,
                    role
            );

            showSuccess("Registration successful!");
            clearForm();
        } catch (IllegalArgumentException | SQLException e) {
            handleError(e);
        }
    }

    private String getPassword() {
        return showPasswordCheckBox.isSelected() ?
                passwordVisibleField.getText() : passwordField.getText();
    }

    private String getConfirmPassword() {
        return showPasswordCheckBox.isSelected() ?
                confirmPasswordVisibleField.getText() : confirmPasswordField.getText();
    }

    private UUID validateAndGetSchoolId() {
        if (schoolComboBox.getValue() == null) {
            showError("Please select a school");
            return null;
        }
        UUID schoolId = schoolMap.get(schoolComboBox.getValue());
        if (schoolId == null) {
            showError("Invalid school selection");
        }
        return schoolId;
    }

    private String validateAndGetRole() {
        String role = roleComboBox.getValue();
        if (role == null || !roleComboBox.getItems().contains(role)) {
            showError("Invalid role selected");
            return null;
        }
        return role;
    }

    private boolean validateInputs(String password, String confirmPassword) {
        clearFieldHighlights();

        if (fullNameField.getText().trim().isEmpty() ||
                emailField.getText().trim().isEmpty() ||
                usernameField.getText().trim().isEmpty() ||
                password.trim().isEmpty() ||
                phoneField.getText().trim().isEmpty()) {
            showError("All fields are required");
            return false;
        }

        if (!emailField.getText().matches("^[\\w-.]+@([\\w-]+\\.)+[\\w-]{2,4}$")) {
            showError("Invalid email format");
            return false;
        }

        try {
            String formattedPhone = validateAndFormatPhone(phoneField.getText().trim());
            phoneField.setText(formattedPhone);
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
            phoneField.setStyle("-fx-border-color: red;");
            return false;
        }

        if (!password.equals(confirmPassword)) {
            showError("Passwords don't match");
            return false;
        }

        if (!isPasswordStrong(password)) {
            showError("Password must contain:\n- 8-64 characters\n- 1 uppercase letter\n- 1 lowercase letter\n- 1 number\n- 1 special character");
            return false;
        }

        // Club validation for teachers
        String role = roleComboBox.getValue();
        if ("teacher".equals(role) && clubComboBox.getValue() == null) {
            showError("Please select a club for teacher registration");
            return false;
        }

        return true;

    }

    private String validateAndFormatPhone(String phoneNumber) throws IllegalArgumentException {
        try {
            Phonenumber.PhoneNumber number = phoneUtil.parse(phoneNumber, null);
            if (!phoneUtil.isValidNumber(number)) {
                throw new IllegalArgumentException("Invalid phone number");
            }
            return phoneUtil.format(number, PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (NumberParseException e) {
            throw new IllegalArgumentException("Invalid phone number format: " + e.getMessage());
        }
    }

    private boolean isPasswordStrong(String password) {
        // Check length (8-64 characters)
        if (password.length() < 8 || password.length() > 64) {
            return false;
        }

        // Check for at least one of each required character type
        boolean hasUpper = !password.equals(password.toLowerCase());
        boolean hasLower = !password.equals(password.toUpperCase());
        boolean hasDigit = password.matches(".*\\d.*");
        boolean hasSpecial = password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*");

        return hasUpper && hasLower && hasDigit && hasSpecial;
    }

    private void registerUser(String fullName, String email, String username,
                              String password, String phone, UUID schoolId, String role)
            throws IllegalArgumentException, SQLException {

        UUID clubId = null;
        if ("teacher".equals(role)) {
            clubId = clubMap.get(clubComboBox.getValue());
            if (clubId == null) {
                throw new IllegalArgumentException("Invalid club selection");
            }
        }

        try (Connection conn = DatabaseConnector.getConnection()) {
            conn.setAutoCommit(false);

            try {

                if (isUsernameTaken(conn, username)) {
                    throw new IllegalArgumentException("Username already taken");
                }
                if (isEmailTaken(conn, email)) {
                    throw new IllegalArgumentException("Email already registered");
                }
                if (isPhoneTaken(conn, phone)) {
                    throw new IllegalArgumentException("Phone number already registered");
                }

                // Use the SECURITY DEFINER function to bypass RLS safely
                String sql = "SELECT register_user(?, ?, ?, ?::user_role, ?, ?, ?, ?)";

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, username);
                    stmt.setString(2, BCrypt.hashpw(password, BCrypt.gensalt(12)));
                    stmt.setObject(3, schoolId);
                    stmt.setString(4, role); // This gets cast to user_role by PostgreSQL
                    stmt.setString(5, fullName);
                    stmt.setString(6, email);
                    stmt.setString(7, phone);
                    stmt.setObject(8, clubId); // This can be NULL for coordinators

                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            UUID newUserId = (UUID) rs.getObject(1);
                            System.out.println("Registered new user with ID: " + newUserId);
                        }
                    }
                }
                conn.commit();
            } catch (SQLException | IllegalArgumentException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private boolean isUsernameTaken(Connection conn, String username) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT EXISTS (SELECT 1 FROM users WHERE username = ?)")) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    private boolean isEmailTaken(Connection conn, String email) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT EXISTS (SELECT 1 FROM users WHERE email = ?)")) {
            stmt.setString(1, email);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    private boolean isPhoneTaken(Connection conn, String phone) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT EXISTS (SELECT 1 FROM users WHERE phone = ?)")) {
            stmt.setString(1, phone);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    private void handleError(Exception e) {
        if (e instanceof IllegalArgumentException) {
            handleValidationError((IllegalArgumentException) e);
        } else if (e instanceof SQLException) {
            handleDatabaseError((SQLException) e);
        }
    }

    private void handleValidationError(IllegalArgumentException e) {
        if (e.getMessage().contains("Username")) {
            usernameField.setStyle("-fx-border-color: red;");
        } else if (e.getMessage().contains("Email")) {
            emailField.setStyle("-fx-border-color: red;");
        } else if (e.getMessage().contains("Phone")) {
            phoneField.setStyle("-fx-border-color: red;");
        }
        showErrorAlert(e.getMessage());
    }

    private void handleDatabaseError(SQLException e) {
        showErrorAlert("Database error during registration");
        e.printStackTrace();
    }

    private void showErrorAlert(String message) {
        new Alert(Alert.AlertType.ERROR, message).showAndWait();
    }

    private void clearForm() {
        fullNameField.clear();
        emailField.clear();
        usernameField.clear();
        phoneField.clear();
        roleComboBox.getSelectionModel().clearSelection();
        schoolComboBox.getSelectionModel().clearSelection();

        passwordField.clear();
        confirmPasswordField.clear();
        passwordVisibleField.clear();
        confirmPasswordVisibleField.clear();
        showPasswordCheckBox.setSelected(false);

        clearFieldHighlights();
        togglePasswordVisibility(false);
    }

    private void clearFieldHighlights() {
        usernameField.setStyle("");
        emailField.setStyle("");
        phoneField.setStyle("");
    }

    private void showError(String message) {
        messageLabel.setStyle("-fx-text-fill: red;");
        messageLabel.setText(message);
    }

    private void showSuccess(String message) {
        messageLabel.setStyle("-fx-text-fill: green;");
        messageLabel.setText(message);
    }

    @FXML
    private void handleBackToLogin() {
        try {
            Stage stage = (Stage) emailField.getScene().getWindow();
            Parent root = FXMLLoader.load(getClass().getResource("/fxml/login.fxml"));
            stage.setScene(new Scene(root));
            stage.setTitle("Login");
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Failed to load login screen.");
        }
    }

    private void showAlert(Alert.AlertType type, String message) {
        Alert alert = new Alert(type);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}