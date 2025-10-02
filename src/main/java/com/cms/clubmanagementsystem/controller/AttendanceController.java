package com.cms.clubmanagementsystem.controller;

import com.cms.clubmanagementsystem.model.ClubSchedule;
import com.cms.clubmanagementsystem.service.ClubService;
import com.cms.clubmanagementsystem.utils.DatabaseConnector;
import com.cms.clubmanagementsystem.utils.EventBus;
import com.cms.clubmanagementsystem.utils.EventTypes;
import com.cms.clubmanagementsystem.utils.SessionManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.stage.Stage;
import javafx.util.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.UUID;

public class AttendanceController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(AttendanceController.class);

    @FXML private ComboBox<Session> sessionComboBox;
    @FXML private ComboBox<ClubSchedule> scheduleComboBox;
    @FXML private TableView<AttendanceRecord> attendanceTable;
    @FXML private TableColumn<AttendanceRecord, String> fullNameColumn;
    @FXML private TableColumn<AttendanceRecord, String> gradeColumn;
    @FXML private TableColumn<AttendanceRecord, String> statusColumn;
    @FXML private Label sessionInfoLabel;
    @FXML private DatePicker sessionDatePicker;
    @FXML private Label attendanceSummaryLabel;

    private UUID clubId;
    private String clubName;
    private int selectedYear;
    private int selectedTerm;
    private ObservableList<AttendanceRecord> attendanceList = FXCollections.observableArrayList();
    private ObservableList<Session> sessionList = FXCollections.observableArrayList();
    private ObservableList<ClubSchedule> scheduleList = FXCollections.observableArrayList();

    // Use lowercase values that match the database enum
    private ObservableList<String> statusOptions = FXCollections.observableArrayList("present", "absent", "late");

    private final ClubService clubService = new ClubService();
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("Initializing AttendanceController");

        setupTableView();
        setupTableInteractions();
        setupSessionComboBox();
        setupScheduleComboBox();
        setupDatePicker();
        updateAttendanceSummary();
    }

    private void setupTableView() {
        fullNameColumn.setCellValueFactory(new PropertyValueFactory<>("fullName"));
        gradeColumn.setCellValueFactory(new PropertyValueFactory<>("grade"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));

        // Create a custom cell factory that combines display formatting with ComboBox editing
        statusColumn.setCellFactory(new Callback<TableColumn<AttendanceRecord, String>, TableCell<AttendanceRecord, String>>() {
            @Override
            public TableCell<AttendanceRecord, String> call(TableColumn<AttendanceRecord, String> param) {
                return new ComboBoxTableCell<AttendanceRecord, String>(statusOptions) {
                    @Override
                    public void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);

                        if (empty || item == null) {
                            setText(null);
                            setStyle("");
                        } else {
                            // Convert lowercase database values to display-friendly format
                            String displayText;
                            switch (item) {
                                case "present":
                                    displayText = "Present";
                                    setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                                    break;
                                case "absent":
                                    displayText = "Absent";
                                    setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                                    break;
                                default:
                                    displayText = item;
                                    setStyle("");
                                    break;
                            }
                            setText(displayText);
                        }
                    }
                };
            }
        });

        statusColumn.setOnEditCommit(event -> {
            AttendanceRecord record = event.getRowValue();
            if (record != null) {
                record.setStatus(event.getNewValue());
                attendanceTable.refresh();
                updateAttendanceSummary();
                logger.debug("Updated status for learner {} to {}", record.getLearnerId(), event.getNewValue());
            }
        });

        attendanceTable.setEditable(true);
        attendanceTable.setItems(attendanceList);

        // Listen for changes in the attendance list to update summary
        attendanceList.addListener((javafx.collections.ListChangeListener<AttendanceRecord>) change -> {
            updateAttendanceSummary();
        });
    }

    private void setupTableInteractions() {
        // Add double-click functionality to quickly toggle status
        attendanceTable.setRowFactory(tv -> {
            TableRow<AttendanceRecord> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    AttendanceRecord record = row.getItem();
                    // Toggle between present and absent on double-click
                    if ("present".equals(record.getStatus())) {
                        record.setStatus("absent");
                    } else {
                        record.setStatus("present");
                    }
                    attendanceTable.refresh();
                    updateAttendanceSummary();
                    logger.debug("Toggled status for learner {} to {}", record.getLearnerId(), record.getStatus());
                }
            });
            return row;
        });

        // Add tooltip to the table to explain functionality
        Tooltip tableTooltip = new Tooltip("• Click on Status cells to select from dropdown\n• Double-click on rows to toggle between Present/Absent");
        attendanceTable.setTooltip(tableTooltip);

        statusColumn.setStyle("-fx-alignment: CENTER;");
    }

    private void setupSessionComboBox() {
        sessionComboBox.setItems(sessionList);
        sessionComboBox.setCellFactory(lv -> new ListCell<Session>() {
            @Override
            protected void updateItem(Session item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.getDisplayName());
            }
        });
        sessionComboBox.setButtonCell(new ListCell<Session>() {
            @Override
            protected void updateItem(Session item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.getDisplayName());
            }
        });
        sessionComboBox.getSelectionModel().selectedItemProperty().addListener((obs, old, newValue) -> {
            if (newValue != null) {
                logger.info("Selected session: {}", newValue.getSessionId());
                loadAttendanceData(newValue.getSessionId());
                updateSessionInfo(newValue);
            }
        });
    }

    private void setupScheduleComboBox() {
        scheduleComboBox.setItems(scheduleList);
        scheduleComboBox.setCellFactory(lv -> new ListCell<ClubSchedule>() {
            @Override
            protected void updateItem(ClubSchedule item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("%s - %s (%s)",
                            item.getStartTime().format(timeFormatter),
                            item.getEndTime().format(timeFormatter),
                            item.getVenue()));
                }
            }
        });
        scheduleComboBox.setButtonCell(new ListCell<ClubSchedule>() {
            @Override
            protected void updateItem(ClubSchedule item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("%s - %s (%s)",
                            item.getStartTime().format(timeFormatter),
                            item.getEndTime().format(timeFormatter),
                            item.getVenue()));
                }
            }
        });
    }

    private void setupDatePicker() {
        sessionDatePicker.setValue(LocalDate.now());
        sessionDatePicker.valueProperty().addListener((obs, oldDate, newDate) -> {
            if (newDate != null) {
                loadSessionsForDate(newDate);
            }
        });
    }

    public void setClubId(UUID clubId) {
        this.clubId = clubId;
        loadClubSchedules();
        loadSessionsForDate(LocalDate.now());
    }

    public void setClubName(String clubName) {
        this.clubName = clubName;
        updateSessionInfo(null);
    }

    public void setYearAndTerm(int year, int term) {
        this.selectedYear = year;
        this.selectedTerm = term;
        logger.info("Set year: {}, term: {} for AttendanceController", selectedYear, selectedTerm);

        if (clubId != null) {
            loadClubSchedules();
            if (!sessionList.isEmpty()) {
                Session selectedSession = sessionComboBox.getSelectionModel().getSelectedItem();
                if (selectedSession != null) {
                    loadAttendanceData(selectedSession.getSessionId());
                }
            }
        }
    }

    private void loadClubSchedules() {
        scheduleList.clear();
        if (clubId == null) return;

        try (Connection conn = DatabaseConnector.getConnection()) {
            List<ClubSchedule> schedules = clubService.getClubSchedules(conn, clubId);
            scheduleList.addAll(schedules);
            logger.info("Loaded {} schedules for club {}", schedules.size(), clubId);

            if (!scheduleList.isEmpty()) {
                scheduleComboBox.getSelectionModel().selectFirst();
            }
        } catch (Exception e) {
            logger.error("Error loading club schedules: {}", e.getMessage(), e);
            showError("Error loading club schedules: " + e.getMessage());
        }
    }

    private void loadSessionsForDate(LocalDate date) {
        sessionList.clear();
        if (clubId == null) return;

        try (Connection conn = DatabaseConnector.getConnection()) {
            String sql = """
                SELECT session_id, session_date, start_time, end_time, notes
                FROM attendance_sessions
                WHERE club_id = ? AND session_date = ?
                ORDER BY start_time
            """;

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, clubId);
                ps.setDate(2, java.sql.Date.valueOf(date));
                ResultSet rs = ps.executeQuery();

                while (rs.next()) {
                    sessionList.add(new Session(
                            (UUID) rs.getObject("session_id"),
                            rs.getDate("session_date").toLocalDate(),
                            rs.getTime("start_time").toLocalTime(),
                            rs.getTime("end_time").toLocalTime(),
                            rs.getString("notes")
                    ));
                }
                logger.info("Loaded {} sessions for club {} on {}", sessionList.size(), clubId, date);
            }

            if (!sessionList.isEmpty()) {
                sessionComboBox.getSelectionModel().selectFirst();
            } else {
                sessionComboBox.getSelectionModel().clearSelection();
            }
        } catch (Exception e) {
            logger.error("Error loading sessions: {}", e.getMessage(), e);
            showError("Error loading sessions: " + e.getMessage());
        }
    }

    private void loadAttendanceData(UUID sessionId) {
        attendanceList.clear();
        if (sessionId == null) return;

        try (Connection conn = DatabaseConnector.getConnection()) {
            String sql = """
                SELECT l.learner_id, l.full_name, g.grade_name,
                       ar.status as recorded_status
                FROM club_enrollments ce
                JOIN learners l ON ce.learner_id = l.learner_id
                JOIN grades g ON l.grade_id = g.grade_id
                LEFT JOIN attendance_records ar ON l.learner_id = ar.learner_id
                    AND ar.session_id = ?
                WHERE ce.club_id = ? AND ce.is_active = true
                AND ce.academic_year = ?
                AND ce.term_number = ?
                ORDER BY g.grade_name, l.full_name
            """;

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, sessionId);
                ps.setObject(2, clubId);
                ps.setInt(3, selectedYear);
                ps.setInt(4, selectedTerm);
                ResultSet rs = ps.executeQuery();

                while (rs.next()) {
                    String recordedStatus = rs.getString("recorded_status");
                    // If no attendance record exists, default to "absent"
                    String status = (recordedStatus != null) ? recordedStatus : "absent";

                    attendanceList.add(new AttendanceRecord(
                            rs.getString("learner_id"),
                            rs.getString("full_name"),
                            rs.getString("grade_name"),
                            status
                    ));
                }
                logger.info("Loaded {} attendance records for session {}, year {}, term {}",
                        attendanceList.size(), sessionId, selectedYear, selectedTerm);

                updateAttendanceSummary();
            }
        } catch (Exception e) {
            logger.error("Error loading attendance data: {}", e.getMessage(), e);
            showError("Error loading attendance data: " + e.getMessage());
        }
    }

    private void updateSessionInfo(Session session) {
        if (session != null) {
            sessionInfoLabel.setText(String.format("Session: %s | %s - %s | %s",
                    session.getSessionDate(),
                    session.getStartTime().format(timeFormatter),
                    session.getEndTime().format(timeFormatter),
                    clubName));
        } else {
            sessionInfoLabel.setText("Club: " + clubName);
        }
    }

    private void updateAttendanceSummary() {
        if (attendanceList.isEmpty()) {
            attendanceSummaryLabel.setText("No learners to mark attendance for");
            return;
        }

        long presentCount = attendanceList.stream()
                .filter(record -> "present".equals(record.getStatus()))
                .count();

        long absentCount = attendanceList.stream()
                .filter(record -> "absent".equals(record.getStatus()))
                .count();

        long lateCount = attendanceList.stream()
                .filter(record -> "late".equals(record.getStatus()))
                .count();

        int total = attendanceList.size();

        attendanceSummaryLabel.setText(String.format(
                "Attendance Summary: %d Present, %d Absent, %d Late (Total: %d learners)",
                presentCount, absentCount, lateCount, total
        ));
    }

    @FXML
    private void createNewSession() {
        if (clubId == null) {
            showError("No club selected.");
            return;
        }

        ClubSchedule selectedSchedule = scheduleComboBox.getSelectionModel().getSelectedItem();
        if (selectedSchedule == null) {
            showError("Please select a schedule first.");
            return;
        }

        LocalDate sessionDate = sessionDatePicker.getValue();
        if (sessionDate == null) {
            showError("Please select a date for the session.");
            return;
        }

        // Validate that the session date matches the club's scheduled days
        if (!validateSessionAgainstSchedules(sessionDate)) {
            String dayOfWeek = sessionDate.getDayOfWeek().toString().substring(0, 3);
            String scheduledDays = getScheduledDays();
            showError(String.format(
                    "No club schedule exists for %s.\n\nClub is scheduled on: %s\n\nPlease select a date that matches the club's scheduled days.",
                    dayOfWeek, scheduledDays
            ));
            return;
        }

        // Check if session already exists for this date and schedule
        if (sessionExistsForDate(sessionDate)) {
            showError("A session already exists for this date. Please select a different date or use the existing session.");
            return;
        }

        // Check for future date
        if (sessionDate.isAfter(LocalDate.now())) {
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
            confirmation.setTitle("Future Date Confirmation");
            confirmation.setHeaderText("Creating session for a future date");
            confirmation.setContentText("You are creating a session for " + sessionDate +
                    ". Are you sure you want to create a session for a future date?");

            if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                return;
            }
        }

        try (Connection conn = DatabaseConnector.getConnection()) {
            String sql = """
        INSERT INTO attendance_sessions (session_id, club_id, session_date, start_time, end_time, created_by, school_id)
        VALUES (?, ?, ?, ?, ?, ?, ?)
    """;

            UUID sessionId = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, sessionId);
                ps.setObject(2, clubId);
                ps.setDate(3, java.sql.Date.valueOf(sessionDate));
                ps.setTime(4, Time.valueOf(selectedSchedule.getStartTime()));
                ps.setTime(5, Time.valueOf(selectedSchedule.getEndTime()));
                ps.setObject(6, SessionManager.getCurrentUserId());
                ps.setObject(7, SessionManager.getCurrentSchoolId());
                ps.executeUpdate();
                logger.info("Created new session {} for club {}", sessionId, clubId);
            }

            Session newSession = new Session(sessionId, sessionDate,
                    selectedSchedule.getStartTime(), selectedSchedule.getEndTime(), null);

            sessionList.add(newSession);
            sessionComboBox.getSelectionModel().select(newSession);

            // Load attendance data for the new session (all learners will be marked as absent by default)
            loadAttendanceData(sessionId);

            showInfo("New session created successfully! Please mark attendance for learners who are present.");
        } catch (Exception e) {
            logger.error("Error creating new session: {}", e.getMessage(), e);
            showError("Error creating new session: " + e.getMessage());
        }
    }

    private boolean sessionExistsForDate(LocalDate date) {
        if (clubId == null || date == null) {
            return false;
        }

        try (Connection conn = DatabaseConnector.getConnection()) {
            String sql = "SELECT 1 FROM attendance_sessions WHERE club_id = ? AND session_date = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, clubId);
                ps.setDate(2, java.sql.Date.valueOf(date));
                ResultSet rs = ps.executeQuery();
                boolean exists = rs.next();
                logger.debug("Session exists for date {}: {}", date, exists);
                return exists;
            }
        } catch (Exception e) {
            logger.error("Error checking session existence: {}", e.getMessage(), e);
            return false; // Return false on error to allow creation
        }
    }

    @FXML
    private void saveAttendance() {
        Session selectedSession = sessionComboBox.getSelectionModel().getSelectedItem();
        if (selectedSession == null) {
            showError("Please select or create a session first.");
            return;
        }

        // Check if any attendance has been marked
        long presentCount = attendanceList.stream()
                .filter(record -> "present".equals(record.getStatus()) || "late".equals(record.getStatus()))
                .count();

        if (presentCount == 0) {
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
            confirmation.setTitle("Confirm Save");
            confirmation.setHeaderText("No learners marked as present");
            confirmation.setContentText("You are about to save attendance with no learners marked as present. Are you sure you want to continue?");

            if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                return;
            }
        }

        UUID sessionId = selectedSession.getSessionId();
        try (Connection conn = DatabaseConnector.getConnection()) {
            // Try approach 1: Using cast in SQL
            String sql = """
            INSERT INTO attendance_records (record_id, session_id, learner_id, school_id, status, marked_by)
            VALUES (?, ?, ?, ?, ?::attendance_status, ?)
            ON CONFLICT (session_id, learner_id)
            DO UPDATE SET status = EXCLUDED.status::attendance_status, marked_by = EXCLUDED.marked_by, marked_at = CURRENT_TIMESTAMP
        """;

            boolean hasChanges = false;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (AttendanceRecord record : attendanceList) {
                    // Skip if status hasn't changed from default (but always save for new sessions)
                    if (!record.isStatusChanged() && !isNewSession(sessionId)) {
                        continue;
                    }

                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, sessionId);
                    ps.setObject(3, UUID.fromString(record.getLearnerId()));
                    ps.setObject(4, SessionManager.getCurrentSchoolId());
                    ps.setString(5, record.getStatus()); // This will be cast to attendance_status
                    ps.setObject(6, SessionManager.getCurrentUserId());
                    ps.addBatch();
                    hasChanges = true;
                }

                if (hasChanges) {
                    ps.executeBatch();
                    logger.info("Saved attendance for session {}, year {}, term {}", sessionId, selectedYear, selectedTerm);
                    showInfo("Attendance saved successfully!");
                    EventBus.publish(EventTypes.CLUB_STATS_UPDATED, clubId);

                    // Reset the changed status after saving
                    for (AttendanceRecord record : attendanceList) {
                        record.resetStatusChanged();
                    }
                } else {
                    showInfo("No changes to save.");
                }
            }
        } catch (Exception e) {
            logger.error("Error saving attendance: {}", e.getMessage(), e);

            // If the cast approach fails, try the setObject approach
            try (Connection conn = DatabaseConnector.getConnection()) {
                String sql = """
                INSERT INTO attendance_records (record_id, session_id, learner_id, school_id, status, marked_by)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (session_id, learner_id)
                DO UPDATE SET status = EXCLUDED.status, marked_by = EXCLUDED.marked_by, marked_at = CURRENT_TIMESTAMP
            """;

                boolean hasChanges = false;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    for (AttendanceRecord record : attendanceList) {
                        if (!record.isStatusChanged() && !isNewSession(sessionId)) {
                            continue;
                        }

                        ps.setObject(1, UUID.randomUUID());
                        ps.setObject(2, sessionId);
                        ps.setObject(3, UUID.fromString(record.getLearnerId()));
                        ps.setObject(4, SessionManager.getCurrentSchoolId());
                        ps.setObject(5, record.getStatus()); // Use setObject instead of setString
                        ps.setObject(6, SessionManager.getCurrentUserId());
                        ps.addBatch();
                        hasChanges = true;
                    }

                    if (hasChanges) {
                        ps.executeBatch();
                        logger.info("Saved attendance for session {}, year {}, term {}", sessionId, selectedYear, selectedTerm);
                        showInfo("Attendance saved successfully!");
                        EventBus.publish(EventTypes.CLUB_STATS_UPDATED, clubId);

                        for (AttendanceRecord record : attendanceList) {
                            record.resetStatusChanged();
                        }
                    } else {
                        showInfo("No changes to save.");
                    }
                }
            } catch (Exception e2) {
                logger.error("Error saving attendance with alternative approach: {}", e2.getMessage(), e2);
                showError("Error saving attendance: " + e2.getMessage());
            }
        }
    }

        // Add this method
        @FXML
        private void refreshData() {
            LocalDate currentDate = sessionDatePicker.getValue();
            if (currentDate != null) {
                loadSessionsForDate(currentDate);
                Session selectedSession = sessionComboBox.getSelectionModel().getSelectedItem();
                if (selectedSession != null) {
                    loadAttendanceData(selectedSession.getSessionId());
                }
                showInfo("Data refreshed successfully.");
            }
        }

    private boolean validateSessionAgainstSchedules(LocalDate sessionDate) {
        if (clubId == null || sessionDate == null) {
            return false;
        }

        try (Connection conn = DatabaseConnector.getConnection()) {
            // Get the day of the week for the session date (e.g., "MON", "TUE", etc.)
            String dayOfWeek = sessionDate.getDayOfWeek().toString().substring(0, 3).toUpperCase();

            String sql = """
            SELECT COUNT(*) as matching_schedules
            FROM club_schedules 
            WHERE club_id = ? 
            AND meeting_day = ?
            AND is_active = true
        """;

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, clubId);
                ps.setString(2, dayOfWeek);
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    int matchingSchedules = rs.getInt("matching_schedules");
                    logger.debug("Found {} matching schedules for {} on {}", matchingSchedules, dayOfWeek, sessionDate);
                    return matchingSchedules > 0;
                }
            }
        } catch (Exception e) {
            logger.error("Error validating session against schedules: {}", e.getMessage(), e);
        }
        return false;
    }

    private String getScheduledDays() {
        if (clubId == null) {
            return "No club selected";
        }

        try (Connection conn = DatabaseConnector.getConnection()) {
            String sql = """
            SELECT DISTINCT meeting_day
            FROM club_schedules 
            WHERE club_id = ? AND is_active = true
            ORDER BY meeting_day
        """;

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, clubId);
                ResultSet rs = ps.executeQuery();

                List<String> days = new ArrayList<>();
                while (rs.next()) {
                    days.add(rs.getString("meeting_day"));
                }

                if (days.isEmpty()) {
                    return "No scheduled days";
                }
                return String.join(", ", days);
            }
        } catch (Exception e) {
            logger.error("Error getting scheduled days: {}", e.getMessage(), e);
            return "Error loading schedule";
        }
    }

    private boolean isNewSession(UUID sessionId) {
        // Check if this session has any existing attendance records
        try (Connection conn = DatabaseConnector.getConnection()) {
            String sql = "SELECT 1 FROM attendance_records WHERE session_id = ? LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, sessionId);
                ResultSet rs = ps.executeQuery();
                return !rs.next(); // If no records exist, it's a new session
            }
        } catch (Exception e) {
            logger.error("Error checking if session is new: {}", e.getMessage(), e);
            return true; // Assume it's new if there's an error
        }
    }

    @FXML
    private void closeWindow() {
        // Check for unsaved changes
        boolean hasUnsavedChanges = attendanceList.stream()
                .anyMatch(AttendanceRecord::isStatusChanged);

        if (hasUnsavedChanges) {
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
            confirmation.setTitle("Unsaved Changes");
            confirmation.setHeaderText("You have unsaved attendance changes");
            confirmation.setContentText("Are you sure you want to close without saving?");

            if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                return;
            }
        }

        Stage stage = (Stage) attendanceTable.getScene().getWindow();
        stage.close();
    }

    private void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private void showInfo(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Information");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    // Inner classes
    public static class AttendanceRecord {
        private final String learnerId;
        private final String fullName;
        private final String grade;
        private String status;
        private String originalStatus;

        public AttendanceRecord(String learnerId, String fullName, String grade, String status) {
            this.learnerId = learnerId;
            this.fullName = fullName;
            this.grade = grade;
            this.status = status;
            this.originalStatus = status;
        }

        public String getLearnerId() { return learnerId; }
        public String getFullName() { return fullName; }
        public String getGrade() { return grade; }
        public String getStatus() { return status; }

        public void setStatus(String status) {
            this.status = status;
        }

        public boolean isStatusChanged() {
            return !status.equals(originalStatus);
        }

        public void resetStatusChanged() {
            this.originalStatus = this.status;
        }
    }

    private static class Session {
        private final UUID sessionId;
        private final LocalDate sessionDate;
        private final LocalTime startTime;
        private final LocalTime endTime;
        private final String notes;

        public Session(UUID sessionId, LocalDate sessionDate, LocalTime startTime, LocalTime endTime, String notes) {
            this.sessionId = sessionId;
            this.sessionDate = sessionDate;
            this.startTime = startTime;
            this.endTime = endTime;
            this.notes = notes;
        }

        public UUID getSessionId() { return sessionId; }
        public LocalDate getSessionDate() { return sessionDate; }
        public LocalTime getStartTime() { return startTime; }
        public LocalTime getEndTime() { return endTime; }
        public String getNotes() { return notes; }

        public String getDisplayName() {
            return String.format("%s %s - %s", sessionDate, startTime, endTime);
        }
    }
}