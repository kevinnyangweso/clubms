package com.cms.clubmanagementsystem.controller;

import com.cms.clubmanagementsystem.service.ClubService;
import com.cms.clubmanagementsystem.service.EnrollmentService;
import com.cms.clubmanagementsystem.service.LearnerService;
import com.cms.clubmanagementsystem.utils.DatabaseConnector;
import com.cms.clubmanagementsystem.utils.SessionManager;
import com.cms.clubmanagementsystem.utils.TenantContext;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.scene.control.Tooltip;

import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Year;
import java.util.List;
import java.util.ResourceBundle;
import java.util.UUID;
import javafx.scene.control.Alert;

public class EnrollmentController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(EnrollmentController.class);

    // FXML injections
    @FXML private ComboBox<Integer> termComboBox;
    @FXML private ComboBox<Integer> yearComboBox;
    @FXML private Label clubNameLabel;
    @FXML private ComboBox<String> learnerComboBox;
    @FXML private TableView<EnrollmentService.Enrollment> enrollmentsTable;
    @FXML private TableColumn<EnrollmentService.Enrollment, String> admissionNumberColumn;
    @FXML private TableColumn<EnrollmentService.Enrollment, String> learnerNameColumn;
    @FXML private TableColumn<EnrollmentService.Enrollment, String> clubNameColumn;
    @FXML private Label selectedLearnerName;
    @FXML private Label selectedLearnerGrade;
    @FXML private Label selectedLearnerAdmission;
    @FXML private Label enrolledCountLabel;

    private final ObservableList<EnrollmentService.Enrollment> enrollments = FXCollections.observableArrayList();
    private final ObservableList<String> learners = FXCollections.observableArrayList();

    private UUID currentSchoolId;
    private UUID assignedClubId;
    private String assignedClubName;
    private EnrollmentService enrollmentService = new EnrollmentService();
    private ObservableList<LearnerInfo> learnerInfoList = FXCollections.observableArrayList();
    private ObservableList<ClubInfo> clubInfoList = FXCollections.observableArrayList(); // ADDED: ClubInfo list
    private boolean isTeacher = false;

    // ADDED: ClubInfo class definition
    public static class ClubInfo {
        private UUID clubId;
        private String clubName;

        public ClubInfo(UUID clubId, String clubName) {
            this.clubId = clubId;
            this.clubName = clubName;
        }

        public UUID getClubId() { return clubId; }
        public String getClubName() { return clubName; }

        @Override
        public String toString() {
            return clubName;
        }
    }

    // Helper classes
    public static class LearnerInfo {
        private UUID learnerId;
        private String admissionNumber;
        private String fullName;
        private String gradeName;

        public LearnerInfo(UUID learnerId, String admissionNumber, String fullName, String gradeName) {
            this.learnerId = learnerId;
            this.admissionNumber = admissionNumber;
            this.fullName = fullName;
            this.gradeName = gradeName;
        }

        public UUID getLearnerId() { return learnerId; }
        public String getAdmissionNumber() { return admissionNumber; }
        public String getFullName() { return fullName; }
        public String getGradeName() { return gradeName; }

        @Override
        public String toString() {
            return fullName + " (" + admissionNumber + ") - " + gradeName;
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        currentSchoolId = SessionManager.getCurrentSchoolId();
        checkUserPermissions();

        if (!isTeacher) {
            setupUI();
            disableEntireUI();
            showAlert(Alert.AlertType.ERROR, "Access Denied",
                    "You do not have teacher permissions to access enrollment management.");
            return;
        }

        loadTeacherAssignment();
        setupUI();
        loadInitialData();
        loadCurrentTermEnrollments();
    }

    private void loadTeacherAssignment() {
        UUID userId = SessionManager.getCurrentUserId();
        logger.info("Loading teacher assignment for user: {}", userId);

        try (Connection conn = DatabaseConnector.getConnection()) {
            String sql = """
                SELECT c.club_id, c.club_name 
                FROM clubs c
                JOIN club_teachers ct ON c.club_id = ct.club_id
                WHERE ct.teacher_id = ? 
                AND c.is_active = true
                AND c.school_id = ?
            """;

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, userId);
                ps.setObject(2, currentSchoolId);
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    assignedClubId = (UUID) rs.getObject("club_id");
                    assignedClubName = rs.getString("club_name");
                    logger.info("Teacher assigned to club: {} (ID: {})", assignedClubName, assignedClubId);

                    if (clubNameLabel != null) {
                        clubNameLabel.setText("Enrolling for: " + assignedClubName);
                    }
                } else {
                    logger.warn("No club assigned to teacher");
                    showAlert(Alert.AlertType.WARNING, "No Club Assignment",
                            "You are not assigned to any club. Please contact administrator.");
                    disableEntireUI();
                }
            }
        } catch (Exception e) {
            logger.error("Error loading teacher assignment: {}", e.getMessage());
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to load your club assignment.");
            disableEntireUI();
        }
    }

    private void checkUserPermissions() {
        isTeacher = SessionManager.isTeacher();
        logger.info("User is teacher: {}", isTeacher);
    }

    private void disableEntireUI() {
        learnerComboBox.setDisable(true);
        termComboBox.setDisable(true);
        yearComboBox.setDisable(true);
        enrollmentsTable.setDisable(true);

        Tooltip disabledTooltip = new Tooltip("Access denied or no club assignment");
        learnerComboBox.setTooltip(disabledTooltip);
        termComboBox.setTooltip(disabledTooltip);
        yearComboBox.setTooltip(disabledTooltip);
        enrollmentsTable.setTooltip(disabledTooltip);
    }

    private void loadInitialData() {
        loadLearners();
    }

    private void setupUI() {
        // Setup term combobox with prompt text
        termComboBox.setItems(FXCollections.observableArrayList(1, 2, 3));
        termComboBox.setPromptText("Select term");

        // Setup year combobox as editable for manual entry
        yearComboBox.setEditable(true);
        yearComboBox.setPromptText("Enter academic year");

        // Set current year as default value but don't auto-select
        int currentYear = Year.now().getValue();
        yearComboBox.getEditor().setText(String.valueOf(currentYear));

        // Add text formatter to validate year input
        setupYearInputValidation();
    }

    private void setupYearInputValidation() {
        // Add a text formatter to only allow numeric input
        yearComboBox.getEditor().textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && !newValue.matches("\\d*")) {
                yearComboBox.getEditor().setText(newValue.replaceAll("[^\\d]", ""));
            }
        });

        // Add focus listener to validate year when user finishes typing
        yearComboBox.getEditor().focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) { // When focus is lost
                validateYearInput();
            }
        });
    }

    private Integer getSelectedYear() {
        String yearText = yearComboBox.getEditor().getText();
        if (yearText != null && !yearText.trim().isEmpty()) {
            try {
                return Integer.parseInt(yearText.trim());
            } catch (NumberFormatException e) {
                // Show error and reset to current year
                showAlert(Alert.AlertType.ERROR, "Invalid Year",
                        "Please enter a valid year number");
                int currentYear = Year.now().getValue();
                yearComboBox.getEditor().setText(String.valueOf(currentYear));
                return currentYear;
            }
        }

        // If empty, use current year
        int currentYear = Year.now().getValue();
        yearComboBox.getEditor().setText(String.valueOf(currentYear));
        return currentYear;
    }

    private void validateYearInput() {
        String yearText = yearComboBox.getEditor().getText();
        if (yearText != null && !yearText.trim().isEmpty()) {
            try {
                int year = Integer.parseInt(yearText.trim());
                // Validate reasonable year range (e.g., 2000-2100)
                if (year < 2000 || year > 2100) {
                    showAlert(Alert.AlertType.WARNING, "Invalid Year",
                            "Please enter a year between 2000 and 2100");
                    int currentYear = Year.now().getValue();
                    yearComboBox.getEditor().setText(String.valueOf(currentYear));
                }
            } catch (NumberFormatException e) {
                showAlert(Alert.AlertType.ERROR, "Invalid Input",
                        "Please enter a valid year number");
                int currentYear = Year.now().getValue();
                yearComboBox.getEditor().setText(String.valueOf(currentYear));
            }
        } else {
            // If empty, set to current year
            int currentYear = Year.now().getValue();
            yearComboBox.getEditor().setText(String.valueOf(currentYear));
        }
    }

    @FXML
    private void loadEnrollments() {
        Integer term = termComboBox.getValue();
        Integer year = getSelectedYear(); // Use the safe method

        if (term != null && year != null) {
            loadEnrollmentsForTerm(term, year);
        } else {
            showAlert(Alert.AlertType.WARNING, "Selection Required",
                    "Please select both term and year");
        }
    }

    // Remove the auto-loading from loadCurrentTermEnrollments()
    private void loadCurrentTermEnrollments() {
        // Don't auto-set anything - let user choose
    }

    @FXML
    private void handleEnrollLearner() {
        // Validate that both term and year are selected
        if (!isTermAndYearSelected()) {
            showAlert(Alert.AlertType.ERROR, "Selection Required",
                    "Please select both term and academic year before enrolling a learner.");
            return;
        }

        if (assignedClubId == null) {
            showAlert(Alert.AlertType.ERROR, "Error", "No club assigned. Cannot enroll learners.");
            return;
        }

        String selectedLearner = learnerComboBox.getValue();
        Integer term = termComboBox.getValue();
        Integer year = getSelectedYear(); // Use the new method

        if (selectedLearner == null) {
            showAlert(Alert.AlertType.ERROR, "Validation Error", "Please select a learner");
            return;
        }

        LearnerInfo learner = learnerInfoList.stream()
                .filter(l -> l.toString().equals(selectedLearner))
                .findFirst()
                .orElse(null);

        if (learner == null) {
            showAlert(Alert.AlertType.ERROR, "Selection Error", "Invalid learner selection");
            return;
        }

        try (Connection conn = DatabaseConnector.getConnection()) {
            UUID teacherId = SessionManager.getCurrentUserId();
            if (teacherId != null) {
                TenantContext.setTenant(conn, currentSchoolId.toString(), teacherId.toString());
            }

            boolean success = enrollmentService.enrollLearner(conn, learner.getLearnerId(),
                    assignedClubId, term, year, teacherId);

            if (success) {
                showAlert(Alert.AlertType.INFORMATION, "Success",
                        "Learner enrolled successfully in " + assignedClubName + "!");

                // Refresh enrollments but KEEP the term and year selection
                loadEnrollments();

                // Only clear the learner selection, keep term/year
                handleClearLearnerSelection();
            } else {
                showAlert(Alert.AlertType.ERROR, "Enrollment Error", "Failed to enroll learner");
            }

        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Database Error", "Error: " + e.getMessage());
            logger.error("Error enrolling learner", e);
        }
    }

    private boolean isTermAndYearSelected() {
        Integer term = termComboBox.getValue();
        Integer year = getSelectedYear();
        return term != null && year != null;
    }

    @FXML
    private void handleClearSelections() {
        // Only clear learner selection, keep term and year
        handleClearLearnerSelection();
    }

    private void handleClearLearnerSelection() {
        learnerComboBox.setValue(null);
        learnerComboBox.getEditor().clear();
        selectedLearnerName.setText("Not selected");
        selectedLearnerGrade.setText("-");
        selectedLearnerAdmission.setText("-");
    }

    private void setupContextMenu() {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem withdrawItem = new MenuItem("Withdraw Enrollment");
        MenuItem viewHistoryItem = new MenuItem("View Learner History");

        withdrawItem.setOnAction(event -> {
            EnrollmentService.Enrollment selected = enrollmentsTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                handleWithdrawEnrollment(selected);
            }
        });

        viewHistoryItem.setOnAction(event -> {
            EnrollmentService.Enrollment selected = enrollmentsTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                handleViewLearnerHistory(selected.getLearnerId());
            }
        });

        contextMenu.getItems().addAll(withdrawItem, viewHistoryItem);
        enrollmentsTable.setContextMenu(contextMenu);

        // Enable double-click (view only)
        enrollmentsTable.setRowFactory(tv -> {
            TableRow<EnrollmentService.Enrollment> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    EnrollmentService.Enrollment rowData = row.getItem();
                    handleViewLearnerDetails(rowData);
                }
            });
            return row;
        });
    }

    private void loadEnrollmentsForTerm(int termNumber, int academicYear) {
        if (assignedClubId == null) {
            logger.warn("Cannot load enrollments - no club assigned");
            return;
        }

        try (Connection conn = DatabaseConnector.getConnection()) {
            UUID currentUserId = SessionManager.getCurrentUserId();
            if (currentUserId != null) {
                TenantContext.setTenant(conn, currentSchoolId.toString(), currentUserId.toString());
            }

            // Clear current enrollments
            enrollments.clear();

            // Load enrollments for the teacher's assigned club
            List<EnrollmentService.Enrollment> clubEnrollments =
                    enrollmentService.getEnrollmentsByClub(conn, assignedClubId, termNumber, academicYear);
            enrollments.addAll(clubEnrollments);

            // Update enrolled count label
            if (enrolledCountLabel != null) {
                enrolledCountLabel.setText("Enrolled: " + clubEnrollments.size() + " learners");
            }

            logger.info("Loaded {} enrollments for club {}", clubEnrollments.size(), assignedClubName);

        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Load Error", "Error loading enrollments: " + e.getMessage());
            logger.error("Error loading enrollments for term {} year {}", termNumber, academicYear, e);
        }
    }

    @FXML
    private void handleSearchLearners() {
        String searchTerm = learnerComboBox.getEditor().getText();
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            loadLearners();
            return;
        }

        try (Connection conn = DatabaseConnector.getConnection()) {
            UUID currentUserId = SessionManager.getCurrentUserId();
            if (currentUserId != null) {
                TenantContext.setTenant(conn, currentSchoolId.toString(), currentUserId.toString());
            }

            // FIX: Pass currentSchoolId to constructor
            LearnerService learnerService = new LearnerService(currentSchoolId);
            List<LearnerInfo> searchResults = learnerService.searchLearners(conn, currentSchoolId, searchTerm.trim());

            learnerInfoList.clear();
            learners.clear();
            learnerInfoList.addAll(searchResults);
            learnerInfoList.forEach(learner -> learners.add(learner.toString()));

        } catch (SQLException e) {
            logger.error("Error searching learners", e);
            showAlert(Alert.AlertType.ERROR, "Search Error", "Error searching learners: " + e.getMessage());
        }
    }

    private void loadLearners() {
        try (Connection conn = DatabaseConnector.getConnection()) {
            UUID currentUserId = SessionManager.getCurrentUserId();
            if (currentUserId != null) {
                TenantContext.setTenant(conn, currentSchoolId.toString(), currentUserId.toString());
            }

            // FIX: Pass currentSchoolId to constructor
            LearnerService learnerService = new LearnerService(currentSchoolId);
            learnerInfoList.clear();
            learners.clear();

            // Get learners from database
            List<LearnerInfo> activeLearners = learnerService.getActiveLearners(conn, currentSchoolId);
            learnerInfoList.addAll(activeLearners);

            // Update the combobox
            learnerInfoList.forEach(learner -> learners.add(learner.toString()));

            logger.info("Loaded {} learners from database", learnerInfoList.size());

        } catch (SQLException e) {
            logger.error("Error loading learners from database", e);
            showAlert(Alert.AlertType.ERROR, "Database Error", "Error loading learners: " + e.getMessage());
            loadMockLearners();
        }
    }

    private void loadMockLearners() {
        learnerInfoList.clear();
        learners.clear();

        learnerInfoList.add(new LearnerInfo(UUID.randomUUID(), "ADM2024001", "John Smith", "Grade 5"));
        learnerInfoList.add(new LearnerInfo(UUID.randomUUID(), "ADM2024002", "Sarah Johnson", "Grade 6"));
        learnerInfoList.add(new LearnerInfo(UUID.randomUUID(), "ADM2024003", "Mike Brown", "Grade 4"));
        learnerInfoList.add(new LearnerInfo(UUID.randomUUID(), "ADM2024004", "Emily Davis", "Grade 5"));
        learnerInfoList.add(new LearnerInfo(UUID.randomUUID(), "ADM2024005", "David Wilson", "Grade 6"));

        learnerInfoList.forEach(learner -> learners.add(learner.toString()));
        logger.warn("Using mock learners data due to database error");
    }

    private void updateLearnerInfo() {
        String selected = learnerComboBox.getValue();
        if (selected != null) {
            LearnerInfo learner = learnerInfoList.stream()
                    .filter(l -> l.toString().equals(selected))
                    .findFirst()
                    .orElse(null);

            if (learner != null) {
                selectedLearnerName.setText(learner.getFullName());
                selectedLearnerGrade.setText(learner.getGradeName());
                selectedLearnerAdmission.setText(learner.getAdmissionNumber());
            }
        }
    }

    @FXML
    private void handleRefresh() {
        loadLearners();
        loadEnrollments();
    }

    @FXML
    private void handleWithdrawEnrollment(EnrollmentService.Enrollment enrollment) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirm Withdrawal");
        confirmation.setHeaderText("Withdraw Learner");
        confirmation.setContentText("Are you sure you want to withdraw " + enrollment.getLearnerName() +
                " from " + enrollment.getClubName() + "?");

        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try (Connection conn = DatabaseConnector.getConnection()) {
                    UUID teacherId = SessionManager.getCurrentUserId();
                    if (teacherId != null) {
                        TenantContext.setTenant(conn, currentSchoolId.toString(), teacherId.toString());
                    }

                    boolean success = enrollmentService.withdrawLearner(conn, enrollment.getEnrollmentId());

                    if (success) {
                        showAlert(Alert.AlertType.INFORMATION, "Success", "Learner withdrawn successfully!");
                        loadEnrollments();
                    } else {
                        showAlert(Alert.AlertType.ERROR, "Withdrawal Error", "Failed to withdraw learner");
                    }

                } catch (SQLException e) {
                    showAlert(Alert.AlertType.ERROR, "Database Error", "Error: " + e.getMessage());
                }
            }
        });
    }

    @FXML
    private void handleViewLearnerHistory(UUID learnerId) {
        showAlert(Alert.AlertType.INFORMATION, "Learner History", "View history for learner: " + learnerId);
    }

    @FXML
    private void handleViewLearnerDetails(EnrollmentService.Enrollment enrollment) {
        showAlert(Alert.AlertType.INFORMATION, "Learner Details",
                "Name: " + enrollment.getLearnerName() + "\n" +
                        "Admission: " + enrollment.getAdmissionNumber() + "\n" +
                        "Club: " + enrollment.getClubName());
    }

    @FXML
    private void handleViewHistory() {
        showAlert(Alert.AlertType.INFORMATION, "Enrollment History", "View enrollment history");
    }

    @FXML
    private void handleGenerateReport() {
        showAlert(Alert.AlertType.INFORMATION, "Generate Report", "Generate report");
    }

    @FXML
    private void handleExportExcel() {
        showAlert(Alert.AlertType.INFORMATION, "Export Data", "Export to Excel");
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}