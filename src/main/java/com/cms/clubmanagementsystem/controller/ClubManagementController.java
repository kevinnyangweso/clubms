package com.cms.clubmanagementsystem.controller;

import com.cms.clubmanagementsystem.service.ClubService;
import com.cms.clubmanagementsystem.utils.DatabaseConnector;
import com.cms.clubmanagementsystem.utils.SessionManager;
import com.cms.clubmanagementsystem.utils.TenantContext;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.UUID;

public class ClubManagementController implements Initializable {

    @FXML private TableView<ClubService.Club> clubsTable;
    @FXML private TableColumn<ClubService.Club, String> nameColumn;
    @FXML private TableColumn<ClubService.Club, String> descriptionColumn;
    @FXML private VBox mainContainer;
    @FXML private Button createButton;
    @FXML private Button updateButton;
    @FXML private Button addScheduleButton;
    @FXML private Button deleteButton;
    @FXML private Button viewSchedulesButton;
    @FXML private Label infoLabel;

    private final ObservableList<ClubService.Club> clubs = FXCollections.observableArrayList();
    private UUID currentSchoolId;
    private boolean isActiveCoordinator = false;
    private boolean isCoordinator = false; // NEW: Track if user is any type of coordinator

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        currentSchoolId = SessionManager.getCurrentSchoolId();
        checkCoordinatorPermissions();

        // Check if user has at least coordinator access
        if (!isCoordinator) {
            showAlert("Access Denied", "You do not have coordinator permissions to access club management.");
            // Optionally disable the entire UI or close the window
            mainContainer.setDisable(true);
            return;
        }

