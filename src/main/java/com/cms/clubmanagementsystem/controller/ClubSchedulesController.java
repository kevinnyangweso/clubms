package com.cms.clubmanagementsystem.controller;

import com.cms.clubmanagementsystem.model.ClubSchedule;
import com.cms.clubmanagementsystem.service.ClubService;
import com.cms.clubmanagementsystem.utils.DatabaseConnector;
import com.cms.clubmanagementsystem.utils.SessionManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.net.URL;
import java.sql.Connection;
import java.util.List;
import java.util.ResourceBundle;
import java.util.UUID;

public class ClubSchedulesController implements Initializable {

    @FXML private TableView<ClubSchedule> schedulesTable;
    @FXML private TableColumn<ClubSchedule, String> targetColumn;
    @FXML private TableColumn<ClubSchedule, String> dayColumn;
    @FXML private TableColumn<ClubSchedule, String> timeColumn;
    @FXML private TableColumn<ClubSchedule, String> venueColumn;
    @FXML private TableColumn<ClubSchedule, Void> actionsColumn;
    @FXML private Label clubNameLabel;
    @FXML private Label statusLabel;

    private final ObservableList<ClubSchedule> schedules = FXCollections.observableArrayList();
    private final ClubService clubService = new ClubService();
    private ClubService.Club currentClub;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTable();
    }

    public void setClub(ClubService.Club club) {
        this.currentClub = club;

        // Check coordinator status and update UI accordingly
        boolean isCoordinator = SessionManager.isCoordinator();
        boolean isActiveCoordinator = SessionManager.isActiveCoordinator();

        String statusText;
        if (isActiveCoordinator) {
            statusText = "Schedules for: ";
            statusLabel.setText("Status: Active Coordinator - Full Access");
            statusLabel.setStyle("-fx-text-fill: green;");
        } else if (isCoordinator) {
            statusText = "Viewing Schedules for: ";
            statusLabel.setText("Status: Inactive Coordinator - Read Only");
            statusLabel.setStyle("-fx-text-fill: orange;");
        } else {
            statusText = "Accessing: ";
            statusLabel.setText("Status: No Coordinator Access");
            statusLabel.setStyle("-fx-text-fill: red;");
        }

        clubNameLabel.setText(statusText + club.getClubName());
        loadSchedules();
    }

    private void setupTable() {
        targetColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(cellData.getValue().getTargetDisplay()));

        dayColumn.setCellValueFactory(new PropertyValueFactory<>("meetingDay"));

        timeColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(cellData.getValue().getTimeRange()));

        venueColumn.setCellValueFactory(new PropertyValueFactory<>("venue"));

        // Set up the actions column
        actionsColumn.setCellFactory(param -> new TableCell<ClubSchedule, Void>() {
            private final Button editButton = new Button("Edit");
            private final Button deleteButton = new Button("Delete");
            private final HBox buttonsContainer = new HBox(5, editButton, deleteButton);

            {
                editButton.setOnAction(event -> {
                    ClubSchedule schedule = getTableView().getItems().get(getIndex());
                    editSchedule(schedule);
                });

                deleteButton.setOnAction(event -> {
                    ClubSchedule schedule = getTableView().getItems().get(getIndex());
                    deleteSchedule(schedule);
                });

                // Style the buttons
                editButton.setStyle("-fx-font-size: 10px; -fx-padding: 3px 6px;");
                deleteButton.setStyle("-fx-font-size: 10px; -fx-padding: 3px 6px; -fx-background-color: #ff4444; -fx-text-fill: white;");
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    // Only show edit/delete buttons for ACTIVE coordinators
                    boolean canEdit = SessionManager.isActiveCoordinator();
                    setGraphic(canEdit ? buttonsContainer : null);

                    // Disable buttons if coordinator is not active
                    editButton.setDisable(!canEdit);
                    deleteButton.setDisable(!canEdit);
                }
            }
        });

        schedulesTable.setItems(schedules);

        // Make table non-editable for inactive coordinators
        boolean canEdit = SessionManager.isActiveCoordinator();
        schedulesTable.setEditable(canEdit);
    }

    private void loadSchedules() {
        schedules.clear();
        try (Connection conn = DatabaseConnector.getConnection()) {
            List<ClubSchedule> clubSchedules = clubService.getClubSchedules(conn, currentClub.getClubId());
            schedules.addAll(clubSchedules);
        } catch (Exception e) {
            showAlert("Error", "Failed to load schedules: " + e.getMessage());
        }
    }

    private void editSchedule(ClubSchedule schedule) {
        // Check if coordinator is active before allowing edit
        if (!SessionManager.isActiveCoordinator()) {
            showAlert("Access Denied", "You do not have permission to edit schedules. Your coordinator status is not active.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/club-scheduling.fxml"));
            Parent root = loader.load();

            ClubSchedulingController controller = loader.getController();
            controller.setMode(false); // Set to edit mode
            controller.setClub(currentClub); // Set the club
            controller.setScheduleForEdit(schedule); // Pass the schedule to edit

            Stage stage = new Stage();
            stage.setTitle("Edit Schedule for: " + currentClub.getClubName());
            stage.setScene(new Scene(root, 900, 650));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(schedulesTable.getScene().getWindow());
            stage.showAndWait();

            // Refresh the schedules after editing
            loadSchedules();

        } catch (Exception e) {
            showAlert("Error", "Failed to load schedule editor: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void deleteSchedule(ClubSchedule schedule) {
        // Check if coordinator is active before allowing deletion
        if (!SessionManager.isActiveCoordinator()) {
            showAlert("Access Denied", "You do not have permission to delete schedules. Your coordinator status is not active.");
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirm Deletion");
        confirmation.setHeaderText("Delete Schedule");
        confirmation.setContentText("Are you sure you want to delete this schedule?");

        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try (Connection conn = DatabaseConnector.getConnection()) {
                    boolean success = clubService.deleteSchedule(conn, schedule.getScheduleId());

                    if (success) {
                        showAlert("Success", "Schedule deleted successfully!");
                        loadSchedules(); // Refresh the table
                    } else {
                        showAlert("Error", "Failed to delete schedule.");
                    }
                } catch (Exception e) {
                    showAlert("Error", "Failed to delete schedule: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
    }

    @FXML
    private void handleClose() {
        Stage stage = (Stage) schedulesTable.getScene().getWindow();
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