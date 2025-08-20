package com.cms.clubmanagementsystem.controller;

import com.cms.clubmanagementsystem.service.ClubService;
import com.cms.clubmanagementsystem.utils.DatabaseConnector;
import com.cms.clubmanagementsystem.utils.SessionManager;
import com.cms.clubmanagementsystem.utils.TenantContext;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.net.URL;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ResourceBundle;
import java.util.UUID;

public class ClubManagementController implements Initializable {

    @FXML private TextField clubNameField;
    @FXML private TextArea descriptionArea;
    @FXML private ComboBox<String> meetingDayCombo;
    @FXML private TextField meetingTimeField;
    @FXML private TextField venueField;
    @FXML private Spinner<Integer> capacitySpinner;
    @FXML private TableView<ClubService.Club> clubsTable;
    @FXML private TableColumn<ClubService.Club, String> nameColumn;
    @FXML private TableColumn<ClubService.Club, String> descriptionColumn;
    @FXML private TableColumn<ClubService.Club, String> meetingColumn;
    @FXML private TableColumn<ClubService.Club, String> venueColumn;
    @FXML private TableColumn<ClubService.Club, Integer> capacityColumn;

    private final ObservableList<ClubService.Club> clubs = FXCollections.observableArrayList();
    private UUID currentSchoolId;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        currentSchoolId = SessionManager.getCurrentSchoolId();
        setupUI();
        loadClubs();

    }

    private void setupUI() {
        // Setup meeting days (matches your ENUM type)
        meetingDayCombo.setItems(FXCollections.observableArrayList(
                "MON", "TUE", "WED", "THUR", "FRI", "SAT", "SUN"
        ));

        // Setup capacity spinner (1-1000, respecting your CHECK constraint)
        capacitySpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 1000, 30));

        // Setup table columns
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("clubName"));
        descriptionColumn.setCellValueFactory(new PropertyValueFactory<>("description"));
        meetingColumn.setCellValueFactory(cellData -> {
            ClubService.Club club = cellData.getValue();
            return new javafx.beans.property.SimpleStringProperty(
                    club.getMeetingDay() + " " + club.getMeetingTime()
            );
        });
        venueColumn.setCellValueFactory(new PropertyValueFactory<>("venue"));
        capacityColumn.setCellValueFactory(new PropertyValueFactory<>("capacity"));

        clubsTable.setItems(clubs);

        // Add real-time time format validation (HH:MM)
        meetingTimeField.textProperty().addListener((observable, oldValue, newValue) -> {
            // Allow empty or partial input while typing
            try {
                if (!newValue.isEmpty() && !newValue.matches("^([0-1]?[0-9]?|2[0-3]?)?(:[0-5]?[0-9]?)?$")) {
                    meetingTimeField.setText(oldValue);
                }
            } catch (Exception e) {
                System.err.println("Validation error: " + e.getMessage());
            }
        });
    }

    @FXML
    private void handleCreateClub() {
        String clubName = clubNameField.getText().trim();
        String description = descriptionArea.getText().trim();
        String meetingDay = meetingDayCombo.getValue();
        String meetingTime = meetingTimeField.getText().trim();
        String venue = venueField.getText().trim();
        int capacity = capacitySpinner.getValue();

        // Validation
        if (clubName.isEmpty() || meetingDay == null || meetingTime.isEmpty() || venue.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Please fill in all required fields (marked with *).");
            return;
        }

        // Validate time format (HH:MM)
        if (!meetingTime.matches("^([0-1][0-9]|2[0-3]):[0-5][0-9]$")) {
            showAlert(Alert.AlertType.ERROR, "Please enter time in HH:MM format (e.g., 14:30).");
            return;
        }

        // Validate capacity (should be handled by spinner, but double-check)
        if (capacity <= 0 || capacity > 1000) {
            showAlert(Alert.AlertType.ERROR, "Capacity must be between 1 and 1000.");
            return;
        }

        try (Connection conn = DatabaseConnector.getConnection()) {
            // Set tenant context for RLS
            UUID currentUserId = SessionManager.getCurrentUserId();
            if (currentUserId != null) {
                TenantContext.setTenant(conn, currentSchoolId.toString(), currentUserId.toString());
            } else {
                showAlert(Alert.AlertType.ERROR, "User session expired. Please log in again.");
                return;
            }

            ClubService clubService = new ClubService();

            // Check if club already exists in this school
            if (clubService.clubExists(conn, clubName, currentSchoolId)) {
                showAlert(Alert.AlertType.ERROR, "A club with this name already exists in your school.");
                return;
            }

            // Create club
            boolean success = clubService.createClub(conn, clubName, description, meetingDay,
                    meetingTime, venue, currentSchoolId, capacity);

            if (success) {
                showAlert(Alert.AlertType.INFORMATION, "Club created successfully!");
                clearForm();
                loadClubs();
            } else {
                showAlert(Alert.AlertType.ERROR, "Failed to create club. Please try again.");
            }

        } catch (SQLException e) {
            handleDatabaseError(e, "create club");
        }
    }

    @FXML
    private void handleUpdateClub() {
        ClubService.Club selectedClub = clubsTable.getSelectionModel().getSelectedItem();
        if (selectedClub == null) {
            showAlert(Alert.AlertType.WARNING, "Please select a club to update.");
            return;
        }

        // Populate form with selected club data
        clubNameField.setText(selectedClub.getClubName());
        descriptionArea.setText(selectedClub.getDescription());
        meetingDayCombo.setValue(selectedClub.getMeetingDay());
        meetingTimeField.setText(selectedClub.getMeetingTime());
        venueField.setText(selectedClub.getVenue());
        capacitySpinner.getValueFactory().setValue(selectedClub.getCapacity());
    }

    @FXML
    private void handleSaveUpdate() {
        ClubService.Club selectedClub = clubsTable.getSelectionModel().getSelectedItem();
        if (selectedClub == null) {
            showAlert(Alert.AlertType.WARNING, "Please select a club to update.");
            return;
        }

        String clubName = clubNameField.getText().trim();
        String description = descriptionArea.getText().trim();
        String meetingDay = meetingDayCombo.getValue();
        String meetingTime = meetingTimeField.getText().trim();
        String venue = venueField.getText().trim();
        int capacity = capacitySpinner.getValue();

        // Validation (same as create)
        if (clubName.isEmpty() || meetingDay == null || meetingTime.isEmpty() || venue.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Please fill in all required fields.");
            return;
        }

        if (!meetingTime.matches("^([0-1][0-9]|2[0-3]):[0-5][0-9]$")) {
            showAlert(Alert.AlertType.ERROR, "Please enter time in HH:MM format (e.g., 14:30).");
            return;
        }

        try (Connection conn = DatabaseConnector.getConnection()) {
            UUID currentUserId = SessionManager.getCurrentUserId();
            if (currentUserId != null) {
                TenantContext.setTenant(conn, currentSchoolId.toString(), currentUserId.toString());
            } else {
                showAlert(Alert.AlertType.ERROR, "User session expired. Please log in again.");
                return;
            }

            ClubService clubService = new ClubService();

            // Check if club name conflicts (excluding current club)
            if (!clubName.equals(selectedClub.getClubName())) {
                if (clubService.clubExists(conn, clubName, currentSchoolId)) {
                    showAlert(Alert.AlertType.ERROR, "A club with this name already exists in your school.");
                    return;
                }
            }

            // Update club
            boolean success = clubService.updateClub(conn, selectedClub.getClubId(), clubName,
                    description, meetingDay, meetingTime, venue, capacity);

            if (success) {
                showAlert(Alert.AlertType.INFORMATION, "Club updated successfully!");
                clearForm();
                loadClubs();
                clubsTable.getSelectionModel().clearSelection();
            } else {
                showAlert(Alert.AlertType.ERROR, "Failed to update club. Please try again.");
            }

        } catch (SQLException e) {
            handleDatabaseError(e, "update club");
        }
    }

    @FXML
    private void handleDeleteClub() {
        ClubService.Club selectedClub = clubsTable.getSelectionModel().getSelectedItem();
        if (selectedClub == null) {
            showAlert(Alert.AlertType.WARNING, "Please select a club to delete.");
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirm Deletion");
        confirmation.setHeaderText("Delete Club");
        confirmation.setContentText("Are you sure you want to delete '" + selectedClub.getClubName() + "'? This action cannot be undone.");

        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try (Connection conn = DatabaseConnector.getConnection()) {
                    UUID currentUserId = SessionManager.getCurrentUserId();
                    if (currentUserId != null) {
                        TenantContext.setTenant(conn, currentSchoolId.toString(), currentUserId.toString());
                    } else {
                        showAlert(Alert.AlertType.ERROR, "User session expired. Please log in again.");
                        return;
                    }

                    ClubService clubService = new ClubService();
                    boolean success = clubService.deactivateClub(conn, selectedClub.getClubId());

                    if (success) {
                        showAlert(Alert.AlertType.INFORMATION, "Club deleted successfully!");
                        loadClubs();
                    } else {
                        showAlert(Alert.AlertType.ERROR, "Failed to delete club. Please try again.");
                    }

                } catch (SQLException e) {
                    handleDatabaseError(e, "delete club");
                }
            }
        });
    }

    @FXML
    private void handleClearForm() {
        clearForm();
        clubsTable.getSelectionModel().clearSelection();
    }

    @FXML
    private void handleRefresh() {
        loadClubs();
    }

    private void loadClubs() {
        clubs.clear();
        try (Connection conn = DatabaseConnector.getConnection()) {
            UUID currentUserId = SessionManager.getCurrentUserId();
            if (currentUserId != null) {
                TenantContext.setTenant(conn, currentSchoolId.toString(), currentUserId.toString());
            } else {
                showAlert(Alert.AlertType.ERROR, "User session expired. Please log in again.");
                return;
            }

            ClubService clubService = new ClubService();
            clubs.addAll(clubService.getClubsBySchool(conn, currentSchoolId));

        } catch (SQLException e) {
            handleDatabaseError(e, "load clubs");
        }
    }

    private void clearForm() {
        clubNameField.clear();
        descriptionArea.clear();
        meetingDayCombo.setValue(null);
        meetingTimeField.clear();
        venueField.clear();
        capacitySpinner.getValueFactory().setValue(30);
    }

    private void handleDatabaseError(SQLException e, String operation) {
        // Check for unique constraint violation
        if (e.getMessage().contains("unique constraint") && e.getMessage().contains("club_name")) {
            showAlert(Alert.AlertType.ERROR, "A club with this name already exists in the system.");
        } else if (e.getMessage().contains("check constraint") && e.getMessage().contains("capacity")) {
            showAlert(Alert.AlertType.ERROR, "Capacity must be greater than 0.");
        } else {
            showAlert(Alert.AlertType.ERROR, "Failed to " + operation + ": " + e.getMessage());
        }
        e.printStackTrace();
    }

    private void showAlert(Alert.AlertType type, String message) {
        Alert alert = new Alert(type);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

}