        Platform.runLater(() -> {
            setupUI();
            loadClubs();
            applyStyles();
            updateUIForPermissions();
            updateInfoLabel();
        });
    }

    private void updateInfoLabel() {
        if (isActiveCoordinator) {
            infoLabel.setText("Select a club to view schedules, update, or delete.");
        } else if (isCoordinator) {
            infoLabel.setText("Select a club to view schedules.");
        } else {
            infoLabel.setText("No club management permissions available.");
        }
    }

    private void checkCoordinatorPermissions() {
        isCoordinator = SessionManager.isCoordinator(); // NEW: Check if user is any coordinator
        isActiveCoordinator = SessionManager.isActiveCoordinator(); // Existing check
    }

    private void updateUIForPermissions() {
        // Disable edit buttons for non-active coordinators
        createButton.setDisable(!isActiveCoordinator);
        updateButton.setDisable(!isActiveCoordinator);
        addScheduleButton.setDisable(!isActiveCoordinator);
        deleteButton.setDisable(!isActiveCoordinator);

        // Allow view schedules for any coordinator (active or inactive)
        viewSchedulesButton.setDisable(!isCoordinator);

        if (!isActiveCoordinator) {
            Tooltip permissionTooltip = new Tooltip("Only active coordinators can perform this action");
            createButton.setTooltip(permissionTooltip);
            updateButton.setTooltip(permissionTooltip);
            addScheduleButton.setTooltip(permissionTooltip);
            deleteButton.setTooltip(permissionTooltip);

            if (!isCoordinator) {
                Tooltip viewTooltip = new Tooltip("Coordinator access required to view schedules");
                viewSchedulesButton.setTooltip(viewTooltip);
            } else {
                viewSchedulesButton.setTooltip(null);
            }
        } else {
            createButton.setTooltip(null);
            updateButton.setTooltip(null);
            addScheduleButton.setTooltip(null);
            deleteButton.setTooltip(null);
            viewSchedulesButton.setTooltip(null);
        }

        // Update the info label when permissions change
        updateInfoLabel();
    }

    private void setupUI() {
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("clubName"));
        descriptionColumn.setCellValueFactory(new PropertyValueFactory<>("description"));

        // Disable sorting to prevent unexpected reordering
        nameColumn.setSortable(false);
        descriptionColumn.setSortable(false);

        clubsTable.setItems(clubs);
        nameColumn.setPrefWidth(200);
        descriptionColumn.setPrefWidth(300);
    }

    private void forceAllRowsVisible() {
        // CRUCIAL: This fixes the JavaFX TableView rendering bug
        Set<Node> allRows = clubsTable.lookupAll(".table-row-cell");
        for (Node node : allRows) {
            if (node instanceof TableRow) {
                TableRow<?> row = (TableRow<?>) node;
                row.setVisible(true);
                row.setManaged(true);
                row.setStyle(""); // Clear any hiding styles
            }
        }
        clubsTable.requestLayout();
    }

    @FXML
    private void showClubCreationForm() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/club-creation.fxml"));
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("Create New Club");
            stage.setScene(new Scene(root, 900, 650));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(createButton.getScene().getWindow());
            stage.showAndWait();

            loadClubs();

        } catch (IOException e) {
            showAlert("Error", "Failed to load club creation form: " + e.getMessage());
        }
    }

    @FXML
    private void handleUpdateClub() {
        if (!isActiveCoordinator) {
            showPermissionError();
            return;
        }

        ClubService.Club selectedClub = clubsTable.getSelectionModel().getSelectedItem();
        if (selectedClub == null) {
            showAlert("Selection Required", "Please select a club to update.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/club-creation.fxml"));
            Parent root = loader.load();

            ClubCreationController controller = loader.getController();
            controller.setClubForEdit(selectedClub);

            Stage stage = new Stage();
            stage.setTitle("Update Club: " + selectedClub.getClubName());
            stage.setScene(new Scene(root, 900, 650));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(updateButton.getScene().getWindow());
            stage.showAndWait();

            loadClubs();

        } catch (IOException e) {
            showAlert("Error", "Failed to load club editor: " + e.getMessage());
        }
    }

    // In ClubManagementController.java

    @FXML
    private void showAddScheduleForm() {
        // Check if coordinator has edit permissions
        if (!SessionManager.isActiveCoordinator()) {
            showAlert("Access Denied", "Only active coordinators can add schedules.");
            return;
        }

        // Get the currently selected club
        ClubService.Club selectedClub = clubsTable.getSelectionModel().getSelectedItem();

        if (selectedClub == null) {
            showAlert("Selection Required", "Please select a club first to add schedules.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/club-scheduling.fxml"));
            Parent root = loader.load();

            ClubSchedulingController schedulingController = loader.getController();
            schedulingController.setMode(true); // Set to Add Schedule mode
            schedulingController.setSelectedClub(selectedClub); // Pass the selected club

            Stage stage = new Stage();
            stage.setTitle("Add Schedule for: " + selectedClub.getClubName());
            stage.setScene(new Scene(root, 900, 650));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(addScheduleButton.getScene().getWindow());
            stage.showAndWait();

        } catch (Exception e) {
            showAlert("Error", "Failed to load scheduling form: " + e.getMessage());
        }
    }

    @FXML
    private void showClubSchedules() {
        // Check if user is at least a coordinator
        if (!SessionManager.isCoordinator()) {
            showAlert("Access Denied", "You do not have coordinator permissions to view schedules.");
            return;
        }

        ClubService.Club selectedClub = clubsTable.getSelectionModel().getSelectedItem();
        if (selectedClub == null) {
            showAlert("Selection Required", "Please select a club to view its schedules.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/club-schedules.fxml"));
            Parent root = loader.load();

            ClubSchedulesController schedulesController = loader.getController();
            schedulesController.setClub(selectedClub);

            Stage stage = new Stage();
            stage.setTitle("Schedules for: " + selectedClub.getClubName());
            stage.setScene(new Scene(root, 800, 500));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(viewSchedulesButton.getScene().getWindow());
            stage.showAndWait();

        } catch (Exception e) {
            showAlert("Error", "Failed to load club schedules: " + e.getMessage());
        }
    }

    @FXML
    private void handleDeleteClub() {
        if (!isActiveCoordinator) {
            showPermissionError();
            return;
        }

        ClubService.Club selectedClub = clubsTable.getSelectionModel().getSelectedItem();
        if (selectedClub == null) {
            showAlert("Selection Required", "Please select a club to delete.");
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
                        showAlert("Session Expired", "User session expired. Please log in again.");
                        return;
                    }

                    ClubService clubService = new ClubService();
                    boolean success = clubService.deactivateClub(conn, selectedClub.getClubId());

                    if (success) {
                        showAlert("Success", "Club deleted successfully!");
                        loadClubs();
                    } else {
                        showAlert("Error", "Failed to delete club. Please try again.");
                    }

                } catch (SQLException e) {
                    handleDatabaseError(e, "delete club");
                }
            }
        });
    }

    @FXML
    private void handleRefresh() {
        loadClubs();
    }

    private void loadClubs() {
        try (Connection conn = DatabaseConnector.getConnection()) {
            ClubService clubService = new ClubService();
            List<ClubService.Club> clubList = clubService.getClubsBySchool(conn, currentSchoolId);

            clubs.clear();
            clubs.addAll(clubList);
            clubsTable.refresh();

            // CRUCIAL: Force rows to be visible after a short delay to ensure rendering is complete
            javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(Duration.millis(100));
            pause.setOnFinished(event -> forceAllRowsVisible());
            pause.play();

        } catch (SQLException e) {
            handleDatabaseError(e, "load clubs");
        }
    }

    private void showPermissionError() {
        showAlert("Permission Denied", "Only active coordinators can perform this action.");
    }

    private void handleDatabaseError(SQLException e, String operation) {
        if (e.getMessage().contains("unique constraint") && e.getMessage().contains("club_name")) {
            showAlert("Error", "A club with this name already exists in the system.");
        } else {
            showAlert("Error", "Failed to " + operation + ": " + e.getMessage());
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void applyStyles() {
        nameColumn.setPrefWidth(150);
        descriptionColumn.setPrefWidth(250);
        clubsTable.setPlaceholder(new Label("No clubs found"));

        if (clubsTable.getParent() instanceof VBox) {
            VBox.setVgrow(clubsTable, javafx.scene.layout.Priority.ALWAYS);
        }
    }
}