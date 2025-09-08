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
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.net.URL;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

public class ClubSchedulingController implements Initializable {

    @FXML private ComboBox<String> gradeCombo;
    @FXML private ComboBox<String> classCombo;
    @FXML private ComboBox<String> dayCombo;
    @FXML private TextField startTimeField;
    @FXML private TextField endTimeField;
    @FXML private TextField venueField;
    @FXML private TableView<ClubSchedule> scheduleTable;
    @FXML private Label clubNameLabel;
    @FXML private ComboBox<ClubService.Club> clubCombo;
    private ClubSchedule scheduleToEdit;
    private boolean isEditing = false;
    private ClubService.Club currentClub;
    @FXML
    private Button addUpdateButton;

    private final ObservableList<ClubSchedule> schedules = FXCollections.observableArrayList();
    private final ClubService clubService = new ClubService();
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

    private Map<String, UUID> gradeMap = new HashMap<>();
    private Map<String, UUID> classMap = new HashMap<>();
    private Map<String, ClubService.Club> clubMap = new HashMap<>(); // NEW: Map for clubs

    private UUID schoolId;
    private boolean isAddMode = true; // NEW: Track if we're in add mode

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        schoolId = SessionManager.getCurrentSchoolId();

        // Check if user is at least a coordinator
        if (!SessionManager.isCoordinator()) {
            showAlert("Access Denied", "You do not have coordinator permissions to access this feature.");
            // Optionally close the window or disable functionality
            return;
        }

        setupTable();
        loadComboBoxData();
        setupTimeValidation();

