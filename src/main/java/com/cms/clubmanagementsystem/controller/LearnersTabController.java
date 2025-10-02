package com.cms.clubmanagementsystem.controller;

import com.cms.clubmanagementsystem.utils.DatabaseConnector;
import com.cms.clubmanagementsystem.utils.SessionManager;
import com.itextpdf.kernel.colors.DeviceGray;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.UUID;

/**
 * Controller for managing and displaying learners in a club
 * Provides functionality for viewing, filtering, searching, and exporting learner data
 */
public class LearnersTabController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(LearnersTabController.class);

    // Constants
    private static final String ALL_FILTER = "All";
    private static final String ACTIVE_STATUS = "Active";
    private static final String INACTIVE_STATUS = "Inactive";
    private static final String CSV_FORMAT = "CSV";
    private static final String PDF_FORMAT = "PDF";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // UI Components
    @FXML private TableView<Learner> learnersTable;
    @FXML private TableColumn<Learner, String> fullNameColumn;
    @FXML private TableColumn<Learner, String> admissionNumberColumn;
    @FXML private TableColumn<Learner, String> gradeColumn;
    @FXML private TableColumn<Learner, String> enrollmentDateColumn;
    @FXML private TableColumn<Learner, String> statusColumn;

    @FXML private TextField searchField;
    @FXML private ComboBox<String> gradeFilter;
    @FXML private ComboBox<String> statusFilter;
    @FXML private Label totalLearnersLabel;
    @FXML private Label activeLearnersLabel;
    @FXML private VBox loadingIndicator;
    @FXML private VBox contentArea;

    // Data
    private final ObservableList<Learner> allLearners = FXCollections.observableArrayList();
    private final FilteredList<Learner> filteredLearners = new FilteredList<>(allLearners);
    private final SortedList<Learner> sortedLearners = new SortedList<>(filteredLearners);

    // State
    private UUID clubId;
    private String clubName;
    private int selectedYear;
    private int selectedTerm;
    private boolean isInitialized = false;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("Initializing LearnersTabController");
        initializeTableView();
        initializeFilters();
        initializeSearch();
        initializeSorting();
        isInitialized = true;
    }

    // Public API Methods
    public void setClubId(UUID clubId) {
        this.clubId = clubId;
        logger.debug("Club ID set to: {}", clubId);
        loadDataIfReady();
    }

    public void setClubName(String clubName) {
        this.clubName = clubName;
        logger.debug("Club name set to: {}", clubName);
    }

    public void setYearAndTerm(int year, int term) {
        this.selectedYear = year;
        this.selectedTerm = term;
        logger.debug("Year set to: {}, Term set to: {}", year, term);
        loadDataIfReady();
    }

    // Action Handlers
    @FXML
    private void handleRefresh() {
        logger.info("Refreshing learners data");
        loadLearnersData();
    }

    @FXML
    private void handleExport() {
        logger.info("Initiating data export");

        if (filteredLearners.isEmpty()) {
            showError("Export Error", "No data available to export");
            return;
        }

        Optional<String> formatResult = showExportFormatDialog();
        formatResult.ifPresent(this::initiateExport);
    }

    @FXML
    private void handleViewAttendance() {
        Learner selected = learnersTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            logger.info("Viewing attendance for learner: {}", selected.getFullName());
            showLearnerAttendance(selected);
        } else {
            showError("Selection Error", "Please select a learner to view attendance");
        }
    }

    // Initialization Methods
    private void initializeTableView() {
        fullNameColumn.setCellValueFactory(new PropertyValueFactory<>("fullName"));
        admissionNumberColumn.setCellValueFactory(new PropertyValueFactory<>("admissionNumber"));
        gradeColumn.setCellValueFactory(new PropertyValueFactory<>("grade"));
        enrollmentDateColumn.setCellValueFactory(new PropertyValueFactory<>("enrollmentDate"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));

        statusColumn.setCellFactory(column -> createStatusTableCell());

        learnersTable.setItems(sortedLearners);
        sortedLearners.comparatorProperty().bind(learnersTable.comparatorProperty());
    }

    private TableCell<Learner, String> createStatusTableCell() {
        return new TableCell<Learner, String>() {
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
                if (ACTIVE_STATUS.equals(status)) {
                    setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                } else {
                    setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                }
            }
        };
    }

    private void initializeFilters() {
        initializeStatusFilter();
        initializeGradeFilter();
    }

    private void initializeStatusFilter() {
        statusFilter.setItems(FXCollections.observableArrayList(
                ALL_FILTER, ACTIVE_STATUS, INACTIVE_STATUS
        ));
        statusFilter.setValue(ALL_FILTER);
        statusFilter.valueProperty().addListener((obs, oldVal, newVal) -> applyFilters());
    }

    private void initializeGradeFilter() {
        gradeFilter.setItems(FXCollections.observableArrayList(ALL_FILTER));
        gradeFilter.setValue(ALL_FILTER);
        gradeFilter.valueProperty().addListener((obs, oldVal, newVal) -> applyFilters());
    }

    private void initializeSearch() {
        searchField.textProperty().addListener((observable, oldValue, newValue) -> applyFilters());
    }

    private void initializeSorting() {
        // Default sort by full name
        fullNameColumn.setSortType(TableColumn.SortType.ASCENDING);
        learnersTable.getSortOrder().add(fullNameColumn);
    }

    // Data Loading Methods
    private void loadDataIfReady() {
        if (isInitialized && clubId != null) {
            loadLearnersData();
        }
    }

    private void loadLearnersData() {
        logger.info("Loading learners data for club: {}, year: {}, term: {}", clubId, selectedYear, selectedTerm);

        if (clubId == null) {
            logger.error("Club ID is null, cannot load learners data");
            return;
        }

        // Capture session context before spawning thread
        final UUID currentSchoolId = SessionManager.getCurrentSchoolId();
        final UUID currentUserId = SessionManager.getCurrentUserId();

        logger.info("Current school ID: {}, Current user ID: {}", currentSchoolId, currentUserId);

        if (currentSchoolId == null || currentUserId == null) {
            logger.error("Session context is null - schoolId: {}, userId: {}", currentSchoolId, currentUserId);
            Platform.runLater(() -> {
                showError("Session Error", "User session is not available. Please log in again.");
            });
            return;
        }

        showLoading(true);

        // Run database operations in a separate thread to avoid blocking UI
        new Thread(() -> {
            try (Connection conn = DatabaseConnector.getConnection();
                 PreparedStatement ps = createLearnersQuery(conn, currentSchoolId, currentUserId)) {

                ResultSet rs = ps.executeQuery();
                processLearnersResultSet(rs);

            } catch (Exception e) {
                Platform.runLater(() -> handleDataLoadError(e));
            } finally {
                Platform.runLater(() -> showLoading(false));
            }
        }).start();
    }

    private PreparedStatement createLearnersQuery(Connection conn, UUID currentSchoolId, UUID currentUserId) throws Exception {
        // First, set the tenant context for this connection
        setTenantContext(conn, currentSchoolId, currentUserId);

        String sql = """
        SELECT 
            l.learner_id,
            l.full_name,
            l.admission_number,
            g.grade_name,
            ce.enrollment_date,
            ce.is_active,
            COUNT(ar.record_id) FILTER (WHERE ar.status = 'present') as present_count,
            COUNT(ar.record_id) as total_attendance
        FROM club_enrollments ce
        JOIN learners l ON ce.learner_id = l.learner_id
        JOIN grades g ON l.grade_id = g.grade_id
        LEFT JOIN attendance_records ar ON l.learner_id = ar.learner_id
        LEFT JOIN attendance_sessions ass ON ar.session_id = ass.session_id
        WHERE ce.club_id = ?
        AND ce.academic_year = ?
        AND ce.term_number = ?
        AND ce.school_id = ?
        GROUP BY l.learner_id, l.full_name, l.admission_number, g.grade_name, 
                 ce.enrollment_date, ce.is_active
        ORDER BY l.full_name
    """;

        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setObject(1, clubId);
        ps.setInt(2, selectedYear);
        ps.setInt(3, selectedTerm);
        ps.setObject(4, currentSchoolId);

        return ps;
    }

    private void setTenantContext(Connection conn, UUID currentSchoolId, UUID currentUserId) {
        try {
            // Set the current school and user context for row-level security
            try (PreparedStatement ps = conn.prepareStatement("SELECT set_config('app.current_school_id', ?, false)")) {
                ps.setString(1, currentSchoolId.toString());
                ps.execute();
            }

            try (PreparedStatement ps = conn.prepareStatement("SELECT set_config('app.current_user_id', ?, false)")) {
                ps.setString(1, currentUserId.toString());
                ps.execute();
            }

            logger.debug("Tenant context set for school: {}, user: {}", currentSchoolId, currentUserId);
        } catch (Exception e) {
            logger.error("Failed to set tenant context: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to set tenant context", e);
        }
    }

    private void processLearnersResultSet(ResultSet rs) throws Exception {
        ObservableList<Learner> tempLearners = FXCollections.observableArrayList();
        int activeCount = 0;
        int totalCount = 0;
        ObservableList<String> grades = FXCollections.observableArrayList(ALL_FILTER);

        while (rs.next()) {
            Learner learner = createLearnerFromResultSet(rs);
            tempLearners.add(learner);

            if (learner.isActive()) activeCount++;
            totalCount++;

            updateGradeFilter(grades, learner.getGrade());
        }

        // Update UI on JavaFX Application Thread
        int finalTotalCount = totalCount;
        int finalActiveCount = activeCount;
        Platform.runLater(() -> {
            allLearners.setAll(tempLearners);
            updateGradeFilterComboBox(grades);
            updateStatistics(finalTotalCount, finalActiveCount);
            applyFilters(); // Apply initial filters
            logger.info("Loaded {} learners ({} active) for club {}", finalTotalCount, finalActiveCount, clubId);
        });
    }

    private Learner createLearnerFromResultSet(ResultSet rs) throws Exception {
        String fullName = rs.getString("full_name");
        String admissionNumber = rs.getString("admission_number");
        String grade = rs.getString("grade_name");
        String enrollmentDate = formatEnrollmentDate(rs.getTimestamp("enrollment_date"));
        boolean isActive = rs.getBoolean("is_active");
        int presentCount = rs.getInt("present_count");
        int totalAttendance = rs.getInt("total_attendance");

        return new Learner(fullName, admissionNumber, grade, enrollmentDate,
                isActive, presentCount, totalAttendance);
    }

    private String formatEnrollmentDate(java.sql.Timestamp timestamp) {
        if (timestamp == null) return "N/A";
        return timestamp.toLocalDateTime().toLocalDate().format(DATE_FORMATTER);
    }

    private void updateGradeFilter(ObservableList<String> grades, String grade) {
        if (grade != null && !grades.contains(grade)) {
            grades.add(grade);
        }
    }

    private void updateGradeFilterComboBox(ObservableList<String> grades) {
        Platform.runLater(() -> {
            gradeFilter.setItems(grades);
            if (!grades.contains(gradeFilter.getValue())) {
                gradeFilter.setValue(ALL_FILTER);
            }
        });
    }

    private void handleDataLoadError(Exception e) {
        logger.error("Error loading learners data: {}", e.getMessage(), e);
        showError("Data Load Error", "Failed to load learners data: " + e.getMessage());
    }

    // Filtering Methods
    private void applyFilters() {
        String searchText = searchField.getText().toLowerCase();
        String selectedGrade = gradeFilter.getValue();
        String selectedStatus = statusFilter.getValue();

        filteredLearners.setPredicate(learner ->
                matchesSearch(learner, searchText) &&
                        matchesGrade(learner, selectedGrade) &&
                        matchesStatus(learner, selectedStatus)
        );

        logger.debug("Applied filters: {} learners match criteria", filteredLearners.size());
    }

    private boolean matchesSearch(Learner learner, String searchText) {
        if (searchText == null || searchText.isEmpty()) return true;

        String searchLower = searchText.toLowerCase();
        return (learner.getFullName() != null && learner.getFullName().toLowerCase().contains(searchLower)) ||
                (learner.getAdmissionNumber() != null && learner.getAdmissionNumber().toLowerCase().contains(searchLower)) ||
                (learner.getGrade() != null && learner.getGrade().toLowerCase().contains(searchLower));
    }

    private boolean matchesGrade(Learner learner, String selectedGrade) {
        return ALL_FILTER.equals(selectedGrade) ||
                (learner.getGrade() != null && learner.getGrade().equals(selectedGrade));
    }

    private boolean matchesStatus(Learner learner, String selectedStatus) {
        return ALL_FILTER.equals(selectedStatus) ||
                (learner.getStatus() != null && learner.getStatus().equals(selectedStatus));
    }

    // Export Methods
    private Optional<String> showExportFormatDialog() {
        ChoiceDialog<String> dialog = new ChoiceDialog<>(CSV_FORMAT, CSV_FORMAT, PDF_FORMAT);
        dialog.setTitle("Export Format");
        dialog.setHeaderText("Select Export Format");
        dialog.setContentText("Choose export format:");
        return dialog.showAndWait();
    }

    private void initiateExport(String format) {
        File file = showFileSaveDialog(format);
        if (file != null) {
            performExport(format, file);
        }
    }

    private File showFileSaveDialog(String format) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Learners Data");

        FileChooser.ExtensionFilter extFilter = createExtensionFilter(format);
        fileChooser.getExtensionFilters().add(extFilter);

        String fileName = generateFileName(format);
        fileChooser.setInitialFileName(fileName);

        return fileChooser.showSaveDialog(learnersTable.getScene().getWindow());
    }

    private FileChooser.ExtensionFilter createExtensionFilter(String format) {
        if (PDF_FORMAT.equals(format)) {
            return new FileChooser.ExtensionFilter("PDF files (*.pdf)", "*.pdf");
        } else {
            return new FileChooser.ExtensionFilter("CSV files (*.csv)", "*.csv");
        }
    }

    private String generateFileName(String format) {
        String extension = PDF_FORMAT.equals(format) ? ".pdf" : ".csv";
        String safeClubName = clubName != null ? clubName.replaceAll("\\s+", "_") : "club";
        return String.format("learners_%s_%d_term%d%s",
                safeClubName, selectedYear, selectedTerm, extension);
    }

    private void performExport(String format, File file) {
        try {
            if (PDF_FORMAT.equals(format)) {
                exportToPDF(file);
            } else {
                exportToCSV(file);
            }
            showInfo("Export Successful", "Data exported successfully to: " + file.getAbsolutePath());
        } catch (Exception e) {
            logger.error("Error exporting data: {}", e.getMessage(), e);
            showError("Export Error", "Failed to export data: " + e.getMessage());
        }
    }

    private void exportToCSV(File file) throws IOException {
        logger.info("Exporting {} learners to CSV: {}", filteredLearners.size(), file.getAbsolutePath());

        try (FileWriter writer = new FileWriter(file)) {
            writeCsvHeader(writer);
            writeCsvData(writer);
            writeCsvSummary(writer);
        }

        logger.info("CSV export completed successfully");
    }

    private void writeCsvHeader(FileWriter writer) throws IOException {
        writer.write("Full Name,Admission Number,Grade,Enrollment Date,Status,Attendance %,Present Count,Total Sessions\n");
    }

    private void writeCsvData(FileWriter writer) throws IOException {
        for (Learner learner : filteredLearners) {
            String line = String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%.1f%%,%d,%d\n",
                    escapeCsv(learner.getFullName()),
                    escapeCsv(learner.getAdmissionNumber()),
                    escapeCsv(learner.getGrade()),
                    escapeCsv(learner.getEnrollmentDate()),
                    escapeCsv(learner.getStatus()),
                    learner.getAttendancePercentage(),
                    learner.getPresentCount(),
                    learner.getTotalAttendance()
            );
            writer.write(line);
        }
    }

    private void writeCsvSummary(FileWriter writer) throws IOException {
        writer.write("\nSummary\n");
        writer.write("Total Learners:," + filteredLearners.size() + "\n");
        writer.write("Export Date:," + LocalDateTime.now() + "\n");
        writer.write("Club:," + escapeCsv(clubName) + "\n");
        writer.write("Academic Year:," + selectedYear + "\n");
        writer.write("Term:," + selectedTerm + "\n");
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        return value.replace("\"", "\"\"");
    }

    private void exportToPDF(File file) throws IOException {
        logger.info("Exporting {} learners to PDF: {}", filteredLearners.size(), file.getAbsolutePath());

        try (PdfWriter writer = new PdfWriter(file.getAbsolutePath());
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document document = new Document(pdfDoc)) {

            addPdfHeader(document);
            addPdfTable(document);
            addPdfSummary(document);
        }

        logger.info("PDF export completed successfully");
    }

    private void addPdfHeader(Document document) {
        Paragraph title = new Paragraph("Learners Report - " + clubName)
                .setFontSize(18)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER);
        document.add(title);

        Paragraph metadata = new Paragraph()
                .add("Academic Year: " + selectedYear + " | ")
                .add("Term: " + selectedTerm + " | ")
                .add("Export Date: " + LocalDate.now().format(DATE_FORMATTER))
                .setFontSize(10)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(DeviceGray.GRAY);
        document.add(metadata);

        document.add(new Paragraph("\n"));
    }

    private void addPdfTable(Document document) {
        // Create table with 6 columns
        Table table = new Table(6);
        table.setWidth(UnitValue.createPercentValue(100));

        addPdfTableHeaders(table);
        addPdfTableRows(table);

        document.add(table);
    }

    private void addPdfTableHeaders(Table table) {
        String[] headers = {"Full Name", "Admission No.", "Grade", "Enrollment Date", "Status", "Attendance %"};
        for (String header : headers) {
            Cell cell = new Cell()
                    .add(new Paragraph(header))
                    .setBold()
                    .setBackgroundColor(new DeviceRgb(52, 152, 219))
                    .setFontColor(DeviceGray.WHITE)
                    .setTextAlignment(TextAlignment.CENTER);
            table.addHeaderCell(cell);
        }
    }

    private void addPdfTableRows(Table table) {
        for (Learner learner : filteredLearners) {
            table.addCell(createPdfCell(learner.getFullName()));
            table.addCell(createPdfCell(learner.getAdmissionNumber()));
            table.addCell(createPdfCell(learner.getGrade()));
            table.addCell(createPdfCell(learner.getEnrollmentDate()));
            table.addCell(createStatusPdfCell(learner.getStatus()));
            table.addCell(createPdfCell(learner.getFormattedAttendancePercentage()));
        }
    }

    private Cell createPdfCell(String text) {
        return new Cell()
                .add(new Paragraph(text != null ? text : ""))
                .setPadding(5)
                .setFontSize(10);
    }

    private Cell createStatusPdfCell(String status) {
        Cell cell = createPdfCell(status);
        if (ACTIVE_STATUS.equals(status)) {
            cell.setBackgroundColor(new DeviceRgb(39, 174, 96));
        } else {
            cell.setBackgroundColor(new DeviceRgb(231, 76, 60));
        }
        return cell;
    }

    private void addPdfSummary(Document document) {
        document.add(new Paragraph("\n"));
        Paragraph summary = new Paragraph("Summary: " + filteredLearners.size() + " learners found")
                .setBold()
                .setFontSize(12);
        document.add(summary);
    }

    // UI Utility Methods
    private void updateStatistics(int totalCount, int activeCount) {
        Platform.runLater(() -> {
            totalLearnersLabel.setText(String.valueOf(totalCount));
            activeLearnersLabel.setText(String.valueOf(activeCount));
        });
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

    private void showLearnerAttendance(Learner learner) {
        logger.info("Opening attendance view for learner: {}", learner.getFullName());

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/learner-attendance-detail.fxml"));
            Parent root = loader.load();

            LearnerAttendanceDetailController controller = loader.getController();
            controller.setLearnerData(learner, clubId, clubName, selectedYear, selectedTerm);

            Stage stage = new Stage();
            stage.setTitle(String.format("Attendance - %s (%s)", learner.getFullName(), learner.getAdmissionNumber()));
            stage.setScene(new Scene(root, 1000, 700));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(learnersTable.getScene().getWindow());

            // Set window icon if available
            try {
                stage.getIcons().add(new Image(getClass().getResourceAsStream("/images/attendance_icon.png")));
            } catch (Exception e) {
                logger.debug("Could not load window icon: {}", e.getMessage());
            }

            stage.showAndWait();

        } catch (IOException e) {
            logger.error("Error loading learner attendance view: {}", e.getMessage(), e);
            showError("Navigation Error", "Failed to open attendance view: " + e.getMessage());
        }
    }

    /**
     * Data model representing a learner in the club
     */
    public static class Learner {
        private final SimpleStringProperty fullName;
        private final SimpleStringProperty admissionNumber;
        private final SimpleStringProperty grade;
        private final SimpleStringProperty enrollmentDate;
        private final SimpleStringProperty status;
        private final int presentCount;
        private final int totalAttendance;
        private final boolean isActive;

        public Learner(String fullName, String admissionNumber, String grade,
                       String enrollmentDate, boolean isActive, int presentCount, int totalAttendance) {
            this.fullName = new SimpleStringProperty(fullName != null ? fullName : "");
            this.admissionNumber = new SimpleStringProperty(admissionNumber != null ? admissionNumber : "");
            this.grade = new SimpleStringProperty(grade != null ? grade : "");
            this.enrollmentDate = new SimpleStringProperty(enrollmentDate != null ? enrollmentDate : "");
            this.isActive = isActive;
            this.status = new SimpleStringProperty(isActive ? ACTIVE_STATUS : INACTIVE_STATUS);
            this.presentCount = presentCount;
            this.totalAttendance = totalAttendance;
        }

        // Getters
        public String getFullName() { return fullName.get(); }
        public String getAdmissionNumber() { return admissionNumber.get(); }
        public String getGrade() { return grade.get(); }
        public String getEnrollmentDate() { return enrollmentDate.get(); }
        public String getStatus() { return status.get(); }
        public boolean isActive() { return isActive; }
        public int getPresentCount() { return presentCount; }
        public int getTotalAttendance() { return totalAttendance; }

        public double getAttendancePercentage() {
            return totalAttendance > 0 ? (double) presentCount / totalAttendance * 100 : 0;
        }

        public String getFormattedAttendancePercentage() {
            return String.format("%.1f%%", getAttendancePercentage());
        }
    }
}