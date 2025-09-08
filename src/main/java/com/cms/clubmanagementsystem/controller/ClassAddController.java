package com.cms.clubmanagementsystem.controller;

import com.cms.clubmanagementsystem.service.ClubService;
import com.cms.clubmanagementsystem.utils.DatabaseConnector;
import com.cms.clubmanagementsystem.utils.SessionManager;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ResourceBundle;
import java.util.UUID;

public class ClassAddController implements Initializable {

    @FXML private TextField classNameField;

    private UUID schoolId;
    private final ClubService clubService = new ClubService();
    private boolean isActiveCoordinator = false; // NEW: Track coordinator status

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        schoolId = SessionManager.getCurrentSchoolId();
        checkCoordinatorPermissions(); // NEW: Check permissions
        updateUIForPermissions(); // NEW: Update UI based on permissions
    }

    // NEW: Check coordinator permissions
    private void checkCoordinatorPermissions() {
        isActiveCoordinator = SessionManager.isActiveCoordinator();
    }

    // NEW: Update UI based on permissions
    private void updateUIForPermissions() {
        if (!isActiveCoordinator) {
            classNameField.setDisable(true);
            classNameField.setTooltip(new Tooltip("Only active coordinators can add classes"));

            // Show message if user doesn't have permission
            showAlert("Access Denied", "Only active coordinators can add classes.");
        } else {
            classNameField.setDisable(false);
            classNameField.setTooltip(null);
        }
    }

    @FXML
    private void handleSave() {
        // NEW: Check if coordinator has edit permissions
        if (!isActiveCoordinator) {
            showAlert("Access Denied", "Only active coordinators can add classes.");
            return;
        }

        String className = classNameField.getText().trim();

        if (className.isEmpty()) {
            showAlert("Validation Error", "Please enter a class name.");
            return;
        }

        try (Connection conn = DatabaseConnector.getConnection()) {
            // Check if class already exists
            if (classExists(conn, className)) {
                showAlert("Duplicate Class", "A class with this name already exists.");
                return;
            }

            // Insert new class
            String sql = "INSERT INTO class_groups (group_name, school_id) VALUES (?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, className);
                stmt.setObject(2, schoolId);
                stmt.executeUpdate();
            }

            showAlert("Success", "Class added successfully!");
            closeWindow();

        } catch (SQLException e) {
            showAlert("Database Error", "Failed to add class: " + e.getMessage());
        }
    }

    private boolean classExists(Connection conn, String className) throws SQLException {
        String sql = "SELECT COUNT(*) FROM class_groups WHERE group_name = ? AND school_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, className);
            stmt.setObject(2, schoolId);
            try (var rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    @FXML
    private void handleCancel() {
        closeWindow();
    }

    private void closeWindow() {
        Stage stage = (Stage) classNameField.getScene().getWindow();
        stage.close();
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}