        // Enhanced club combo listener
        clubCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadExistingSchedules(newVal.getClubId());
                if (isAddMode) {
                    clubNameLabel.setText("Add Schedule for: " + newVal.getClubName());
                } else {
                    clubNameLabel.setText("Scheduling for: " + newVal.getClubName());
                }
            }
        });
    }

    // NEW: Method to set mode (add schedule for any club vs edit specific club)
    public void setMode(boolean isAddMode) {
        this.isAddMode = isAddMode;
        updateUIForMode();
    }

    // Add this method to set a schedule for editing
    public void setScheduleForEdit(ClubSchedule schedule) {
        this.scheduleToEdit = schedule;
        this.isEditing = true;
        populateFormWithScheduleData();
    }

    private void populateFormWithScheduleData() {
        if (scheduleToEdit != null) {
            // Set grade
            if (scheduleToEdit.getGradeId() != null) {
                gradeCombo.getSelectionModel().select(scheduleToEdit.getGradeName());
            } else {
                gradeCombo.getSelectionModel().select("All Grades");
            }

            // Set class
            if (scheduleToEdit.getClassGroupId() != null) {
                classCombo.getSelectionModel().select(scheduleToEdit.getClassName());
            } else {
                classCombo.getSelectionModel().select("All Classes");
            }

            // Set day
            dayCombo.getSelectionModel().select(scheduleToEdit.getMeetingDay());

            // Set times
            startTimeField.setText(scheduleToEdit.getStartTime().format(timeFormatter));
            endTimeField.setText(scheduleToEdit.getEndTime().format(timeFormatter));

            // Set venue
            venueField.setText(scheduleToEdit.getVenue());
        }
    }

    // NEW: Method to set specific club (for edit mode)
    public void setClub(ClubService.Club club) {
        this.currentClub = club; // Set the current club
        this.isAddMode = false;
        updateUIForMode();
        clubCombo.getSelectionModel().select(club);
        clubNameLabel.setText("Scheduling for: " + club.getClubName());
        loadExistingSchedules(club.getClubId());
    }

    // NEW: Update UI based on mode
    private void updateUIForMode() {
        clubCombo.setVisible(isAddMode);
        clubCombo.setManaged(isAddMode);

        // Enable/disable add buttons based on coordinator permissions
        boolean canEdit = SessionManager.isActiveCoordinator(); // Use the proper method

        // Update the label based on mode
        if (isAddMode) {
            ClubService.Club selectedClub = clubCombo.getSelectionModel().getSelectedItem();
            if (selectedClub != null) {
                clubNameLabel.setText("Add Schedule for: " + selectedClub.getClubName());
            } else {
                clubNameLabel.setText("Add Schedule - Select a Club");
            }
        } else if (currentClub != null) {
            clubNameLabel.setText("Scheduling for: " + currentClub.getClubName());
        }

        // Update button text based on mode
        if (isAddMode) {
            addUpdateButton.setText("Add Schedule");
            // Disable add button if coordinator cannot edit
            addUpdateButton.setDisable(!canEdit);
        } else {
            addUpdateButton.setText("Update Schedule");
            // Disable update button if coordinator cannot edit
            addUpdateButton.setDisable(!canEdit);
        }
    }
    // ============ SCHEDULING METHODS ============

    private void setupTimeValidation() {
        // Add real-time validation to time fields
        startTimeField.textProperty().addListener((observable, oldValue, newValue) -> {
            validateTimeField(startTimeField, newValue);
        });

        endTimeField.textProperty().addListener((observable, oldValue, newValue) -> {
            validateTimeField(endTimeField, newValue);
        });

        // Set placeholder text with format hint
        startTimeField.setPromptText("HH:MM (e.g., 14:30)");
        endTimeField.setPromptText("HH:MM (e.g., 15:30)");
    }

    private void validateTimeField(TextField timeField, String value) {
        if (value == null || value.trim().isEmpty()) {
            clearFieldStyle(timeField);
            return;
        }

        if (value.matches("^([0-1]?[0-9]?|2[0-3]?)?(:[0-5]?[0-9]?)?$")) {
            clearFieldStyle(timeField);
            if (value.length() == 2 && !value.contains(":")) {
                timeField.setText(value + ":");
                timeField.positionCaret(3);
            }
        } else {
            timeField.setStyle("-fx-border-color: #e74c3c; -fx-border-width: 2px;");
        }
    }

    private void clearFieldStyle(TextField field) {
        field.setStyle("");
    }

    private boolean isValidTimeFormat(String timeString) {
        if (timeString == null || timeString.trim().isEmpty()) {
            return false;
        }
        try {
            LocalTime.parse(timeString, timeFormatter);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private LocalTime parseTime(String timeString) {
        try {
            return LocalTime.parse(timeString, timeFormatter);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private boolean isWithinSchoolHours(LocalTime time) {
        LocalTime schoolStart = LocalTime.of(7, 0);
        LocalTime schoolEnd = LocalTime.of(17, 30);
        return !time.isBefore(schoolStart) && !time.isAfter(schoolEnd);
    }

    private void loadExistingSchedules(UUID clubId) {
        schedules.clear();
        try (Connection conn = DatabaseConnector.getConnection()) {
            List<ClubSchedule> existingSchedules = clubService.getClubSchedules(conn, clubId);
            schedules.addAll(existingSchedules);
        } catch (Exception e) {
            showAlert("Error", "Failed to load existing schedules: " + e.getMessage());
        }
    }

    private void setupTable() {
        TableColumn<ClubSchedule, String> targetCol = new TableColumn<>("Target");
        targetCol.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(cellData.getValue().getTargetDisplay()));

        TableColumn<ClubSchedule, String> dayCol = new TableColumn<>("Day");
        dayCol.setCellValueFactory(new PropertyValueFactory<>("meetingDay"));

        TableColumn<ClubSchedule, String> timeCol = new TableColumn<>("Time");
        timeCol.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(cellData.getValue().getTimeRange()));

        TableColumn<ClubSchedule, String> venueCol = new TableColumn<>("Venue");
        venueCol.setCellValueFactory(new PropertyValueFactory<>("venue"));

        TableColumn<ClubSchedule, Void> actionsCol = new TableColumn<>("Actions");
        actionsCol.setCellFactory(param -> new TableCell<>() {
            private final Button removeBtn = new Button("Remove");
            {
                removeBtn.setOnAction(event -> {
                    // Check if coordinator has edit permissions
                    if (!SessionManager.isActiveCoordinator()) {
                        showAlert("Access Denied", "You do not have permission to remove schedules. Your coordinator status is not active.");
                        return;
                    }

                    ClubSchedule schedule = getTableView().getItems().get(getIndex());
                    schedules.remove(schedule);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    // Only show remove button for active coordinators
                    boolean canEdit = SessionManager.isActiveCoordinator();
                    setGraphic(canEdit ? removeBtn : null);
                    removeBtn.setDisable(!canEdit);
                }
            }
        });

        scheduleTable.getColumns().setAll(targetCol, dayCol, timeCol, venueCol, actionsCol);
        scheduleTable.setItems(schedules);
    }

    // In your ClubSchedulingController, update the loadComboBoxData method:

    private void loadComboBoxData() {
        try (Connection conn = DatabaseConnector.getConnection()) {
            // CLEAR EXISTING ITEMS FIRST - This is the crucial fix
            gradeCombo.getItems().clear();
            classCombo.getItems().clear();
            gradeMap.clear();
            classMap.clear();

            // Load clubs (keep existing logic)
            List<ClubService.Club> clubs = clubService.getClubsBySchool(conn, schoolId);
            clubCombo.getItems().setAll(clubs); // Use setAll instead of addAll

            clubCombo.setCellFactory(param -> new ListCell<ClubService.Club>() {
                @Override
                protected void updateItem(ClubService.Club item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                    } else {
                        setText(item.getClubName());
                    }
                }
            });

            clubCombo.setButtonCell(new ListCell<ClubService.Club>() {
                @Override
                protected void updateItem(ClubService.Club item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                    } else {
                        setText(item.getClubName());
                    }
                }
            });

            // Load grades - CLEARED FIRST, then add fresh data
            List<Map<String, Object>> gradeData = clubService.getGrades(conn, schoolId);
            List<String> gradeNames = gradeData.stream()
                    .map(data -> {
                        String gradeName = (String) data.get("gradeName");
                        UUID gradeId = (UUID) data.get("gradeId");
                        gradeMap.put(gradeName, gradeId);
                        return gradeName;
                    })
                    .collect(Collectors.toList());

            gradeCombo.getItems().addAll("All Grades");
            gradeCombo.getItems().addAll(gradeNames);
            gradeCombo.getSelectionModel().selectFirst();

            // Load classes - CLEARED FIRST, then add fresh data
            List<Map<String, Object>> classData = clubService.getClassGroups(conn, schoolId);
            List<String> classNames = classData.stream()
                    .map(data -> {
                        String className = (String) data.get("className");
                        UUID classId = (UUID) data.get("classId");
                        classMap.put(className, classId);
                        return className;
                    })
                    .collect(Collectors.toList());

            classCombo.getItems().addAll("All Classes");
            classCombo.getItems().addAll(classNames);
            classCombo.getSelectionModel().selectFirst();

            // Load days (only need to set once, but clear to be safe)
            dayCombo.getItems().clear();
            dayCombo.getItems().addAll("MON", "TUE", "WED", "THUR", "FRI", "SAT", "SUN");
            dayCombo.getSelectionModel().selectFirst();

        } catch (SQLException e) {
            showAlert("Database Error", "Failed to load data: " + e.getMessage());
        }
    }

    // Method to check if grades and classes are available
    private boolean validateDataLoaded() {
        if (gradeCombo.getItems().size() <= 1) { // Only "All Grades" is available
            showAlert("Data Error", "No grades found for your school. Please contact administrator.");
            return false;
        }

        if (classCombo.getItems().size() <= 1) { // Only "All Classes" is available
            showAlert("Data Error", "No classes found for your school. Please contact administrator.");
            return false;
        }

        return true;
    }

    @FXML
    private void addSchedule() {
        // Check if coordinator has edit permissions
        if (!SessionManager.isActiveCoordinator()) {
            showAlert("Access Denied", "You do not have permission to modify schedules. Your coordinator status is not active.");
            return;
        }

        if (isEditing) {
            updateSchedule();
        } else {
            addNewSchedule();
        }
    }

    @FXML
    private void addNewSchedule() {
        // NEW: Get selected club
        ClubService.Club selectedClub = clubCombo.getSelectionModel().getSelectedItem();
        if (selectedClub == null) {
            showAlert("Selection Required", "Please select a club first.");
            return;
        }

        String selectedGrade = gradeCombo.getSelectionModel().getSelectedItem();
        String selectedClass = classCombo.getSelectionModel().getSelectedItem();
        String selectedDay = dayCombo.getSelectionModel().getSelectedItem();
        String startTimeStr = startTimeField.getText().trim();
        String endTimeStr = endTimeField.getText().trim();
        String venue = venueField.getText().trim();

        // Validate time format
        if (!isValidTimeFormat(startTimeStr)) {
            showAlert("Invalid Time", "Please enter a valid start time in HH:MM format (e.g., 14:30).");
            startTimeField.requestFocus();
            return;
        }

        if (!isValidTimeFormat(endTimeStr)) {
            showAlert("Invalid Time", "Please enter a valid end time in HH:MM format (e.g., 15:30).");
            endTimeField.requestFocus();
            return;
        }

        LocalTime startTime = parseTime(startTimeStr);
        LocalTime endTime = parseTime(endTimeStr);

        // Validate time logic
        if (endTime.isBefore(startTime) || endTime.equals(startTime)) {
            showAlert("Invalid Time", "End time must be after start time.");
            endTimeField.requestFocus();
            return;
        }

        // Validate school hours
        if (!isWithinSchoolHours(startTime)) {
            showAlert("Invalid Time", "Start time must be within school hours (07:00 - 17:30).");
            startTimeField.requestFocus();
            return;
        }

        if (!isWithinSchoolHours(endTime)) {
            showAlert("Invalid Time", "End time must be within school hours (07:00 - 17:30).");
            endTimeField.requestFocus();
            return;
        }

        // Validate session duration
        if (java.time.Duration.between(startTime, endTime).toMinutes() < 30) {
            showAlert("Invalid Duration", "Club session should be at least 30 minutes long.");
            endTimeField.requestFocus();
            return;
        }

        // Get UUIDs
        UUID gradeId = selectedGrade.equals("All Grades") ? null : gradeMap.get(selectedGrade);
        UUID classId = selectedClass.equals("All Classes") ? null : classMap.get(selectedClass);

        // Check for duplicates
        for (ClubSchedule existing : schedules) {
            if (Objects.equals(existing.getGradeId(), gradeId) &&
                    Objects.equals(existing.getClassGroupId(), classId)) {
                showAlert("Duplicate Schedule",
                        "A schedule for this grade/class combination already exists.");
                return;
            }
        }

        // Check for time conflicts
        try (Connection conn = DatabaseConnector.getConnection()) {
            boolean hasConflict = clubService.hasScheduleConflict(
                    conn, gradeId, classId, selectedDay, startTime, endTime,
                    schoolId, selectedClub.getClubId(), null);
            if (hasConflict) {
                showAlert("Schedule Conflict",
                        "This grade/class already has a club scheduled at this time.");
                return;
            }
        } catch (Exception e) {
            showAlert("Error", "Failed to check schedule conflicts: " + e.getMessage());
            return;
        }

        // Create and add schedule
        ClubSchedule schedule = new ClubSchedule(gradeId, classId, selectedDay, startTime, endTime, venue);
        schedule.setClubId(selectedClub.getClubId());

        // Set display names
        if (gradeId != null) {
            schedule.setGradeName(selectedGrade);
        }
        if (classId != null) {
            schedule.setClassName(selectedClass);
        }

        schedules.add(schedule);

        // Show success message
        showAlert("Success", "Schedule added successfully!");

        // NEW: Clear everything and reset to default after successful addition
        resetFormToDefault();
    }

    private void updateSchedule() {
        // Get form values
        String selectedGrade = gradeCombo.getSelectionModel().getSelectedItem();
        String selectedClass = classCombo.getSelectionModel().getSelectedItem();
        String selectedDay = dayCombo.getSelectionModel().getSelectedItem();
        String startTimeStr = startTimeField.getText().trim();
        String endTimeStr = endTimeField.getText().trim();
        String venue = venueField.getText().trim();

        // Validate inputs (same as add schedule)
        if (!isValidTimeFormat(startTimeStr)) {
            showAlert("Invalid Time", "Please enter a valid start time in HH:MM format (e.g., 14:30).");
            startTimeField.requestFocus();
            return;
        }

        if (!isValidTimeFormat(endTimeStr)) {
            showAlert("Invalid Time", "Please enter a valid end time in HH:MM format (e.g., 15:30).");
            endTimeField.requestFocus();
            return;
        }

        LocalTime startTime = parseTime(startTimeStr);
        LocalTime endTime = parseTime(endTimeStr);

        // Validate time logic
        if (endTime.isBefore(startTime) || endTime.equals(startTime)) {
            showAlert("Invalid Time", "End time must be after start time.");
            endTimeField.requestFocus();
            return;
        }

        // Get UUIDs
        UUID gradeId = selectedGrade.equals("All Grades") ? null : gradeMap.get(selectedGrade);
        UUID classId = selectedClass.equals("All Classes") ? null : classMap.get(selectedClass);

        // Check for conflicts (excluding the current schedule being edited)
        try (Connection conn = DatabaseConnector.getConnection()) {
            boolean hasConflict = clubService.hasScheduleConflict(
                    conn, gradeId, classId, selectedDay, startTime, endTime,
                    schoolId, null, scheduleToEdit.getScheduleId());

            if (hasConflict) {
                showAlert("Schedule Conflict",
                        "This grade/class already has a club scheduled at this time.");
                return;
            }
        } catch (Exception e) {
            showAlert("Error", "Failed to check schedule conflicts: " + e.getMessage());
            return;
        }

        // Update the schedule
        try (Connection conn = DatabaseConnector.getConnection()) {
            boolean success = clubService.updateSchedule(
                    conn, scheduleToEdit.getScheduleId(), gradeId, classId,
                    selectedDay, startTime, endTime, venue);

            if (success) {
                showAlert("Success", "Schedule updated successfully!");
                closeWindow();
            } else {
                showAlert("Error", "Failed to update schedule.");
            }
        } catch (Exception e) {
            showAlert("Error", "Failed to update schedule: " + e.getMessage());
        }
    }

    // NEW: Method to reset the form to default state
    private void resetFormToDefault() {
        // Reset combo boxes to their first/default selection
        gradeCombo.getSelectionModel().selectFirst();    // "All Grades"
        classCombo.getSelectionModel().selectFirst();    // "All Classes"
        dayCombo.getSelectionModel().selectFirst();      // "MON" or first day

        // Clear input fields
        startTimeField.clear();
        endTimeField.clear();
        venueField.clear();

        // Clear any error styling
        clearFieldStyle(startTimeField);
        clearFieldStyle(endTimeField);

        // Optionally: Focus on the first field for next entry
        startTimeField.requestFocus();
    }

    // NEW: Method to set the pre-selected club for Add Schedule mode
    public void setSelectedClub(ClubService.Club club) {
        if (isAddMode && club != null) {
            // Auto-select the club in the combo box
            clubCombo.getSelectionModel().select(club);
            clubNameLabel.setText("Add Schedule for: " + club.getClubName());
            loadExistingSchedules(club.getClubId());
        }
    }

    // In your ClubSchedulingController.java, add these methods:

    @FXML
    private void showAddGradeForm() {
        // Check if coordinator has edit permissions
        if (!SessionManager.isActiveCoordinator()) {
            showAlert("Access Denied", "You do not have permission to add grades. Your coordinator status is not active.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/grade-add.fxml"));
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("Add New Grade");
            stage.setScene(new Scene(root, 400, 200));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(gradeCombo.getScene().getWindow());
            stage.showAndWait();

            // Refresh grades after adding a new one
            loadComboBoxData();

        } catch (Exception e) {
            showAlert("Error", "Failed to load grade form: " + e.getMessage());
        }
    }

    @FXML
    private void showAddClassForm() {
        // Check if coordinator has edit permissions
        if (!SessionManager.isActiveCoordinator()) {
            showAlert("Access Denied", "You do not have permission to add classes. Your coordinator status is not active.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/class-add.fxml"));
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("Add New Class");
            stage.setScene(new Scene(root, 400, 200));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(classCombo.getScene().getWindow());
            stage.showAndWait();

            // Refresh classes after adding a new one
            loadComboBoxData();

        } catch (Exception e) {
            showAlert("Error", "Failed to load class form: " + e.getMessage());
        }
    }

    private void clearScheduleInputFields() {
        startTimeField.clear();
        endTimeField.clear();
        venueField.clear();
    }

    // ============ END OF SCHEDULING METHODS ============

    @FXML
    private void handleSave() {
        // Check if coordinator has edit permissions
        if (!SessionManager.isActiveCoordinator()) {
            showAlert("Access Denied", "You do not have permission to save schedules. Your coordinator status is not active.");
            return;
        }

        // NEW: Get selected club
        ClubService.Club selectedClub = clubCombo.getSelectionModel().getSelectedItem();
        if (selectedClub == null) {
            showAlert("Selection Required", "Please select a club first.");
            return;
        }

        if (schedules.isEmpty()) {
            showAlert("Validation Error", "At least one schedule is required.");
            return;
        }

        try (Connection conn = DatabaseConnector.getConnection()) {
            // Remove existing schedules and add new ones (MODIFIED: use selectedClub)
            clubService.updateClubSchedules(conn, selectedClub.getClubId(), schedules);

            showAlert("Success", "Club schedule saved successfully with " + schedules.size() + " schedules.");
            closeWindow();

        } catch (SQLException e) {
            showAlert("Database Error", "Failed to save schedules: " + e.getMessage());
        }
    }

    @FXML
    private void handleCancel() {
        closeWindow();
    }

    @FXML
    private void handleRefresh() {
        loadComboBoxData();
        showAlert("Info", "Grades and classes data refreshed.");
    }

    @FXML
    private void handleBack() {
        closeWindow();
    }

    private void closeWindow() {
        Stage stage = (Stage) scheduleTable.getScene().getWindow();
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