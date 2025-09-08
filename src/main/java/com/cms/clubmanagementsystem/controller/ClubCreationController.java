package com.cms.clubmanagementsystem.controller;

import com.cms.clubmanagementsystem.service.ClubService;
import com.cms.clubmanagementsystem.utils.DatabaseConnector;
import com.cms.clubmanagementsystem.utils.SessionManager;
import com.cms.clubmanagementsystem.utils.TransactionUtils;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.stage.Stage;

import java.net.URL;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ResourceBundle;
import java.util.UUID;

public class ClubCreationController implements Initializable {

    @FXML private TextField clubNameField;
    @FXML private TextArea descriptionArea;
    @FXML private Button submitButton;

    private final ClubService clubService = new ClubService();
    private final UUID schoolId = SessionManager.getCurrentSchoolId();
    private final UUID coordinatorId = SessionManager.getCurrentUserId();

    private ClubService.Club clubForEdit;
    private boolean isEditMode = false;
    private boolean isActiveCoordinator = false;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        checkCoordinatorPermissions();
        setupInputValidation();
        updateUIForPermissions();
    }

    private void checkCoordinatorPermissions() {
        isActiveCoordinator = SessionManager.isActiveCoordinator();
    }

    private void updateUIForPermissions() {
        if (!isActiveCoordinator) {
            submitButton.setDisable(true);
            submitButton.setTooltip(new Tooltip("Only active coordinators can create or update clubs"));
            clubNameField.setDisable(true);
            descriptionArea.setDisable(true);

            // Show message if user doesn't have permission
            showAlert("Access Denied", "Only active coordinators can create or update clubs.");
        } else {
            submitButton.setDisable(false);
            submitButton.setTooltip(null);
            clubNameField.setDisable(false);
            descriptionArea.setDisable(false);
        }
    }

    private void setupInputValidation() {
        // Limit club name length
        clubNameField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.length() > 100) {
                clubNameField.setText(oldValue);
            }
        });

        // Limit description length
        descriptionArea.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.length() > 500) {
                descriptionArea.setText(oldValue);
            }
        });
    }

    public void setClubForEdit(ClubService.Club club) {
        // Check if coordinator has edit permissions
        if (!SessionManager.isActiveCoordinator()) {
            showAlert("Access Denied", "Only active coordinators can update clubs.");
            return;
        }

        this.clubForEdit = club;
        this.isEditMode = true;
        populateFormWithClubData();
        updateButtonText();
    }

    private void populateFormWithClubData() {
        clubNameField.setText(clubForEdit.getClubName());
        descriptionArea.setText(clubForEdit.getDescription());
    }

    private void updateButtonText() {
        if (isEditMode) {
            submitButton.setText("Update Club");
        } else {
            submitButton.setText("Create Club");
        }
    }

    @FXML
    private void handleSubmit() {
        // Check if coordinator has edit permissions
        if (!isActiveCoordinator) {
            showAlert("Access Denied", "Only active coordinators can create or update clubs.");
            return;
        }

        String clubName = clubNameField.getText().trim();
        String description = descriptionArea.getText().trim();

        // Validation
        if (clubName.isEmpty()) {
            showAlert("Validation Error", "Club name is required.");
            clubNameField.requestFocus();
            return;
        }

        try (Connection conn = DatabaseConnector.getConnection()) {
            TransactionUtils.executeInTransaction(conn, connection -> {
                if (isEditMode) {
                    updateClub(connection, clubName, description);
                } else {
                    createClub(connection, clubName, description);
                }
                return null;
            });

        } catch (SQLException e) {
            showAlert("Database Error", "Failed to save club: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void createClub(Connection connection, String clubName, String description) {
        try {
            // Check for existing club
            if (clubService.clubExists(connection, clubName, schoolId)) {
                if (clubService.activeClubExists(connection, clubName, schoolId)) {
                    throw new SQLException("An active club with this name already exists.");
                } else {
                    // Offer to reactivate instead of create new
                    if (showConfirmation("Reactivate Club",
                            "An inactive club with this name exists. Would you like to reactivate it?")) {
                        clubService.reactivateClub(connection, clubName, schoolId);
                        ClubService.Club club = clubService.getClubByName(connection, clubName, schoolId);
                        navigateToScheduling(club);
                        return;
                    } else {
                        return; // User chose not to reactivate
                    }
                }
            }

            // Create new club
            UUID clubId = clubService.createClub(connection, clubName, description, coordinatorId, schoolId);
            ClubService.Club newClub = clubService.getClubById(connection, clubId);
            navigateToScheduling(newClub);

        } catch (SQLException e) {
            throw new RuntimeException("Failed to create club: " + e.getMessage(), e);
        }
    }

    private void updateClub(Connection connection, String clubName, String description) {
        try {
            // For simplicity, using default values for parameters not in the form
            String currentMeetingDay = "MON";
            String currentMeetingTime = "14:00";
            String currentVenue = "TBD";

            clubService.updateClub(connection, clubForEdit.getClubId(), clubName, description,
                    currentMeetingDay, currentMeetingTime, currentVenue);

            // Refresh the club object
            clubForEdit = clubService.getClubById(connection, clubForEdit.getClubId());
            navigateToScheduling(clubForEdit);

        } catch (SQLException e) {
            throw new RuntimeException("Failed to update club: " + e.getMessage(), e);
        }
    }

    private void navigateToScheduling(ClubService.Club club) {
        try {
            URL fxmlUrl = getClass().getResource("/fxml/club-scheduling.fxml");
            if (fxmlUrl == null) {
                showAlert("Error", "Scheduling form not found. Please check the file path.");
                return;
            }

            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent root = loader.load();

            ClubSchedulingController schedulingController = loader.getController();
            schedulingController.setClub(club);

            Stage schedulingStage = new Stage();
            schedulingStage.setScene(new Scene(root));
            schedulingStage.setTitle("Club Scheduling - " + club.getClubName());
            schedulingStage.show();

            // Close the current window
            Stage currentStage = (Stage) clubNameField.getScene().getWindow();
            currentStage.close();

        } catch (Exception e) {
            showAlert("Error", "Failed to load scheduling screen: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void handleCancel() {
        Stage stage = (Stage) clubNameField.getScene().getWindow();
        stage.close();
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private boolean showConfirmation(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        return alert.showAndWait().orElse(null) == ButtonType.OK;
    }
}