package com.cms.clubmanagementsystem.controller;

import com.cms.clubmanagementsystem.utils.DatabaseConnector;
import com.cms.clubmanagementsystem.utils.SessionManager;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.UUID;

/**
 * Controller for detailed learner attendance view
 */
public class LearnerAttendanceDetailController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(LearnerAttendanceDetailController.class);

    // Constants
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy");
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMM yyyy");
    private static final DateTimeFormatter FILE_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter EXPORT_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // UI Components
    @FXML private Label learnerNameLabel;
    @FXML private Label admissionNumberLabel;
    @FXML private Label gradeLabel;
    @FXML private Label enrollmentDateLabel;
    @FXML private Label statusLabel;

    @FXML private Label totalSessionsLabel;
    @FXML private Label presentCountLabel;
    @FXML private Label absentCountLabel;
    @FXML private Label attendanceRateLabel;

    @FXML private TableView<AttendanceSession> attendanceTable;
    @FXML private TableColumn<AttendanceSession, String> dateColumn;
    @FXML private TableColumn<AttendanceSession, String> dayColumn;
    @FXML private TableColumn<AttendanceSession, String> statusColumn;
    @FXML private TableColumn<AttendanceSession, String> markedByColumn;
    @FXML private TableColumn<AttendanceSession, String> notesColumn;

    @FXML private BarChart<String, Number> attendanceChart;
    @FXML private CategoryAxis monthAxis;
    @FXML private NumberAxis percentageAxis;

    @FXML private ComboBox<String> timePeriodFilter;
    @FXML private ComboBox<String> statusFilter;
    @FXML private DatePicker fromDatePicker;
    @FXML private DatePicker toDatePicker;
    @FXML private VBox loadingIndicator;
    @FXML private VBox contentArea;

    // Data
    private final ObservableList<AttendanceSession> allSessions = FXCollections.observableArrayList();
    private final ObservableList<AttendanceSession> filteredSessions = FXCollections.observableArrayList();

    // State
    private LearnersTabController.Learner learner;
    private UUID clubId;
    private String clubName;
    private int selectedYear;
    private int selectedTerm;
    private AttendanceStatistics statistics;
    private boolean dataInitialized = false;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("Initializing LearnerAttendanceDetailController");
        initializeUI();
        initializeFilters();
        initializeChart();
    }

    public void setLearnerData(LearnersTabController.Learner learner, UUID clubId, String clubName, int year, int term) {
        try {
            // Prevent multiple initialization
            if (dataInitialized) {
                logger.warn("Attempted to reinitialize learner data, ignoring.");
                return;
            }

            logger.info("Setting learner data: {}, club: {}, year: {}, term: {}",
                    learner.getFullName(), clubName, year, term);

            // Validate inputs
            if (learner == null) {
                throw new IllegalArgumentException("Learner cannot be null");
            }

            if (clubId == null) {
                throw new IllegalArgumentException("Club ID cannot be null");
            }

            // Store the data
            this.learner = learner;
            this.clubId = clubId;
            this.clubName = clubName != null ? clubName : "Unknown Club";
            this.selectedYear = year > 0 ? year : LocalDate.now().getYear();
            this.selectedTerm = (term >= 1 && term <= 3) ? term : 1;

            logger.info("Validated data - Year: {}, Term: {}", this.selectedYear, this.selectedTerm);

            // Mark as initialized
            dataInitialized = true;

            // Update UI immediately on the JavaFX thread
            Platform.runLater(() -> {
                updateLearnerInfo();
                setupDatePickers();
                loadAttendanceData(); // This will now use the captured session context
            });

        } catch (Exception e) {
            logger.error("Error setting learner data: {}", e.getMessage(), e);
            Platform.runLater(() -> {
                showError("Data Error", "Failed to set learner data: " + e.getMessage());
            });
        }
    }

    private void initializeUI() {
        initializeTableColumns();
        setupDatePickers();
    }

    private void initializeTableColumns() {
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("sessionDate"));
        dayColumn.setCellValueFactory(new PropertyValueFactory<>("dayOfWeek"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        markedByColumn.setCellValueFactory(new PropertyValueFactory<>("markedBy"));
        notesColumn.setCellValueFactory(new PropertyValueFactory<>("notes"));

        // Custom cell factory for status column
        statusColumn.setCellFactory(column -> new TableCell<AttendanceSession, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if ("Present".equals(item)) {
                        setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                    }
                }
            }
        });

        // Wrap text for notes column
        notesColumn.setCellFactory(tc -> {
            TableCell<AttendanceSession, String> cell = new TableCell<AttendanceSession, String>() {
                private Text text = new Text();

                {
                    setGraphic(text);
                    setPrefHeight(Control.USE_COMPUTED_SIZE);
                    text.wrappingWidthProperty().bind(notesColumn.widthProperty().subtract(10));
                }

                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        text.setText(null);
                    } else {
                        text.setText(item);
                    }
                }
            };
            return cell;
        });
    }

    private void setupDatePickers() {
        try {
            logger.info("Setting up date pickers for year: {}, term: {}", selectedYear, selectedTerm);

            // Validate year and term
            if (selectedYear <= 0) {
                selectedYear = LocalDate.now().getYear();
                logger.warn("Invalid year, using current year: {}", selectedYear);
            }

            if (selectedTerm < 1 || selectedTerm > 3) {
                selectedTerm = 1;
                logger.warn("Invalid term, using term 1: {}", selectedTerm);
            }

            // Set date range for the term
            LocalDate termStart = calculateTermStartDate(selectedYear, selectedTerm);
            LocalDate termEnd = calculateTermEndDate(selectedYear, selectedTerm);

            logger.info("Term date range: {} to {}", termStart, termEnd);

            // Set date picker boundaries
            fromDatePicker.setDayCellFactory(picker -> createDayCellFactory(termStart, termEnd));
            toDatePicker.setDayCellFactory(picker -> createDayCellFactory(termStart, termEnd));

            // Set initial values
            fromDatePicker.setValue(termStart);
            toDatePicker.setValue(termEnd);

            // Add listeners
            fromDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> filterAttendanceData());
            toDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> filterAttendanceData());

        } catch (Exception e) {
            logger.error("Error setting up date pickers: {}", e.getMessage(), e);
            // Set safe default values
            LocalDate today = LocalDate.now();
            fromDatePicker.setValue(today.minusMonths(1));
            toDatePicker.setValue(today);
        }
    }

    private void filterAttendanceData() {
        logger.info("Filtering attendance data");
        applyFilters();
    }

    private DateCell createDayCellFactory(LocalDate minDate, LocalDate maxDate) {
        return new DateCell() {
            @Override
            public void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                if (item.isBefore(minDate) || item.isAfter(maxDate)) {
                    setDisable(true);
                    setStyle("-fx-background-color: #ffc0cb;"); // Light red for disabled dates
                }
            }
        };
    }

    private LocalDate calculateTermStartDate() {
        return calculateTermStartDate(selectedYear, selectedTerm);
    }

    private LocalDate calculateTermEndDate(int year, int term) {
        try {
            logger.info("Calculating term end date for year: {}, term: {}", year, term);

            // Validate inputs
            if (year <= 0) {
                year = LocalDate.now().getYear();
            }

            if (term < 1 || term > 3) {
                term = 1;
            }

            // Calculate term end dates
            // Term 1 ends in April, Term 2 ends in August, Term 3 ends in December
            int month;
            switch (term) {
                case 1:
                    month = 4; // April
                    break;
                case 2:
                    month = 8; // August
                    break;
                case 3:
                    month = 12; // December
                    break;
                default:
                    month = 4; // Default to April
            }

            // Get the last day of the month
            LocalDate termEnd = LocalDate.of(year, month, 1).with(TemporalAdjusters.lastDayOfMonth());
            logger.info("Calculated term end date: {}", termEnd);
            return termEnd;

        } catch (DateTimeException e) {
            logger.error("Error calculating term end date for year: {}, term: {}: {}", year, term, e.getMessage());
            // Fallback to current date
            return LocalDate.now().with(TemporalAdjusters.lastDayOfMonth());
        }
    }

    private LocalDate calculateTermStartDate(int year, int term) {
        try {
            logger.info("Calculating term start date for year: {}, term: {}", year, term);

            // Validate inputs
            if (year <= 0) {
                logger.warn("Invalid year: {}, using current year", year);
                year = LocalDate.now().getYear();
            }

            if (term < 1 || term > 3) {
                logger.warn("Invalid term: {}, using term 1", term);
                term = 1;
            }

            // Calculate term start dates based on academic calendar
            // Term 1: January, Term 2: May, Term 3: September
            int month;
            switch (term) {
                case 1:
                    month = 1; // January
                    break;
                case 2:
                    month = 5; // May
                    break;
                case 3:
                    month = 9; // September
                    break;
                default:
                    month = 1; // Default to January
            }

            LocalDate termStart = LocalDate.of(year, month, 1);
            logger.info("Calculated term start date: {}", termStart);
            return termStart;

        } catch (DateTimeException e) {
            logger.error("Error calculating term start date for year: {}, term: {}: {}", year, term, e.getMessage());
            // Fallback to current date
            return LocalDate.now().withDayOfMonth(1);
        }
    }

    private LocalDate calculateTermEndDate() {
        int endMonth = selectedTerm * 4; // Apr, Aug, Dec for terms 1,2,3
        return LocalDate.of(selectedYear, endMonth, 1).plusMonths(1).minusDays(1);
    }

    private void initializeFilters() {
        // Time period filter
        timePeriodFilter.setItems(FXCollections.observableArrayList(
                "All Time", "This Term", "Last 30 Days", "Last 7 Days", "Custom"
        ));
        timePeriodFilter.setValue("This Term");
        timePeriodFilter.valueProperty().addListener((obs, oldVal, newVal) -> handleTimePeriodChange(newVal));

        // Status filter
        statusFilter.setItems(FXCollections.observableArrayList(
                "All", "Present", "Absent"
        ));
        statusFilter.setValue("All");
        statusFilter.valueProperty().addListener((obs, oldVal, newVal) -> applyFilters());
    }

    private void handleTimePeriodChange(String period) {
        LocalDate fromDate = null;
        LocalDate toDate = LocalDate.now();

        switch (period) {
            case "This Term":
                fromDate = calculateTermStartDate();
                break;
            case "Last 30 Days":
                fromDate = LocalDate.now().minusDays(30);
                break;
            case "Last 7 Days":
                fromDate = LocalDate.now().minusDays(7);
                break;
            case "All Time":
                fromDate = null;
                break;
            case "Custom":
                // Keep current custom dates
                return;
        }

        fromDatePicker.setValue(fromDate);
        toDatePicker.setValue(toDate);
        applyFilters();
    }

    private void initializeChart() {
        monthAxis.setLabel("Month");
        percentageAxis.setLabel("Attendance Rate (%)");
        percentageAxis.setAutoRanging(false);
        percentageAxis.setLowerBound(0);
        percentageAxis.setUpperBound(100);
        percentageAxis.setTickUnit(20);
    }

    private void updateLearnerInfo() {
        if (learner != null) {
            learnerNameLabel.setText(learner.getFullName());
            admissionNumberLabel.setText(learner.getAdmissionNumber());
            gradeLabel.setText(learner.getGrade());
            enrollmentDateLabel.setText(learner.getEnrollmentDate());
            statusLabel.setText(learner.getStatus());

            // Style status label
            if ("Active".equals(learner.getStatus())) {
                statusLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
            } else {
                statusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
            }
        }
    }

    private void loadAttendanceData() {
        logger.info("Loading attendance data for learner: {}", learner != null ? learner.getFullName() : "Unknown");

        if (learner == null || clubId == null) {
            logger.error("❌ Missing required data - learner: {}, clubId: {}", learner, clubId);
            showError("Data Error", "Learner or club data not available. Please try reopening the attendance view.");
            return;
        }

        // Validate admission number
        if (learner.getAdmissionNumber() == null || learner.getAdmissionNumber().trim().isEmpty()) {
            logger.error("❌ Missing admission number for learner: {}", learner.getFullName());
            showError("Data Error", "Learner admission number is missing. Cannot load attendance data.");
            return;
        }

        // Capture session context BEFORE spawning thread
        final UUID currentSchoolId = SessionManager.getCurrentSchoolId();
        final UUID currentUserId = SessionManager.getCurrentUserId();
        final String admissionNumber = learner.getAdmissionNumber();

        if (currentSchoolId == null) {
            logger.error("❌ No school context available");
            showError("Session Error", "No school context available. Please log in again.");
            return;
        }

        logger.info("Captured session context - School: {}, User: {}, Admission: {}",
                currentSchoolId, currentUserId, admissionNumber);

        showLoading(true);

        new Thread(() -> {
            try (Connection conn = DatabaseConnector.getConnection()) {
                // Use the CAPTURED session context, don't call SessionManager in background thread
                setTenantContext(conn, currentSchoolId, currentUserId);

                loadAttendanceSessions(conn, currentSchoolId, admissionNumber);
                calculateStatistics();
                updateChart();

                Platform.runLater(() -> {
                    attendanceTable.setItems(filteredSessions);
                    applyFilters();
                    updateStatisticsDisplay();
                    showLoading(false);
                });

            } catch (Exception e) {
                logger.error("Error loading attendance data for admission number {}: {}", admissionNumber, e.getMessage(), e);
                Platform.runLater(() -> {
                    showError("Data Load Error",
                            "Failed to load attendance data for " + learner.getFullName() +
                                    "\n\nError: " + e.getMessage());
                    showLoading(false);
                });
            }
        }).start();
    }

    private void loadAttendanceSessions(Connection conn, UUID schoolId, String admissionNumber) throws Exception {
        logger.info("Loading attendance sessions for learner: {}, club: {}, school: {}",
                admissionNumber, clubId, schoolId);

        // Get learner ID first with proper error handling
        UUID learnerId;
        try {
            learnerId = getLearnerIdFromAdmissionNumber(conn, schoolId, admissionNumber);
        } catch (Exception e) {
            logger.error("Failed to get learner ID: {}", e.getMessage());
            throw new Exception("Cannot load attendance: " + e.getMessage(), e);
        }

        String sql = """
    SELECT 
        ass.session_date,
        ass.start_time,
        ass.end_time,
        ar.status,
        ar.marked_at,
        u.full_name as marked_by,
        ass.notes
    FROM attendance_sessions ass
    JOIN attendance_records ar ON ass.session_id = ar.session_id
    LEFT JOIN users u ON ar.marked_by = u.user_id
    WHERE ar.learner_id = ?
    AND ass.club_id = ?
    AND ass.school_id = ?
    ORDER BY ass.session_date DESC, ass.start_time DESC
    """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, learnerId);
            ps.setObject(2, clubId);
            ps.setObject(3, schoolId);

            ResultSet rs = ps.executeQuery();
            allSessions.clear();
            int count = 0;

            while (rs.next()) {
                AttendanceSession session = createAttendanceSessionFromResultSet(rs);
                allSessions.add(session);
                count++;
            }

            logger.info("Loaded {} attendance sessions for learner {}", count, learner.getFullName());
        }
    }

    private void setTenantContext(Connection conn, UUID schoolId, UUID userId) throws Exception {
        logger.info("Setting tenant context - School: {}, User: {}", schoolId, userId);

        if (schoolId != null) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT set_config('app.current_school_id', ?, false)")) {
                ps.setString(1, schoolId.toString());
                ps.execute();
                logger.info("✅ Set app.current_school_id = {}", schoolId);
            }
        }

        if (userId != null) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT set_config('app.current_user_id', ?, false)")) {
                ps.setString(1, userId.toString());
                ps.execute();
                logger.info("✅ Set app.current_user_id = {}", userId);
            }
        }
    }

    private UUID getLearnerIdFromAdmissionNumber(Connection conn, UUID schoolId, String admissionNumber) throws Exception {
        logger.info("Looking up learner ID for admission number: {} in school: {}", admissionNumber, schoolId);

        if (schoolId == null) {
            throw new Exception("No school context available");
        }

        if (admissionNumber == null || admissionNumber.trim().isEmpty()) {
            throw new Exception("Admission number is required");
        }

        String sql = "SELECT learner_id FROM learners WHERE admission_number = ? AND school_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, admissionNumber.trim());
            ps.setObject(2, schoolId);

            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                UUID learnerId = (UUID) rs.getObject("learner_id");
                logger.info("✅ Found learner ID: {} for admission number: {}", learnerId, admissionNumber);
                return learnerId;
            } else {
                // More detailed debugging
                logger.error("❌ No learner found with admission number: '{}' in school: {}", admissionNumber, schoolId);

                // Check if the school exists and has any learners
                String debugSql = "SELECT COUNT(*) as learner_count FROM learners WHERE school_id = ?";
                try (PreparedStatement countStmt = conn.prepareStatement(debugSql)) {
                    countStmt.setObject(1, schoolId);
                    ResultSet countRs = countStmt.executeQuery();
                    if (countRs.next()) {
                        int totalLearners = countRs.getInt("learner_count");
                        logger.info("📊 Total learners in school {}: {}", schoolId, totalLearners);
                    }
                }

                throw new Exception("Learner not found with admission number: " + admissionNumber +
                        ". Please verify the admission number is correct.");
            }
        } catch (SQLException e) {
            logger.error("❌ Database error looking up learner: {}", e.getMessage(), e);
            throw new Exception("Database error while looking up learner: " + e.getMessage());
        }
    }

    private AttendanceSession createAttendanceSessionFromResultSet(ResultSet rs) throws Exception {
        LocalDate sessionDate = rs.getDate("session_date").toLocalDate();
        String status = rs.getString("status");
        String markedBy = rs.getString("marked_by");
        String notes = rs.getString("notes");

        return new AttendanceSession(sessionDate, status, markedBy, notes);
    }

    private void calculateStatistics() {
        int total = allSessions.size();
        long presentCount = allSessions.stream().filter(s -> "present".equalsIgnoreCase(s.getStatus())).count();
        long absentCount = total - presentCount;
        double attendanceRate = total > 0 ? (double) presentCount / total * 100 : 0;

        statistics = new AttendanceStatistics(total, (int) presentCount, (int) absentCount, attendanceRate);
    }

    private void applyFilters() {
        LocalDate fromDate = fromDatePicker.getValue();
        LocalDate toDate = toDatePicker.getValue();
        String statusFilterValue = statusFilter.getValue();

        filteredSessions.clear();

        for (AttendanceSession session : allSessions) {
            if (matchesDateRange(session, fromDate, toDate) &&
                    matchesStatus(session, statusFilterValue)) {
                filteredSessions.add(session);
            }
        }

        updateFilteredStatistics();
        logger.debug("Applied filters: {} sessions match criteria", filteredSessions.size());
    }

    private boolean matchesDateRange(AttendanceSession session, LocalDate fromDate, LocalDate toDate) {
        LocalDate sessionDate = session.getRawDate();

        if (fromDate == null && toDate == null) {
            return true; // No date filter applied
        }

        boolean afterFrom = fromDate == null || !sessionDate.isBefore(fromDate);
        boolean beforeTo = toDate == null || !sessionDate.isAfter(toDate);
        return afterFrom && beforeTo;
    }

    private boolean matchesStatus(AttendanceSession session, String statusFilter) {
        if ("All".equals(statusFilter)) return true;
        if (statusFilter == null || session.getStatus() == null) return false;
        return session.getStatus().equalsIgnoreCase(statusFilter);
    }

    private void updateFilteredStatistics() {
        int total = filteredSessions.size();
        long presentCount = filteredSessions.stream().filter(s -> "Present".equalsIgnoreCase(s.getStatus())).count();
        long absentCount = total - presentCount;
        double attendanceRate = total > 0 ? (double) presentCount / total * 100 : 0;

        Platform.runLater(() -> {
            totalSessionsLabel.setText(String.valueOf(total));
            presentCountLabel.setText(String.valueOf(presentCount));
            absentCountLabel.setText(String.valueOf(absentCount));
            attendanceRateLabel.setText(String.format("%.1f%%", attendanceRate));
        });
    }

    private void updateStatisticsDisplay() {
        if (statistics != null) {
            Platform.runLater(() -> {
                totalSessionsLabel.setText(String.valueOf(statistics.getTotalSessions()));
                presentCountLabel.setText(String.valueOf(statistics.getPresentCount()));
                absentCountLabel.setText(String.valueOf(statistics.getAbsentCount()));
                attendanceRateLabel.setText(String.format("%.1f%%", statistics.getAttendanceRate()));
            });
        }
    }

    private void updateChart() {
        Platform.runLater(() -> {
            attendanceChart.getData().clear();

            if (allSessions.isEmpty()) {
                return;
            }

            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName("Monthly Attendance Rate");

            Map<String, MonthlyStats> monthlyStats = calculateMonthlyStats();

            for (Map.Entry<String, MonthlyStats> entry : monthlyStats.entrySet()) {
                String month = entry.getKey();
                MonthlyStats stats = entry.getValue();
                double rate = stats.getAttendanceRate();

                XYChart.Data<String, Number> data = new XYChart.Data<>(month, rate);
                series.getData().add(data);
            }

            attendanceChart.getData().add(series);

            // Style the chart
            for (XYChart.Data<String, Number> data : series.getData()) {
                data.nodeProperty().addListener((obs, oldNode, newNode) -> {
                    if (newNode != null) {
                        double rate = data.getYValue().doubleValue();
                        if (rate >= 80) {
                            newNode.setStyle("-fx-bar-fill: #27ae60;");
                        } else if (rate >= 60) {
                            newNode.setStyle("-fx-bar-fill: #f39c12;");
                        } else {
                            newNode.setStyle("-fx-bar-fill: #e74c3c;");
                        }
                    }
                });
            }
        });
    }

    private Map<String, MonthlyStats> calculateMonthlyStats() {
        Map<String, MonthlyStats> monthlyStats = new HashMap<>();

        for (AttendanceSession session : allSessions) {
            String monthKey = session.getRawDate().format(MONTH_FORMATTER);
            MonthlyStats stats = monthlyStats.getOrDefault(monthKey, new MonthlyStats());
            stats.addSession("Present".equalsIgnoreCase(session.getStatus()));
            monthlyStats.put(monthKey, stats);
        }

        return monthlyStats;
    }

    @FXML
    private void handleExportAttendance() {
        if (filteredSessions.isEmpty()) {
            showError("Export Error", "No attendance data to export");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Attendance Data");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV files (*.csv)", "*.csv")
        );

        String fileName = String.format("attendance_%s_%s.csv",
                learner.getFullName().replaceAll("\\s+", "_"),
                LocalDate.now().format(FILE_DATE_FORMATTER));
        fileChooser.setInitialFileName(fileName);

        File file = fileChooser.showSaveDialog(attendanceTable.getScene().getWindow());
        if (file != null) {
            exportAttendanceToCSV(file);
        }
    }

    private void exportAttendanceToCSV(File file) {
        logger.info("Exporting {} attendance records to CSV for learner: {}",
                filteredSessions.size(), learner.getFullName());

        Task<Void> exportTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                try (FileWriter writer = new FileWriter(file)) {
                    int totalRecords = filteredSessions.size();
                    int currentProgress = 0;

                    // Write header and metadata
                    updateProgress(5, 100);
                    writeExportHeader(writer);

                    // Write statistics
                    updateProgress(15, 100);
                    writeExportStatistics(writer);

                    // Write attendance details
                    updateProgress(20, 100);
                    writer.write("ATTENDANCE DETAILS\n");
                    writer.write("No.,Session Date,Day of Week,Status,Marked By,Notes\n");

                    int recordNumber = 1;
                    for (AttendanceSession session : filteredSessions) {
                        // Check if task was cancelled
                        if (isCancelled()) {
                            throw new IOException("Export cancelled by user");
                        }

                        String line = String.format("%d,\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                                recordNumber,
                                escapeCsv(session.getSessionDate()),
                                escapeCsv(session.getDayOfWeek()),
                                escapeCsv(session.getStatus()),
                                escapeCsv(session.getMarkedBy()),
                                escapeCsv(session.getNotes())
                        );
                        writer.write(line);
                        recordNumber++;

                        // Update progress - calculate progress between 20% and 80%
                        if (totalRecords > 0) {
                            double progress = 20 + (60.0 * recordNumber / totalRecords);
                            updateProgress(progress, 100);
                        }
                    }

                    // Write monthly breakdown
                    updateProgress(85, 100);
                    writeMonthlyBreakdown(writer);

                    updateProgress(100, 100);

                } catch (IOException e) {
                    logger.error("Error exporting attendance data to CSV: {}", e.getMessage(), e);
                    throw e;
                }
                return null;
            }
        };

        // Optional: Add progress bar to track export progress
        ProgressBar progressBar = new ProgressBar();
        progressBar.progressProperty().bind(exportTask.progressProperty());

        // Show progress in a dialog for large exports
        if (filteredSessions.size() > 50) {
            Dialog<Void> progressDialog = createProgressDialog(exportTask, progressBar);
            new Thread(exportTask).start();
            progressDialog.showAndWait();
        } else {
            new Thread(exportTask).start();
        }

        exportTask.setOnSucceeded(e -> {
            logger.info("Attendance CSV export completed successfully: {}", file.getAbsolutePath());
            showInfo("Export Successful",
                    String.format("Attendance data for %s exported successfully to:\n%s",
                            learner.getFullName(), file.getAbsolutePath()));
        });

        exportTask.setOnFailed(e -> {
            Throwable ex = exportTask.getException();
            logger.error("Export failed: {}", ex.getMessage(), ex);
            showError("Export Error",
                    "Failed to export attendance data: " + ex.getMessage());
        });
    }

    private Dialog<Void> createProgressDialog(Task<Void> exportTask, ProgressBar progressBar) {
        Dialog<Void> progressDialog = new Dialog<>();
        progressDialog.setTitle("Exporting Data");
        progressDialog.setHeaderText("Exporting attendance data to CSV...");

        VBox content = new VBox(10);
        content.setPadding(new javafx.geometry.Insets(20));
        content.getChildren().addAll(
                new Label("Please wait while we export the data..."),
                progressBar
        );

        progressDialog.getDialogPane().setContent(content);
        progressDialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);

        // Handle cancel button
        Button cancelButton = (Button) progressDialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        cancelButton.setOnAction(e -> {
            if (exportTask.isRunning()) {
                exportTask.cancel();
            }
        });

        // Close dialog when task completes
        exportTask.setOnSucceeded(e -> progressDialog.close());
        exportTask.setOnFailed(e -> progressDialog.close());
        exportTask.setOnCancelled(e -> progressDialog.close());

        return progressDialog;
    }

    private void writeExportHeader(FileWriter writer) throws IOException {
        writer.write("Learner Attendance Report\n");
        writer.write("Generated on:," + LocalDateTime.now().format(EXPORT_DATE_FORMATTER) + "\n");
        writer.write("Learner Name:," + escapeCsv(learner.getFullName()) + "\n");
        writer.write("Admission Number:," + escapeCsv(learner.getAdmissionNumber()) + "\n");
        writer.write("Grade:," + escapeCsv(learner.getGrade()) + "\n");
        writer.write("Club:," + escapeCsv(clubName) + "\n");
        writer.write("Academic Year:," + selectedYear + "\n");
        writer.write("Term:," + selectedTerm + "\n");
        writer.write("Date Range:," + getFormattedDateRange() + "\n");
        writer.write("Filter Criteria:," + getFilterCriteria() + "\n");
        writer.write("\n");
    }

    private void writeExportStatistics(FileWriter writer) throws IOException {
        writer.write("ATTENDANCE SUMMARY\n");
        writer.write("Total Sessions:," + filteredSessions.size() + "\n");

        long presentCount = filteredSessions.stream()
                .filter(s -> "Present".equalsIgnoreCase(s.getStatus()))
                .count();
        long absentCount = filteredSessions.size() - presentCount;
        double attendanceRate = filteredSessions.size() > 0 ?
                (double) presentCount / filteredSessions.size() * 100 : 0;

        writer.write("Present:," + presentCount + "\n");
        writer.write("Absent:," + absentCount + "\n");
        writer.write("Attendance Rate:," + String.format("%.1f%%", attendanceRate) + "\n");

        // Add performance indicators
        writer.write("Performance:," + getPerformanceIndicator(attendanceRate) + "\n");
        writer.write("\n");
    }

    private void writeMonthlyBreakdown(FileWriter writer) throws IOException {
        writer.write("\n");
        writer.write("MONTHLY BREAKDOWN\n");
        writer.write("Month,Total Sessions,Present,Absent,Attendance Rate,Performance\n");

        Map<String, MonthlyStats> monthlyStats = calculateMonthlyStatsForExport();
        for (Map.Entry<String, MonthlyStats> entry : monthlyStats.entrySet()) {
            String month = entry.getKey();
            MonthlyStats stats = entry.getValue();
            double rate = stats.getAttendanceRate();

            String monthlyLine = String.format("\"%s\",%d,%d,%d,\"%.1f%%\",\"%s\"\n",
                    escapeCsv(month),
                    stats.getTotalSessions(),
                    stats.getPresentSessions(),
                    stats.getAbsentSessions(),
                    rate,
                    getPerformanceIndicator(rate)
            );
            writer.write(monthlyLine);
        }
    }


    private Map<String, MonthlyStats> calculateMonthlyStatsForExport() {
        Map<String, MonthlyStats> monthlyStats = new HashMap<>();

        for (AttendanceSession session : filteredSessions) {
            String monthKey = session.getRawDate().format(MONTH_FORMATTER);
            MonthlyStats stats = monthlyStats.getOrDefault(monthKey, new MonthlyStats());
            stats.addSession("Present".equalsIgnoreCase(session.getStatus()));
            monthlyStats.put(monthKey, stats);
        }

        return monthlyStats;
    }

    private String getFormattedDateRange() {
        LocalDate fromDate = fromDatePicker.getValue();
        LocalDate toDate = toDatePicker.getValue();

        if (fromDate != null && toDate != null) {
            return String.format("%s to %s",
                    fromDate.format(DATE_FORMATTER),
                    toDate.format(DATE_FORMATTER));
        } else if (fromDate != null) {
            return String.format("From %s", fromDate.format(DATE_FORMATTER));
        } else if (toDate != null) {
            return String.format("Until %s", toDate.format(DATE_FORMATTER));
        } else {
            return "All dates";
        }
    }

    private String getFilterCriteria() {
        List<String> criteria = new ArrayList<>();

        String timePeriod = timePeriodFilter.getValue();
        if (timePeriod != null && !"Custom".equals(timePeriod)) {
            criteria.add("Period: " + timePeriod);
        }

        String status = statusFilter.getValue();
        if (status != null && !"All".equals(status)) {
            criteria.add("Status: " + status);
        }

        return criteria.isEmpty() ? "All records" : String.join("; ", criteria);
    }

    private String getPerformanceIndicator(double attendanceRate) {
        if (attendanceRate >= 90) return "Excellent";
        if (attendanceRate >= 80) return "Good";
        if (attendanceRate >= 70) return "Fair";
        if (attendanceRate >= 60) return "Needs Improvement";
        return "Poor";
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        return value.replace("\"", "\"\"");
    }

    @FXML
    private void handleClose() {
        logger.info("Closing learner attendance view");
        reset();
        Stage stage = (Stage) attendanceTable.getScene().getWindow();
        stage.close();
    }

    public void reset() {
        this.dataInitialized = false;
        this.learner = null;
        this.clubId = null;
        this.clubName = null;
        this.allSessions.clear();
        this.filteredSessions.clear();
        logger.info("LearnerAttendanceDetailController reset");
    }

    private void showLoading(boolean show) {
        Platform.runLater(() -> {
            loadingIndicator.setVisible(show);
            loadingIndicator.setManaged(show);
            contentArea.setVisible(!show);
            contentArea.setManaged(!show);
        });
    }

    private void showInfo(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private void showError(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    // Data Models
    public static class AttendanceSession {
        private final SimpleStringProperty sessionDate;
        private final SimpleStringProperty dayOfWeek;
        private final SimpleStringProperty status;
        private final SimpleStringProperty markedBy;
        private final SimpleStringProperty notes;
        private final LocalDate rawDate;

        public AttendanceSession(LocalDate sessionDate, String status, String markedBy, String notes) {
            this.rawDate = sessionDate;
            this.sessionDate = new SimpleStringProperty(sessionDate.format(DATE_FORMATTER));
            this.dayOfWeek = new SimpleStringProperty(sessionDate.getDayOfWeek().toString());
            this.status = new SimpleStringProperty(capitalize(status));
            this.markedBy = new SimpleStringProperty(markedBy != null ? markedBy : "System");
            this.notes = new SimpleStringProperty(notes != null ? notes : "");
        }

        private String capitalize(String str) {
            if (str == null || str.isEmpty()) return str;
            return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
        }

        // Getters
        public String getSessionDate() { return sessionDate.get(); }
        public String getDayOfWeek() { return dayOfWeek.get(); }
        public String getStatus() { return status.get(); }
        public String getMarkedBy() { return markedBy.get(); }
        public String getNotes() { return notes.get(); }
        public LocalDate getRawDate() { return rawDate; }
    }

    private static class AttendanceStatistics {
        private final int totalSessions;
        private final int presentCount;
        private final int absentCount;
        private final double attendanceRate;

        public AttendanceStatistics(int totalSessions, int presentCount, int absentCount, double attendanceRate) {
            this.totalSessions = totalSessions;
            this.presentCount = presentCount;
            this.absentCount = absentCount;
            this.attendanceRate = attendanceRate;
        }

        // Getters
        public int getTotalSessions() { return totalSessions; }
        public int getPresentCount() { return presentCount; }
        public int getAbsentCount() { return absentCount; }
        public double getAttendanceRate() { return attendanceRate; }
    }

    private static class MonthlyStats {
        private int totalSessions = 0;
        private int presentSessions = 0;
        private int absentSessions = 0;

        public void addSession(boolean isPresent) {
            totalSessions++;
            if (isPresent) {
                presentSessions++;
            } else {
                absentSessions++;
            }
        }

        public double getAttendanceRate() {
            return totalSessions > 0 ? (double) presentSessions / totalSessions * 100 : 0;
        }

        public int getTotalSessions() { return totalSessions; }
        public int getPresentSessions() { return presentSessions; }
        public int getAbsentSessions() { return absentSessions; }
    }
}