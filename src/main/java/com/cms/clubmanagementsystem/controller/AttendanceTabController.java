package com.cms.clubmanagementsystem.controller;

import com.cms.clubmanagementsystem.utils.DatabaseConnector;
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
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.UUID;
import javafx.scene.control.ProgressIndicator;

public class AttendanceTabController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(AttendanceTabController.class);

    // Constants
    private static final class Constants {
        static final String DATE_PATTERN = "yyyy-MM-dd";
        static final String DISPLAY_DATE_PATTERN = "MMM dd, yyyy";
        static final String EXPORT_DATE_PATTERN = "yyyyMMdd";
        static final int PAGE_SIZE = 100;
        static final int EXCEL_WINDOW_SIZE = 100; // For SXSSFWorkbook
    }

    // SQL Queries
    private static final String SESSIONS_SUMMARY_SQL = """
        SELECT 
            asess.session_id,
            asess.session_date,
            asess.start_time,
            asess.end_time,
            COUNT(ar.record_id) as total_records,
            COUNT(CASE WHEN ar.status = 'present' THEN 1 END) as present_count,
            COUNT(CASE WHEN ar.status = 'absent' THEN 1 END) as absent_count,
            COUNT(CASE WHEN ar.status = 'late' THEN 1 END) as late_count
        FROM attendance_sessions asess
        LEFT JOIN attendance_records ar ON asess.session_id = ar.session_id
        LEFT JOIN club_enrollments ce ON ar.learner_id = ce.learner_id 
            AND ce.club_id = asess.club_id 
            AND ce.academic_year = ?
            AND ce.term_number = ?
            AND ce.is_active = true
        WHERE asess.club_id = ?
        AND EXTRACT(YEAR FROM asess.session_date) = ?
        AND (ce.learner_id IS NOT NULL OR ar.record_id IS NULL)
    """;

    private static final String ALL_ATTENDANCE_RECORDS_SQL = """
        SELECT 
            l.full_name,
            g.grade_name,
            ar.status,
            TO_CHAR(asess.session_date, 'YYYY-MM-DD') as session_date,
            TO_CHAR(asess.session_date, 'Month') as month_name,
            asess.session_date as raw_date
        FROM attendance_records ar
        JOIN attendance_sessions asess ON ar.session_id = asess.session_id
        JOIN learners l ON ar.learner_id = l.learner_id
        JOIN grades g ON l.grade_id = g.grade_id
        JOIN club_enrollments ce ON l.learner_id = ce.learner_id 
            AND ce.club_id = ? 
            AND ce.academic_year = ?
            AND ce.term_number = ?
            AND ce.is_active = true
        WHERE asess.club_id = ?
        AND EXTRACT(YEAR FROM asess.session_date) = ?
    """;

    private static final String SESSION_DETAILS_SQL = """
        SELECT 
            l.full_name,
            g.grade_name,
            ar.status,
            TO_CHAR(asess.session_date, 'Mon dd, yyyy') as session_date
        FROM attendance_records ar
        JOIN attendance_sessions asess ON ar.session_id = asess.session_id
        JOIN learners l ON ar.learner_id = l.learner_id
        JOIN grades g ON l.grade_id = g.grade_id
        JOIN club_enrollments ce ON l.learner_id = ce.learner_id 
            AND ce.club_id = ? 
            AND ce.academic_year = ?
            AND ce.term_number = ?
            AND ce.is_active = true
        WHERE ar.session_id = ?
        ORDER BY g.grade_name, l.full_name
    """;

    private static final String EXPORT_DATA_SQL = """
        SELECT 
            asess.session_date,
            TO_CHAR(asess.start_time, 'HH24:MI') as start_time,
            TO_CHAR(asess.end_time, 'HH24:MI') as end_time,
            l.full_name as learner_name,
            g.grade_name as grade,
            ar.status,
            TO_CHAR(ar.marked_at, 'YYYY-MM-DD HH24:MI:SS') as marked_at,
            u.full_name as marked_by
        FROM attendance_records ar
        JOIN attendance_sessions asess ON ar.session_id = asess.session_id
        JOIN learners l ON ar.learner_id = l.learner_id
        JOIN grades g ON l.grade_id = g.grade_id
        JOIN club_enrollments ce ON l.learner_id = ce.learner_id 
            AND ce.club_id = ? 
            AND ce.academic_year = ?
            AND ce.term_number = ?
            AND ce.is_active = true
        LEFT JOIN users u ON ar.marked_by = u.user_id
        WHERE asess.club_id = ?
        ORDER BY asess.session_date DESC, g.grade_name, l.full_name
    """;

    // FXML Components
    @FXML private TableView<AttendanceSession> sessionsTable;
    @FXML private TableColumn<AttendanceSession, String> dateColumn;
    @FXML private TableColumn<AttendanceSession, String> timeColumn;
    @FXML private TableColumn<AttendanceSession, String> presentColumn;
    @FXML private TableColumn<AttendanceSession, String> absentColumn;
    @FXML private TableColumn<AttendanceSession, String> totalColumn;

    @FXML private TableView<AttendanceRecord> attendanceTable;
    @FXML private TableColumn<AttendanceRecord, String> learnerNameColumn;
    @FXML private TableColumn<AttendanceRecord, String> gradeColumn;
    @FXML private TableColumn<AttendanceRecord, String> statusColumn;
    @FXML private TableColumn<AttendanceRecord, String> sessionDateColumn;

    @FXML private ComboBox<String> monthComboBox;
    @FXML private ComboBox<String> yearComboBox;
    @FXML private Label summaryLabel;
    @FXML private Label sessionDetailsLabel;
    @FXML private Button previousPageButton;
    @FXML private Button nextPageButton;
    @FXML private Label pageInfoLabel;

    // Instance variables
    private UUID clubId;
    private String clubName;
    private int selectedYear;
    private int selectedTerm;
    private int currentPage = 0;
    private int totalRecords = 0;

    private ObservableList<AttendanceSession> sessionsList = FXCollections.observableArrayList();
    private ObservableList<AttendanceRecord> attendanceRecords = FXCollections.observableArrayList();

    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(Constants.DATE_PATTERN);
    private final DateTimeFormatter displayDateFormatter = DateTimeFormatter.ofPattern(Constants.DISPLAY_DATE_PATTERN);
    private final Properties exportConfig = new Properties();

    private Dialog<Void> progressDialog;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        try {
            loadExportConfig();
            setupTables();
            setupComboBoxes();
            setupTableListeners();
            setupPaginationControls();
            logger.info("AttendanceTabController initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize AttendanceTabController: {}", e.getMessage(), e);
            showError("Failed to initialize attendance tab. Please restart the application.");
        }
    }

    private void loadExportConfig() {
        try (InputStream input = getClass().getResourceAsStream("/export.properties")) {
            if (input != null) {
                exportConfig.load(input);
                logger.info("Export configuration loaded successfully");
            } else {
                logger.info("No export configuration found, using defaults");
                // Set default values
                exportConfig.setProperty("excel.window.size", "100");
                exportConfig.setProperty("export.page.size", "1000");
            }
        } catch (IOException e) {
            logger.warn("Could not load export properties, using defaults: {}", e.getMessage());
        }
    }

    private void setupTables() {
        try {
            // Setup sessions table
            dateColumn.setCellValueFactory(new PropertyValueFactory<>("formattedDate"));
            timeColumn.setCellValueFactory(new PropertyValueFactory<>("timeRange"));
            presentColumn.setCellValueFactory(new PropertyValueFactory<>("presentCount"));
            absentColumn.setCellValueFactory(new PropertyValueFactory<>("absentCount"));
            totalColumn.setCellValueFactory(new PropertyValueFactory<>("totalCount"));
            sessionsTable.setItems(sessionsList);

            // Setup attendance records table
            learnerNameColumn.setCellValueFactory(new PropertyValueFactory<>("learnerName"));
            gradeColumn.setCellValueFactory(new PropertyValueFactory<>("grade"));
            statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
            sessionDateColumn.setCellValueFactory(new PropertyValueFactory<>("sessionDate"));

            // Style status column
            statusColumn.setCellFactory(column -> new TableCell<AttendanceRecord, String>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setStyle("");
                    } else {
                        setText(item);
                        applyStatusStyle(item);
                    }
                }

                private void applyStatusStyle(String status) {
                    switch (status.toLowerCase()) {
                        case "present":
                            setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                            break;
                        case "absent":
                            setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                            break;
                        case "late":
                            setStyle("-fx-text-fill: #f39c12; -fx-font-weight: bold;");
                            break;
                        default:
                            setStyle("");
                    }
                }
            });

            attendanceTable.setItems(attendanceRecords);
        } catch (Exception e) {
            logger.error("Error setting up tables: {}", e.getMessage(), e);
            throw new RuntimeException("Table setup failed", e);
        }
    }

    private void setupComboBoxes() {
        try {
            // Setup year combo box
            int currentYear = LocalDate.now().getYear();
            for (int year = currentYear - 1; year <= currentYear + 1; year++) {
                yearComboBox.getItems().add(String.valueOf(year));
            }
            yearComboBox.setValue(String.valueOf(currentYear));

            // Setup month combo box
            monthComboBox.getItems().addAll(
                    "All Months", "January", "February", "March", "April", "May", "June",
                    "July", "August", "September", "October", "November", "December"
            );
            monthComboBox.setValue("All Months");

            // Add listeners
            yearComboBox.valueProperty().addListener((obs, oldVal, newVal) -> loadAttendanceData());
            monthComboBox.valueProperty().addListener((obs, oldVal, newVal) -> loadAttendanceData());
        } catch (Exception e) {
            logger.error("Error setting up combo boxes: {}", e.getMessage(), e);
            showError("Error initializing filter controls.");
        }
    }

    private void setupTableListeners() {
        sessionsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null && validateSessionData(newSelection)) {
                loadSessionAttendanceDetails(newSelection.getSessionId());
                sessionDetailsLabel.setText(String.format("Session: %s | %s",
                        newSelection.getFormattedDate(), newSelection.getTimeRange()));
            }
        });
    }

    private void setupPaginationControls() {
        updatePaginationControls();
    }

    private void updatePaginationControls() {
        if (previousPageButton != null && nextPageButton != null && pageInfoLabel != null) {
            previousPageButton.setDisable(currentPage == 0);
            nextPageButton.setDisable(attendanceRecords.size() < Constants.PAGE_SIZE);

            int startRecord = currentPage * Constants.PAGE_SIZE + 1;
            int endRecord = startRecord + attendanceRecords.size() - 1;
            pageInfoLabel.setText(String.format("Showing %d-%d", startRecord, endRecord));
        }
    }

    public void setClubId(UUID clubId) {
        this.clubId = clubId;
        loadAttendanceData();
    }

    public void setClubName(String clubName) {
        this.clubName = clubName;
    }

    public void setYearAndTerm(int year, int term) {
        this.selectedYear = year;
        this.selectedTerm = term;
        if (yearComboBox != null) {
            yearComboBox.setValue(String.valueOf(year));
        }
        loadAttendanceData();
    }

    private void loadAttendanceData() {
        if (!validateInputs()) {
            return;
        }

        sessionsList.clear();
        attendanceRecords.clear();
        currentPage = 0;

        String selectedMonth = monthComboBox.getValue();
        String selectedYearStr = yearComboBox.getValue();

        try (Connection conn = DatabaseConnector.getConnection()) {
            loadSessionsSummary(conn, selectedMonth, selectedYearStr);
            loadAttendanceRecordsWithPagination(currentPage, Constants.PAGE_SIZE, selectedMonth, selectedYearStr);
        } catch (SQLException e) {
            logger.error("Database error loading attendance data: {}", e.getMessage(), e);
            showError("Database connection error. Please try again.");
        } catch (Exception e) {
            logger.error("Unexpected error loading attendance data: {}", e.getMessage(), e);
            showError("An unexpected error occurred while loading attendance data.");
        }
    }

    private boolean validateInputs() {
        if (clubId == null) {
            logger.warn("Attempted to load attendance data without clubId");
            showError("No club assigned. Please contact administrator.");
            return false;
        }

        if (yearComboBox.getValue() == null || yearComboBox.getValue().trim().isEmpty()) {
            showError("Please select a valid year.");
            return false;
        }

        try {
            Integer.parseInt(yearComboBox.getValue());
            return true;
        } catch (NumberFormatException e) {
            showError("Invalid year format.");
            return false;
        }
    }

    private void loadSessionsSummary(Connection conn, String selectedMonth, String selectedYearStr) throws SQLException {
        StringBuilder sqlBuilder = new StringBuilder(SESSIONS_SUMMARY_SQL);

        if (!"All Months".equals(selectedMonth)) {
            sqlBuilder.append(" AND TO_CHAR(asess.session_date, 'Month') LIKE ?");
        }

        sqlBuilder.append(" GROUP BY asess.session_id, asess.session_date, asess.start_time, asess.end_time");
        sqlBuilder.append(" ORDER BY asess.session_date DESC");

        try (PreparedStatement ps = conn.prepareStatement(sqlBuilder.toString())) {
            int paramIndex = 1;
            ps.setInt(paramIndex++, selectedYear);
            ps.setInt(paramIndex++, selectedTerm);
            ps.setObject(paramIndex++, clubId);
            ps.setInt(paramIndex++, Integer.parseInt(selectedYearStr));

            if (!"All Months".equals(selectedMonth)) {
                ps.setString(paramIndex, selectedMonth.trim() + "%");
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AttendanceSession session = new AttendanceSession(
                            (UUID) rs.getObject("session_id"),
                            rs.getDate("session_date").toLocalDate(),
                            rs.getTime("start_time").toLocalTime(),
                            rs.getTime("end_time").toLocalTime(),
                            rs.getInt("present_count"),
                            rs.getInt("absent_count"),
                            rs.getInt("late_count"),
                            rs.getInt("total_records")
                    );

                    if (validateSessionData(session)) {
                        sessionsList.add(session);
                    }
                }
            }

            logger.info("Loaded {} sessions for club {}", sessionsList.size(), clubId);
        }
    }

    private void loadAttendanceRecordsWithPagination(int page, int pageSize, String selectedMonth, String selectedYearStr) {
        try (Connection conn = DatabaseConnector.getConnection()) {
            StringBuilder sqlBuilder = new StringBuilder(ALL_ATTENDANCE_RECORDS_SQL);

            if (!"All Months".equals(selectedMonth)) {
                sqlBuilder.append(" AND TO_CHAR(asess.session_date, 'Month') LIKE ?");
            }

            sqlBuilder.append(" ORDER BY asess.session_date DESC, l.full_name");
            sqlBuilder.append(" LIMIT ? OFFSET ?");

            try (PreparedStatement ps = conn.prepareStatement(sqlBuilder.toString())) {
                int paramIndex = 1;
                ps.setObject(paramIndex++, clubId);
                ps.setInt(paramIndex++, selectedYear);
                ps.setInt(paramIndex++, selectedTerm);
                ps.setObject(paramIndex++, clubId);
                ps.setInt(paramIndex++, Integer.parseInt(selectedYearStr));

                if (!"All Months".equals(selectedMonth)) {
                    ps.setString(paramIndex++, selectedMonth.trim() + "%");
                }

                ps.setInt(paramIndex++, pageSize);
                ps.setInt(paramIndex, page * pageSize);

                try (ResultSet rs = ps.executeQuery()) {
                    ObservableList<AttendanceRecord> newRecords = FXCollections.observableArrayList();
                    int presentCount = 0;
                    int absentCount = 0;
                    int lateCount = 0;
                    int totalCount = 0;

                    while (rs.next()) {
                        String status = rs.getString("status");
                        LocalDate sessionDate = rs.getDate("raw_date").toLocalDate();

                        newRecords.add(new AttendanceRecord(
                                rs.getString("full_name"),
                                rs.getString("grade_name"),
                                status,
                                sessionDate.format(displayDateFormatter)
                        ));

                        totalCount++;
                        switch (status.toLowerCase()) {
                            case "present": presentCount++; break;
                            case "absent": absentCount++; break;
                            case "late": lateCount++; break;
                        }
                    }

                    int finalPresentCount = presentCount;
                    int finalAbsentCount = absentCount;
                    int finalLateCount = lateCount;
                    int finalTotalCount = totalCount;
                    Platform.runLater(() -> {
                        attendanceRecords.setAll(newRecords);
                        updateSummary(finalPresentCount, finalAbsentCount, finalLateCount, finalTotalCount);
                        updatePaginationControls();
                    });

                    logger.info("Loaded {} attendance records for club {} (page {})",
                            totalCount, clubId, page);
                }
            }
        } catch (Exception e) {
            logger.error("Error loading paginated attendance records: {}", e.getMessage(), e);
            showError("Error loading attendance records.");
        }
    }

    private void loadSessionAttendanceDetails(UUID sessionId) {
        if (sessionId == null) return;

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(SESSION_DETAILS_SQL)) {

            ps.setObject(1, clubId);
            ps.setInt(2, selectedYear);
            ps.setInt(3, selectedTerm);
            ps.setObject(4, sessionId);

            try (ResultSet rs = ps.executeQuery()) {
                ObservableList<AttendanceRecord> sessionRecords = FXCollections.observableArrayList();
                int presentCount = 0;
                int absentCount = 0;
                int lateCount = 0;

                while (rs.next()) {
                    String status = rs.getString("status");
                    sessionRecords.add(new AttendanceRecord(
                            rs.getString("full_name"),
                            rs.getString("grade_name"),
                            status,
                            rs.getString("session_date")
                    ));

                    switch (status.toLowerCase()) {
                        case "present": presentCount++; break;
                        case "absent": absentCount++; break;
                        case "late": lateCount++; break;
                    }
                }

                // Calculate totalCount here in the outer scope
                int totalCount = presentCount + absentCount + lateCount;

                int finalPresentCount = presentCount;
                int finalAbsentCount = absentCount;
                int finalLateCount = lateCount;
                Platform.runLater(() -> {
                    attendanceTable.setItems(sessionRecords);

                    if (totalCount > 0) {
                        double presentPercentage = (double) finalPresentCount / totalCount * 100;
                        summaryLabel.setText(String.format(
                                "Session Summary: %d Present (%.1f%%), %d Absent, %d Late - Total: %d learners",
                                finalPresentCount, presentPercentage, finalAbsentCount, finalLateCount, totalCount
                        ));
                    } else {
                        summaryLabel.setText("No attendance records found for current enrollments");
                    }
                });

                // Now totalCount is accessible here
                logger.info("Loaded session details - Present: {}, Absent: {}, Late: {}, Total: {}",
                        presentCount, absentCount, lateCount, totalCount);
            }
        } catch (Exception e) {
            logger.error("Error loading session details: {}", e.getMessage(), e);
            showError("Error loading session details: " + e.getMessage());
        }
    }

    private boolean validateSessionData(AttendanceSession session) {
        if (session.getSessionDate() == null) {
            logger.warn("Session date is null for session: {}", session.getSessionId());
            return false;
        }

        if (session.getStartTime() == null || session.getEndTime() == null) {
            logger.warn("Invalid time range for session: {}", session.getSessionId());
            return false;
        }

        if (session.getStartTime().isAfter(session.getEndTime())) {
            logger.warn("Start time after end time for session: {}", session.getSessionId());
            return false;
        }

        return true;
    }

    private void updateSummary(int present, int absent, int late, int total) {
        Platform.runLater(() -> {
            if (total == 0) {
                summaryLabel.setText("No attendance records found for selected period");
                return;
            }

            double presentPercentage = (double) present / total * 100;
            double absentPercentage = (double) absent / total * 100;
            double latePercentage = (double) late / total * 100;

            summaryLabel.setText(String.format(
                    "Overall Summary: %d Present (%.1f%%), %d Absent (%.1f%%), %d Late (%.1f%%) - Total: %d records",
                    present, presentPercentage, absent, absentPercentage, late, latePercentage, total
            ));
        });
    }

    @FXML
    private void handleTakeAttendance() {
        if (!validateInputs()) {
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/attendance-management.fxml"));
            Parent root = loader.load();

            AttendanceController controller = loader.getController();
            controller.setClubId(clubId);
            controller.setClubName(clubName);
            controller.setYearAndTerm(selectedYear, selectedTerm);

            Stage stage = new Stage();
            stage.setTitle("Take Attendance - " + clubName);
            stage.setScene(new Scene(root, 1000, 700));
            stage.initModality(Modality.APPLICATION_MODAL);

            Stage currentStage = (Stage) sessionsTable.getScene().getWindow();
            stage.initOwner(currentStage);

            stage.showAndWait();

            // Refresh attendance data after taking attendance
            loadAttendanceData();

        } catch (IOException e) {
            showError("Failed to load attendance management: " + e.getMessage());
            logger.error("Error loading attendance management: {}", e.getMessage(), e);
        }
    }

    @FXML
    private void handleExportAttendance() {
        if (!validateInputs()) {
            return;
        }

        try {
            FileChooser fileChooser = createFileChooser("Export Attendance Data",
                    String.format("%s_attendance_%d_term%d_%s",
                            clubName.replaceAll("\\s+", "_"),
                            selectedYear,
                            selectedTerm,
                            LocalDate.now().format(DateTimeFormatter.ofPattern(Constants.EXPORT_DATE_PATTERN))));

            Stage stage = (Stage) sessionsTable.getScene().getWindow();
            File file = fileChooser.showSaveDialog(stage);

            if (file != null) {
                progressDialog = createExportProgressDialog();
                progressDialog.show();

                new Thread(() -> {
                    try {
                        exportAttendanceData(file);
                        Platform.runLater(() -> {
                            progressDialog.close();
                            showInfo("Attendance data exported successfully to: " + file.getAbsolutePath());
                        });
                    } catch (Exception e) {
                        Platform.runLater(() -> {
                            progressDialog.close();
                            logger.error("Error exporting attendance data: {}", e.getMessage(), e);
                            showError("Error exporting attendance data: " + e.getMessage());
                        });
                    }
                }).start();
            }

        } catch (Exception e) {
            logger.error("Error exporting attendance data: {}", e.getMessage(), e);
            showError("Error exporting attendance data: " + e.getMessage());
        }
    }

    @FXML
    private void handleExportSession() {
        AttendanceSession selectedSession = sessionsTable.getSelectionModel().getSelectedItem();
        if (selectedSession == null) {
            showError("Please select a session to export.");
            return;
        }

        if (!validateSessionData(selectedSession)) {
            showError("Invalid session data selected.");
            return;
        }

        try {
            FileChooser fileChooser = createFileChooser("Export Session Attendance",
                    String.format("%s_session_%s_%s",
                            clubName.replaceAll("\\s+", "_"),
                            selectedSession.getFormattedDate().replace(" ", "_"),
                            LocalDate.now().format(DateTimeFormatter.ofPattern(Constants.EXPORT_DATE_PATTERN))));

            Stage stage = (Stage) sessionsTable.getScene().getWindow();
            File file = fileChooser.showSaveDialog(stage);

            if (file != null) {
                progressDialog = createExportProgressDialog();
                progressDialog.show();

                new Thread(() -> {
                    try {
                        exportSessionData(selectedSession.getSessionId(), file);
                        Platform.runLater(() -> {
                            progressDialog.close();
                            showInfo("Session data exported successfully to: " + file.getAbsolutePath());
                        });
                    } catch (Exception e) {
                        Platform.runLater(() -> {
                            progressDialog.close();
                            logger.error("Error exporting session data: {}", e.getMessage(), e);
                            showError("Error exporting session data: " + e.getMessage());
                        });
                    }
                }).start();
            }

        } catch (Exception e) {
            logger.error("Error exporting session data: {}", e.getMessage(), e);
            showError("Error exporting session data: " + e.getMessage());
        }
    }

    private FileChooser createFileChooser(String title, String defaultFileName) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(title);
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("CSV Files", "*.csv"),
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        fileChooser.setInitialFileName(defaultFileName);
        return fileChooser;
    }

    private Dialog<Void> createExportProgressDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Exporting Data");
        dialog.setHeaderText("Please wait while we export your data...");

        // Use determinate progress indicator
        ProgressIndicator progressIndicator = new ProgressIndicator(0);
        progressIndicator.setPrefSize(50, 50);

        Label contentLabel = new Label("Preparing export...");
        contentLabel.setStyle("-fx-font-weight: bold;");

        VBox vbox = new VBox(10, progressIndicator, contentLabel);
        vbox.setAlignment(javafx.geometry.Pos.CENTER);
        vbox.setPadding(new javafx.geometry.Insets(20));

        dialog.getDialogPane().setContent(vbox);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);

        // Handle cancel button
        dialog.setResultConverter(buttonType -> {
            if (buttonType == ButtonType.CANCEL) {
                logger.info("Export operation cancelled by user");
            }
            return null;
        });

        return dialog;
    }

    private void exportAttendanceData(File file) {
        try (Connection conn = DatabaseConnector.getConnection()) {
            // Get total count first
            int totalRecords = getTotalRecordCount(conn, EXPORT_DATA_SQL, clubId, selectedYear, selectedTerm, clubId);

            // Add this missing logging statement
            logger.info("Starting export of {} records for club {}", totalRecords, clubId);

            try (PreparedStatement ps = conn.prepareStatement(EXPORT_DATA_SQL)) {
                ps.setObject(1, clubId);
                ps.setInt(2, selectedYear);
                ps.setInt(3, selectedTerm);
                ps.setObject(4, clubId);

                try (ResultSet rs = ps.executeQuery()) {
                    exportData(rs, file, getFileExtension(file.getName()), totalRecords);
                }
            }

        } catch (Exception e) {
            logger.error("Error exporting attendance data: {}", e.getMessage(), e);
            throw new RuntimeException("Export failed", e);
        }
    }

    private void exportSessionData(UUID sessionId, File file) {
        String sql = EXPORT_DATA_SQL.replace("asess.club_id = ?", "ar.session_id = ?");

        try (Connection conn = DatabaseConnector.getConnection()) {
            // Get total count first
            int totalRecords = getTotalRecordCount(conn, sql, clubId, selectedYear, selectedTerm, sessionId);

            logger.info("Starting session export of {} records for session {}", totalRecords, sessionId);

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, clubId);
                ps.setInt(2, selectedYear);
                ps.setInt(3, selectedTerm);
                ps.setObject(4, sessionId);

                try (ResultSet rs = ps.executeQuery()) {
                    exportData(rs, file, getFileExtension(file.getName()), totalRecords);
                }
            }

        } catch (Exception e) {
            logger.error("Error exporting session data: {}", e.getMessage(), e);
            throw new RuntimeException("Session export failed", e);
        }
    }

    private void exportData(ResultSet rs, File file, String exportType, int totalRecords) {
        try {
            if ("xlsx".equalsIgnoreCase(exportType)) {
                exportToExcel(rs, file, totalRecords);
            } else {
                if (!file.getName().toLowerCase().endsWith(".csv")) {
                    file = new File(file.getAbsolutePath() + ".csv");
                }
                exportToCsv(rs, file, totalRecords);
            }
        } catch (Exception e) {
            logger.error("Export failed for type {}: {}", exportType, e.getMessage(), e);
            throw new RuntimeException("Export failed", e);
        }
    }

    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(lastDot + 1).toLowerCase() : "csv";
    }

    private void exportToCsv(ResultSet rs, File file, int totalRecords) throws Exception {
        try (java.io.PrintWriter writer = new java.io.PrintWriter(file, "UTF-8")) {
            java.sql.ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            // Write CSV header
            for (int i = 1; i <= columnCount; i++) {
                String columnName = getDisplayColumnName(metaData.getColumnName(i));
                writer.print(escapeCsvValue(columnName));
                if (i < columnCount) writer.print(",");
            }
            writer.println();

            // Write data
            int processedCount = 0;

            // Show initial progress
            showExportProgress(0, totalRecords > 0 ? totalRecords : -1);

            while (rs.next()) {
                for (int i = 1; i <= columnCount; i++) {
                    String value = rs.getString(i);
                    writer.print(escapeCsvValue(value));
                    if (i < columnCount) writer.print(",");
                }
                writer.println();
                processedCount++;

                // Update progress - only update every 10 records to avoid UI overhead
                if (processedCount % 10 == 0 || processedCount == totalRecords) {
                    showExportProgress(processedCount, totalRecords > 0 ? totalRecords : -1);
                }
            }

            // Final progress update
            showExportProgress(processedCount, totalRecords > 0 ? totalRecords : -1);

            logger.info("Exported {} attendance records to CSV: {}", processedCount, file.getAbsolutePath());
        }
    }

    private void exportToExcel(ResultSet rs, File file, int totalRecords) throws Exception {
        int windowSize = Integer.parseInt(exportConfig.getProperty("excel.window.size",
                String.valueOf(Constants.EXCEL_WINDOW_SIZE)));

        try (Workbook workbook = new SXSSFWorkbook(windowSize)) {
            Sheet sheet = workbook.createSheet("Attendance Data");

            // Create styles
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);

            // Create header row
            Row headerRow = sheet.createRow(0);
            java.sql.ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            for (int i = 1; i <= columnCount; i++) {
                Cell cell = headerRow.createCell(i - 1);
                cell.setCellValue(getDisplayColumnName(metaData.getColumnName(i)));
                cell.setCellStyle(headerStyle);
            }

            // Write data rows
            int rowNum = 1;
            int processedCount = 0;

            // Show initial progress
            showExportProgress(0, totalRecords > 0 ? totalRecords : -1);

            while (rs.next()) {
                Row row = sheet.createRow(rowNum++);
                for (int i = 1; i <= columnCount; i++) {
                    Cell cell = row.createCell(i - 1);
                    String value = rs.getString(i);
                    cell.setCellValue(value != null ? value : "");
                    cell.setCellStyle(dataStyle);
                }

                processedCount++;

                // Update progress - only update every 10 records to avoid UI overhead
                if (processedCount % 10 == 0 || processedCount == totalRecords) {
                    showExportProgress(processedCount, totalRecords > 0 ? totalRecords : -1);
                }
            }

            // Final progress update
            showExportProgress(processedCount, totalRecords > 0 ? totalRecords : -1);

            // Auto-size columns
            for (int i = 0; i < columnCount; i++) {
                sheet.autoSizeColumn(i);
            }

            try (FileOutputStream outputStream = new FileOutputStream(file)) {
                workbook.write(outputStream);
            }

            logger.info("Exported {} attendance records to Excel: {}", processedCount, file.getAbsolutePath());
        }
    }

    private int getTotalRecordCount(Connection conn, String sql, Object... params) {
        try {
            // Create a count query from the original SQL
            String countSql = "SELECT COUNT(*) FROM (" + sql + ") AS count_query";
            try (PreparedStatement ps = conn.prepareStatement(countSql)) {
                for (int i = 0; i < params.length; i++) {
                    ps.setObject(i + 1, params[i]);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
        } catch (SQLException e) {
            logger.warn("Could not determine total record count, using indeterminate progress: {}", e.getMessage());
        }
        return -1; // Return -1 if count cannot be determined
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private String escapeCsvValue(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            value = "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String getDisplayColumnName(String dbColumnName) {
        switch (dbColumnName) {
            case "session_date": return "Session Date";
            case "start_time": return "Start Time";
            case "end_time": return "End Time";
            case "learner_name": return "Learner Name";
            case "grade": return "Grade";
            case "status": return "Status";
            case "marked_at": return "Marked At";
            case "marked_by": return "Marked By";
            default: return dbColumnName;
        }
    }

    private void showExportProgress(int current, int total) {
        Platform.runLater(() -> {
            if (progressDialog != null && progressDialog.isShowing()) {
                DialogPane dialogPane = progressDialog.getDialogPane();
                Node content = dialogPane.getContent();

                if (content instanceof VBox) {
                    VBox vbox = (VBox) content;

                    // Update or add progress label
                    Label progressLabel = findProgressLabel(vbox);
                    if (progressLabel == null) {
                        progressLabel = new Label();
                        progressLabel.setStyle("-fx-font-weight: bold;");
                        vbox.getChildren().add(progressLabel);
                    }

                    // Update progress text
                    if (total > 0) {
                        double progress = (double) current / total;
                        int percentage = (int) (progress * 100);

                        // Only update if the text actually changed to reduce UI updates
                        String newText = String.format("Processed %d of %d records (%d%%)", current, total, percentage);
                        if (!newText.equals(progressLabel.getText())) {
                            progressLabel.setText(newText);
                        }

                        ProgressIndicator progressIndicator = findProgressIndicator(vbox);
                        if (progressIndicator != null) {
                            progressIndicator.setProgress(progress);
                        }
                    } else {
                        String newText = String.format("Processed %d records...", current);
                        if (!newText.equals(progressLabel.getText())) {
                            progressLabel.setText(newText);
                        }
                    }
                }
            }
        });
    }

    // Helper method to find progress label in VBox
    private Label findProgressLabel(VBox vbox) {
        if (vbox == null) return null;

        for (Node node : vbox.getChildren()) {
            if (node instanceof Label) {
                Label label = (Label) node;
                String text = label.getText();
                if (text != null &&
                        (text.contains("records") || text.contains("Processed") || text.contains("Preparing"))) {
                    return label;
                }
            }
        }
        return null;
    }

    // Helper method to find progress indicator in VBox
    private ProgressIndicator findProgressIndicator(VBox vbox) {
        if (vbox == null) return null;

        for (Node node : vbox.getChildren()) {
            if (node instanceof ProgressIndicator) {
                return (ProgressIndicator) node;
            }
        }
        return null;
    }

    @FXML
    private void handleRefresh() {
        loadAttendanceData();
        showInfo("Attendance data refreshed");
    }

    @FXML
    private void handlePreviousPage() {
        if (currentPage > 0) {
            currentPage--;
            loadAttendanceRecordsWithPagination(currentPage, Constants.PAGE_SIZE,
                    monthComboBox.getValue(), yearComboBox.getValue());
        }
    }

    @FXML
    private void handleNextPage() {
        currentPage++;
        loadAttendanceRecordsWithPagination(currentPage, Constants.PAGE_SIZE,
                monthComboBox.getValue(), yearComboBox.getValue());
    }

    public void cleanup() {
        if (sessionsList != null) {
            sessionsList.clear();
        }
        if (attendanceRecords != null) {
            attendanceRecords.clear();
        }
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.close();
        }
        logger.info("AttendanceTabController cleaned up");
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

    // Inner classes for data models
    public static class AttendanceSession {
        private final UUID sessionId;
        private final LocalDate sessionDate;
        private final java.time.LocalTime startTime;
        private final java.time.LocalTime endTime;
        private final int presentCount;
        private final int absentCount;
        private final int lateCount;
        private final int totalCount;

        public AttendanceSession(UUID sessionId, LocalDate sessionDate, java.time.LocalTime startTime,
                                 java.time.LocalTime endTime, int presentCount, int absentCount,
                                 int lateCount, int totalCount) {
            this.sessionId = sessionId;
            this.sessionDate = sessionDate;
            this.startTime = startTime;
            this.endTime = endTime;
            this.presentCount = presentCount;
            this.absentCount = absentCount;
            this.lateCount = lateCount;
            this.totalCount = totalCount;
        }

        public UUID getSessionId() { return sessionId; }
        public String getFormattedDate() {
            return sessionDate.format(DateTimeFormatter.ofPattern(Constants.DISPLAY_DATE_PATTERN));
        }
        public String getTimeRange() {
            return startTime.toString() + " - " + endTime.toString();
        }
        public String getPresentCount() { return String.valueOf(presentCount); }
        public String getAbsentCount() { return String.valueOf(absentCount); }
        public String getTotalCount() { return String.valueOf(totalCount); }
        public LocalDate getSessionDate() { return sessionDate; }
        public java.time.LocalTime getStartTime() { return startTime; }
        public java.time.LocalTime getEndTime() { return endTime; }
    }

    public static class AttendanceRecord {
        private final String learnerName;
        private final String grade;
        private final String status;
        private final String sessionDate;

        public AttendanceRecord(String learnerName, String grade, String status, String sessionDate) {
            this.learnerName = learnerName;
            this.grade = grade;
            this.status = status;
            this.sessionDate = sessionDate;
        }

        public String getLearnerName() { return learnerName; }
        public String getGrade() { return grade; }
        public String getStatus() { return status; }
        public String getSessionDate() { return sessionDate; }
    }
}