package com.cms.clubmanagementsystem.controller;

import com.cms.clubmanagementsystem.service.EnrollmentService;
import com.cms.clubmanagementsystem.service.LearnerService;
import com.cms.clubmanagementsystem.utils.*;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.apache.poi.ss.usermodel.Cell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.sql.*;
import java.time.Year;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.UUID;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.Bindings;

public class EnrollmentController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(EnrollmentController.class);

    // FXML injections
    @FXML private ComboBox<Integer> termComboBox;
    @FXML private ComboBox<String> yearComboBox;
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
    @FXML private TableColumn<EnrollmentService.Enrollment, String> statusColumn;

    private final ObservableList<EnrollmentService.Enrollment> enrollments = FXCollections.observableArrayList();
    private final ObservableList<String> learners = FXCollections.observableArrayList();
    private final ObservableList<LearnerInfo> learnerInfoList = FXCollections.observableArrayList();

    private UUID currentSchoolId;
    private UUID assignedClubId;
    private String assignedClubName;
    private final EnrollmentService enrollmentService = new EnrollmentService();
    private boolean isTeacher = false;

    // Helper class
    public static class LearnerInfo {
        private final UUID learnerId;
        private final String admissionNumber;
        private final String fullName;
        private final String gradeName;

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
        try {
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
            setupLearnerComboBox();
            setupTableSelectionListener();
            setupAutoLoadListeners();
            setupEventListeners();
            setupContextMenu();
            setupCleanupListener();
            loadLearners();

            // Auto-load with default values after UI is ready
            Platform.runLater(this::autoLoadEnrollments);

        } catch (Exception e) {
            logger.error("Error initializing EnrollmentController: {}", e.getMessage(), e);
            showAlert(Alert.AlertType.ERROR, "Initialization Error",
                    "Failed to initialize enrollment management: " + e.getMessage());
        }
    }

    // ========== INITIALIZATION METHODS ==========

    private void debouncedLoadEnrollments() {
        Timeline debouncer = new Timeline(new KeyFrame(Duration.millis(600), e -> {
            try {
                String yearText = yearComboBox.getValue();
                if (yearText != null && !yearText.trim().isEmpty()) {
                    Integer.parseInt(yearText.trim());
                    loadEnrollments();
                }
            } catch (NumberFormatException e1) {
                // Ignore - invalid input will be handled by validation
            }
        }));
        debouncer.setCycleCount(1);
        debouncer.play();
    }

    private void setupEventListeners() {
        EventBus.subscribe(EventTypes.ENROLLMENT_ADDED, this::handleExternalEnrollmentChange);
        EventBus.subscribe(EventTypes.ENROLLMENT_WITHDRAWN, this::handleExternalEnrollmentChange);
        EventBus.subscribe(EventTypes.ENROLLMENT_CHANGED, this::handleExternalEnrollmentChange);
    }

    private void handleExternalEnrollmentChange(Object data) {
        if (isTermAndYearSelected()) {
            Platform.runLater(() -> {
                PauseTransition pause = new PauseTransition(Duration.millis(300));
                pause.setOnFinished(event -> loadEnrollments());
                pause.play();
            });
        }
    }

    private void autoLoadEnrollments() {
        if (assignedClubId != null && isTermAndYearSelected()) {
            logger.info("Auto-loading enrollments for term {}, year {}",
                    termComboBox.getValue(), getSelectedYear());
            loadEnrollments();
        }
    }

    private void setupCleanupListener() {
        enrollmentsTable.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.windowProperty().addListener((windowObs, oldWindow, newWindow) -> {
                    if (newWindow != null) {
                        newWindow.setOnCloseRequest(event -> cleanupEventListeners());
                    }
                });
            }
        });
    }

    private void cleanupEventListeners() {
        EventBus.unsubscribe(EventTypes.ENROLLMENT_ADDED, this::handleExternalEnrollmentChange);
        EventBus.unsubscribe(EventTypes.ENROLLMENT_WITHDRAWN, this::handleExternalEnrollmentChange);
        EventBus.unsubscribe(EventTypes.ENROLLMENT_CHANGED, this::handleExternalEnrollmentChange);
        logger.info("Cleaned up event listeners for EnrollmentController");
    }

    private void checkUserPermissions() {
        isTeacher = SessionManager.isTeacher();
        logger.info("User is teacher: {}", isTeacher);
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

    private void setupUI() {
        termComboBox.setItems(FXCollections.observableArrayList(1, 2, 3));
        termComboBox.setPromptText("Select term");

        setupYearComboBoxAsString();
        setupYearInputValidation();

        admissionNumberColumn.setCellValueFactory(new PropertyValueFactory<>("admissionNumber"));
        learnerNameColumn.setCellValueFactory(new PropertyValueFactory<>("learnerName"));
        clubNameColumn.setCellValueFactory(new PropertyValueFactory<>("clubName"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));

        statusColumn.setCellFactory(column -> new TableCell<EnrollmentService.Enrollment, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    EnrollmentService.Enrollment enrollment = getTableView().getItems().get(getIndex());
                    if (enrollment.isActive()) {
                        setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                    }
                }
            }
        });

        enrollmentsTable.setItems(enrollments);
        enrollmentsTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
    }

    private void setupYearComboBoxAsString() {
        yearComboBox.getItems().clear();
        ObservableList<String> years = FXCollections.observableArrayList(
                "2020", "2021", "2022", "2023", "2024", "2025",
                "2026", "2027", "2028", "2029", "2030"
        );
        yearComboBox.setItems(years);
        yearComboBox.setEditable(true);
        yearComboBox.setPromptText("Enter academic year");
        yearComboBox.setValue(String.valueOf(Year.now().getValue()));
    }

    private void setupYearInputValidation() {
        yearComboBox.getEditor().focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) {
                validateYearInput();
            }
        });

        yearComboBox.getEditor().textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && !newValue.matches("\\d*")) {
                yearComboBox.getEditor().setText(newValue.replaceAll("[^\\d]", ""));
            }
        });
    }

    private void setupContextMenu() {
        ContextMenu contextMenu = new ContextMenu();

        MenuItem withdrawItem = new MenuItem("Withdraw Enrollment");
        withdrawItem.setOnAction(event -> handleWithdrawEnrollment());

        MenuItem viewDetailsItem = new MenuItem("View Full Details");
        viewDetailsItem.setOnAction(event -> showEnrollmentDetails());

        MenuItem viewHistoryItem = new MenuItem("View Learner History");
        viewHistoryItem.setOnAction(event -> {
            EnrollmentService.Enrollment selected = enrollmentsTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                showLearnerHistory(selected.getLearnerId());
            }
        });

        contextMenu.getItems().addAll(withdrawItem, new SeparatorMenuItem(), viewDetailsItem, viewHistoryItem);

        // Create a BooleanBinding for the withdrawItem disable property
        BooleanBinding disableWithdrawBinding = Bindings.createBooleanBinding(
                () -> {
                    EnrollmentService.Enrollment selected = enrollmentsTable.getSelectionModel().getSelectedItem();
                    return selected == null || !selected.canBeWithdrawn();
                },
                enrollmentsTable.getSelectionModel().selectedItemProperty()
        );

        withdrawItem.disableProperty().bind(disableWithdrawBinding);

        // Ensure context menu only shows for non-empty rows
        enrollmentsTable.setRowFactory(tv -> {
            TableRow<EnrollmentService.Enrollment> row = new TableRow<>();
            row.contextMenuProperty().bind(
                    javafx.beans.binding.Bindings.when(row.emptyProperty())
                            .then((ContextMenu) null)
                            .otherwise(contextMenu)
            );
            return row;
        });
    }

    private void setupTableSelectionListener() {
        enrollmentsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                logger.debug("Table selection changed to: {}", newSelection.getLearnerName());
            }
        });
    }

    private void setupLearnerComboBox() {
        learnerComboBox.setItems(learners);
        learnerComboBox.setEditable(true);
        learnerComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            updateLearnerInfo();
        });
    }

    private void setupAutoLoadListeners() {
        termComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && getSelectedYear() != null) {
                loadEnrollments();
            }
        });

        yearComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && !newValue.trim().isEmpty() && termComboBox.getValue() != null) {
                debouncedLoadEnrollments();
            }
        });

        yearComboBox.getEditor().textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && !newValue.trim().isEmpty() && termComboBox.getValue() != null) {
                debouncedLoadEnrollments();
            }
        });
    }

    // ========== DATA LOADING METHODS ==========

    private void loadLearners() {
        try (Connection conn = DatabaseConnector.getConnection()) {
            UUID currentUserId = SessionManager.getCurrentUserId();
            if (currentUserId != null) {
                TenantContext.setTenant(conn, currentSchoolId.toString(), currentUserId.toString());
            }

            LearnerService learnerService = new LearnerService(currentSchoolId);
            List<LearnerInfo> activeLearners = learnerService.getActiveLearners(conn, currentSchoolId);

            Platform.runLater(() -> {
                learnerInfoList.clear();
                learners.clear();
                learnerInfoList.addAll(activeLearners);

                for (LearnerInfo learner : learnerInfoList) {
                    learners.add(learner.toString());
                }

                learnerComboBox.setItems(learners);
                updateLearnerInfo();
            });

        } catch (SQLException e) {
            logger.error("Error loading learners: {}", e.getMessage());
            Platform.runLater(() -> {
                showAlert(Alert.AlertType.ERROR, "Database Error",
                        "Error loading learners: " + e.getMessage());
            });
        }
    }

    @FXML
    private void loadEnrollments() {
        Integer term = termComboBox.getValue();
        Integer year = getSelectedYear();

        if (term != null && year != null && assignedClubId != null) {
            loadEnrollmentsForTerm(term, year);
        }
    }

    private void loadEnrollmentsForTerm(int termNumber, int academicYear) {
        if (assignedClubId == null) {
            logger.warn("Cannot load enrollments - no club assigned");
            showAlert(Alert.AlertType.WARNING, "No Club", "You are not assigned to any club.");
            return;
        }

        try (Connection conn = DatabaseConnector.getConnection()) {
            UUID currentUserId = SessionManager.getCurrentUserId();
            if (currentUserId != null) {
                TenantContext.setTenant(conn, currentSchoolId.toString(), currentUserId.toString());
            }

            enrollments.clear();
            List<EnrollmentService.Enrollment> clubEnrollments =
                    enrollmentService.getEnrollmentsByClub(conn, assignedClubId, termNumber, academicYear);

            enrollments.addAll(clubEnrollments);

            Platform.runLater(() -> {
                if (enrolledCountLabel != null) {
                    long activeCount = enrollments.stream()
                            .filter(EnrollmentService.Enrollment::isActive)
                            .count();
                    enrolledCountLabel.setText(String.format("Enrolled: %d/%d learners (Active/Total)",
                            activeCount, enrollments.size()));
                }
            });

            logger.info("Loaded {} enrollments for club {}", clubEnrollments.size(), assignedClubName);

        } catch (SQLException e) {
            logger.error("Error loading enrollments for term {} year {}: {}",
                    termNumber, academicYear, e.getMessage(), e);
            showAlert(Alert.AlertType.ERROR, "Load Error",
                    "Error loading enrollments: " + e.getMessage());
        }
    }

    // ========== EVENT HANDLER METHODS ==========

    @FXML
    private void handleEnrollLearner() {
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
        Integer year = getSelectedYear();

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

        try {
            boolean alreadyEnrolled = isLearnerAlreadyEnrolled(learner.getLearnerId(), term, year);

            if (alreadyEnrolled) {
                showAlert(Alert.AlertType.WARNING, "Already Enrolled",
                        "This learner is already enrolled in your club for the selected term and year.");
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
                    EnrollmentService.EnrollmentStatus status = checkEnrollmentStatus(conn, learner.getLearnerId(), assignedClubId, term, year);

                    if (status == EnrollmentService.EnrollmentStatus.WITHDRAWN) {
                        showAlert(Alert.AlertType.INFORMATION, "Re-enrollment Success",
                                "✅ Learner re-enrolled successfully in " + assignedClubName + "!\n\n" +
                                        "The learner was previously withdrawn and has now been re-activated for this term.");
                    } else {
                        showAlert(Alert.AlertType.INFORMATION, "Enrollment Success",
                                "✅ Learner enrolled successfully in " + assignedClubName + "!");
                    }

                    loadEnrollments();
                    handleClearLearnerSelection();

                    EventBus.publish(EventTypes.ENROLLMENT_ADDED, assignedClubId);
                    EventBus.publish(EventTypes.ENROLLMENT_CHANGED);
                } else {
                    showAlert(Alert.AlertType.ERROR, "Enrollment Error", "Failed to enroll learner. Please try again.");
                }

            } catch (SQLException e) {
                handleEnrollmentError(e);
            }
        } catch (Exception e) {
            logger.error("Error during enrollment: {}", e.getMessage());
            showAlert(Alert.AlertType.ERROR, "Error",
                    "An unexpected error occurred during enrollment. Please try again.");
        }
    }

    private EnrollmentService.EnrollmentStatus checkEnrollmentStatus(Connection conn, UUID learnerId, UUID clubId, Integer term, Integer year) {
        try {
            String sql = "SELECT is_active FROM club_enrollments " +
                    "WHERE learner_id = ? AND club_id = ? AND term_number = ? AND academic_year = ? " +
                    "AND school_id = ?";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, learnerId);
                ps.setObject(2, clubId);
                ps.setInt(3, term);
                ps.setInt(4, year);
                ps.setObject(5, currentSchoolId);

                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return rs.getBoolean("is_active") ?
                            EnrollmentService.EnrollmentStatus.ACTIVE :
                            EnrollmentService.EnrollmentStatus.WITHDRAWN;
                }
                return EnrollmentService.EnrollmentStatus.NONE;
            }
        } catch (SQLException e) {
            logger.error("Error checking enrollment status: {}", e.getMessage());
            return EnrollmentService.EnrollmentStatus.NONE;
        }
    }

    @FXML
    private void handleWithdrawEnrollment() {
        if (enrollments.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No Enrollments",
                    "Please load enrollments first by selecting term/year.");
            return;
        }

        EnrollmentService.Enrollment selectedEnrollment = enrollmentsTable.getSelectionModel().getSelectedItem();

        if (selectedEnrollment == null) {
            showAlert(Alert.AlertType.WARNING, "Selection Required",
                    "Please select an enrollment from the table to withdraw.");
            return;
        }

        if (!selectedEnrollment.canBeWithdrawn()) {
            showAlert(Alert.AlertType.WARNING, "Cannot Withdraw",
                    "This enrollment is already withdrawn.");
            return;
        }

        if (!isEnrollmentInCurrentSelection(selectedEnrollment)) {
            showAlert(Alert.AlertType.WARNING, "Selection Mismatch",
                    "Selected enrollment does not match current term/year filter. Please refresh and try again.");
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirm Withdrawal");
        confirmation.setHeaderText("Confirm Enrollment Withdrawal");
        confirmation.setContentText(String.format(
                "Are you sure you want to withdraw %s from %s club?\n\nTerm: %d, Year: %d\n\nThis action cannot be undone.",
                selectedEnrollment.getLearnerName(),
                selectedEnrollment.getClubName(),
                selectedEnrollment.getTermNumber(),
                selectedEnrollment.getAcademicYear()
        ));

        Optional<ButtonType> result = confirmation.showAndWait();

        if (result.isPresent() && result.get() == ButtonType.OK) {
            logger.info("User confirmed withdrawal for enrollment: {}", selectedEnrollment.getEnrollmentId());
            performWithdrawal(selectedEnrollment);
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

            LearnerService learnerService = new LearnerService(currentSchoolId);
            List<LearnerInfo> searchResults = learnerService.searchLearners(conn, currentSchoolId, searchTerm.trim());

            learnerInfoList.clear();
            learners.clear();
            learnerInfoList.addAll(searchResults);

            for (LearnerInfo learner : learnerInfoList) {
                learners.add(learner.toString());
            }

            String currentValue = learnerComboBox.getValue();
            if (currentValue != null && !learners.contains(currentValue)) {
                learnerComboBox.setValue(null);
            }

            learnerComboBox.setItems(learners);
            if (!learners.isEmpty()) {
                learnerComboBox.show();
            }

        } catch (SQLException e) {
            logger.error("Error searching learners", e);
            showAlert(Alert.AlertType.ERROR, "Search Error", "Error searching learners: " + e.getMessage());
        }
    }

    @FXML
    private void handleClearSelections() {
        handleClearLearnerSelection();
    }

    @FXML
    private void handleRefresh() {
        loadLearners();
        loadEnrollments();
        updateLearnerInfo();
        showAlert(Alert.AlertType.INFORMATION, "Refreshed", "Data refreshed successfully.");
    }

    @FXML
    private void handleViewHistory() {
        String selectedLearner = learnerComboBox.getValue();

        if (selectedLearner == null) {
            showAlert(Alert.AlertType.WARNING, "Selection Required", "Please select a learner first.");
            return;
        }

        LearnerInfo learner = learnerInfoList.stream()
                .filter(l -> l.toString().equals(selectedLearner))
                .findFirst()
                .orElse(null);

        if (learner != null) {
            showLearnerHistory(learner.getLearnerId());
        } else {
            showAlert(Alert.AlertType.ERROR, "Error", "Could not find selected learner.");
        }
    }

    @FXML
    private void handleGenerateReport() {
        if (assignedClubId == null) {
            showAlert(Alert.AlertType.WARNING, "No Club", "You are not assigned to any club.");
            return;
        }

        Integer term = termComboBox.getValue();
        Integer year = getSelectedYear();

        if (term == null || year == null) {
            showAlert(Alert.AlertType.WARNING, "Selection Required", "Please select both term and year first.");
            return;
        }

        try (Connection conn = DatabaseConnector.getConnection()) {
            UUID currentUserId = SessionManager.getCurrentUserId();
            if (currentUserId != null) {
                TenantContext.setTenant(conn, currentSchoolId.toString(), currentUserId.toString());
            }

            List<EnrollmentService.Enrollment> enrollments = enrollmentService.getEnrollmentsByClub(
                    conn, assignedClubId, term, year);

            if (enrollments.isEmpty()) {
                showAlert(Alert.AlertType.INFORMATION, "No Data", "No enrollments found for the selected term and year.");
                return;
            }

            Dialog<Void> reportDialog = new Dialog<>();
            reportDialog.setTitle("Enrollment Report");
            reportDialog.setHeaderText("Enrollment Report for " + assignedClubName + " - Term " + term + ", " + year);

            TextArea reportContent = new TextArea();
            reportContent.setEditable(false);
            reportContent.setWrapText(true);
            reportContent.setPrefSize(600, 400);

            StringBuilder reportBuilder = new StringBuilder();
            reportBuilder.append("ENROLLMENT REPORT\n");
            reportBuilder.append("=================\n");
            reportBuilder.append("Club: ").append(assignedClubName).append("\n");
            reportBuilder.append("Term: ").append(term).append("\n");
            reportBuilder.append("Academic Year: ").append(year).append("\n");
            reportBuilder.append("Total Enrollments: ").append(enrollments.size()).append("\n\n");
            reportBuilder.append("ENROLLED LEARNERS:\n");
            reportBuilder.append("=================\n");

            for (EnrollmentService.Enrollment enrollment : enrollments) {
                reportBuilder.append("• ").append(enrollment.getLearnerName())
                        .append(" (").append(enrollment.getAdmissionNumber()).append(") - ")
                        .append(enrollment.getGradeName()).append("\n");
            }

            reportContent.setText(reportBuilder.toString());
            reportDialog.getDialogPane().setContent(reportContent);
            reportDialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            reportDialog.showAndWait();

        } catch (SQLException e) {
            logger.error("Error generating report: {}", e.getMessage(), e);
            showAlert(Alert.AlertType.ERROR, "Report Error", "Error generating report: " + e.getMessage());
        }
    }

    @FXML
    private void handleExportExcel() {
        if (assignedClubId == null) {
            showAlert(Alert.AlertType.WARNING, "No Club", "You are not assigned to any club.");
            return;
        }

        Integer term = termComboBox.getValue();
        Integer year = getSelectedYear();

        if (term == null || year == null) {
            showAlert(Alert.AlertType.WARNING, "Selection Required", "Please select both term and year first.");
            return;
        }

        try (Connection conn = DatabaseConnector.getConnection()) {
            UUID currentUserId = SessionManager.getCurrentUserId();
            if (currentUserId != null) {
                TenantContext.setTenant(conn, currentSchoolId.toString(), currentUserId.toString());
            }

            // Fetch enrollment data
            List<EnrollmentService.Enrollment> enrollments = enrollmentService.getEnrollmentsByClub(
                    conn, assignedClubId, term, year);

            if (enrollments.isEmpty()) {
                showAlert(Alert.AlertType.INFORMATION, "No Data", "No enrollments found for the selected term and year.");
                return;
            }

            // Create Excel workbook and sheet
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("Enrollments");

            // Create header row
            Row headerRow = sheet.createRow(0);
            String[] headers = {
                    "Admission Number", "Learner Name", "Club Name", "Grade", "Gender",
                    "Term", "Academic Year", "Status", "Enrollment Date", "Last Updated"
            };

            // Style for header
            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Populate header row
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Populate data rows
            int rowNum = 1;
            for (EnrollmentService.Enrollment enrollment : enrollments) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(enrollment.getAdmissionNumber());
                row.createCell(1).setCellValue(enrollment.getLearnerName());
                row.createCell(2).setCellValue(enrollment.getClubName());
                row.createCell(3).setCellValue(enrollment.getGradeName());
                row.createCell(4).setCellValue(enrollment.getGender());
                row.createCell(5).setCellValue(enrollment.getTermNumber());
                row.createCell(6).setCellValue(enrollment.getAcademicYear());
                row.createCell(7).setCellValue(enrollment.isActive() ? "Active" : "Withdrawn");
                row.createCell(8).setCellValue(
                        enrollment.getEnrollmentDate() != null ?
                                enrollment.getEnrollmentDate().toLocalDateTime().toString() : "N/A"
                );
                row.createCell(9).setCellValue(
                        enrollment.getUpdatedAt() != null ?
                                enrollment.getUpdatedAt().toLocalDateTime().toString() : "N/A"
                );
            }

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Prompt user to save the file
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save Enrollment Report");
            fileChooser.setInitialFileName("Enrollment_" + assignedClubName + "_Term" + term + "_" + year + ".xlsx");
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Excel Files", "*.xlsx")
            );

            Stage stage = (Stage) enrollmentsTable.getScene().getWindow();
            File file = fileChooser.showSaveDialog(stage);

            if (file != null) {
                try (FileOutputStream fileOut = new FileOutputStream(file)) {
                    workbook.write(fileOut);
                    showAlert(Alert.AlertType.INFORMATION, "Export Successful",
                            "Enrollment data exported successfully to " + file.getAbsolutePath());
                    logger.info("Exported enrollment data to {}", file.getAbsolutePath());
                } catch (IOException e) {
                    logger.error("Error writing Excel file: {}", e.getMessage(), e);
                    showAlert(Alert.AlertType.ERROR, "Export Error",
                            "Failed to save Excel file: " + e.getMessage());
                }
            } else {
                logger.info("Excel export cancelled by user");
            }

            // Close the workbook
            workbook.close();

        } catch (SQLException e) {
            logger.error("Error fetching enrollment data for export: {}", e.getMessage(), e);
            showAlert(Alert.AlertType.ERROR, "Database Error",
                    "Error fetching enrollment data: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during Excel export: {}", e.getMessage(), e);
            showAlert(Alert.AlertType.ERROR, "Export Error",
                    "An unexpected error occurred during export: " + e.getMessage());
        }
    }

    // ========== PRIVATE HELPER METHODS ==========

    private void performWithdrawal(EnrollmentService.Enrollment enrollment) {
        try (Connection conn = DatabaseConnector.getConnection()) {
            UUID teacherId = SessionManager.getCurrentUserId();
            UUID schoolId = SessionManager.getCurrentSchoolId();

            if (teacherId != null && schoolId != null) {
                TenantContext.setTenant(conn, schoolId.toString(), teacherId.toString());
            }

            logger.info("Attempting to withdraw enrollment ID: {}", enrollment.getEnrollmentId());

            boolean success = enrollmentService.withdrawEnrollment(conn, enrollment.getEnrollmentId(), teacherId);

            if (success) {
                logger.info("Withdrawal successful for enrollment: {}", enrollment.getEnrollmentId());
                showSuccessWithdrawalAlert(enrollment);
                refreshEnrollmentsAfterWithdrawal();
                publishWithdrawalEvents();
            } else {
                logger.error("Withdrawal failed for enrollment: {}", enrollment.getEnrollmentId());
                showAlert(Alert.AlertType.ERROR, "Withdrawal Error",
                        "Failed to withdraw enrollment. Please check the logs for details.");
            }

        } catch (SecurityException e) {
            logger.error("Authorization error during withdrawal: {}", e.getMessage());
            showAlert(Alert.AlertType.ERROR, "Authorization Error",
                    "You are not authorized to withdraw enrollments from this club.");
        } catch (SQLException e) {
            logger.error("Database error during withdrawal: {}", e.getMessage(), e);
            handleWithdrawalError(e);
        } catch (Exception e) {
            logger.error("Unexpected error during withdrawal: {}", e.getMessage(), e);
            showAlert(Alert.AlertType.ERROR, "Error", "An unexpected error occurred: " + e.getMessage());
        }
    }

    private void handleEnrollmentError(SQLException e) {
        String errorMessage = e.getMessage();
        String userFriendlyMessage;
        Alert.AlertType alertType;

        if (errorMessage.contains("Learner is already actively enrolled in this club for the selected term and year")) {
            userFriendlyMessage = "This learner is already actively enrolled in this club for the selected term and year.\n\n" +
                    "Please select a different learner or check the current enrollments list.";
            alertType = Alert.AlertType.WARNING;
        }
        else if (errorMessage.contains("Learner is already enrolled in another club for this term")) {
            userFriendlyMessage = "This learner is already enrolled in another club for this term.\n\n" +
                    "A learner can only be enrolled in one club per term. Please select a different learner.";
            alertType = Alert.AlertType.WARNING;
        }
        else if (errorMessage.contains("duplicate key value violates unique constraint") ||
                errorMessage.contains("already enrolled")) {
            userFriendlyMessage = "This learner appears to already have an enrollment record.\n\n" +
                    "Please check the current enrollments list or try refreshing the data.";
            alertType = Alert.AlertType.WARNING;
        }
        else if (errorMessage.contains("foreign key") || errorMessage.contains("constraint")) {
            userFriendlyMessage = "There seems to be an issue with the learner or club data.\n\n" +
                    "Please contact your system administrator if this problem persists.";
            alertType = Alert.AlertType.ERROR;
        }
        else if (errorMessage.contains("permission denied") || errorMessage.contains("authorization")) {
            userFriendlyMessage = "You don't have permission to perform this action.\n\n" +
                    "Please contact your club coordinator or administrator.";
            alertType = Alert.AlertType.ERROR;
        }
        else {
            userFriendlyMessage = "An error occurred while enrolling the learner.\n\nPlease try again or contact support if the problem continues.";
            alertType = Alert.AlertType.ERROR;
        }

        showAlert(alertType, "Enrollment Issue", userFriendlyMessage);
        logger.warn("Enrollment error handled: {}", errorMessage);
    }

    private void handleWithdrawalError(SQLException e) {
        String errorMessage = e.getMessage();

        if (errorMessage.contains("already withdrawn")) {
            showAlert(Alert.AlertType.WARNING, "Already Withdrawn", "This enrollment has already been withdrawn.");
        } else if (errorMessage.contains("permission denied") || errorMessage.contains("authorization")) {
            showAlert(Alert.AlertType.ERROR, "Permission Denied",
                    "You do not have permission to withdraw enrollments from this club.");
        } else if (errorMessage.contains("RLS") || errorMessage.contains("row-level security")) {
            showAlert(Alert.AlertType.ERROR, "Security Policy Violation",
                    "Access denied by security policy. Please contact administrator.");
        } else {
            showAlert(Alert.AlertType.ERROR, "Database Error", "Error withdrawing enrollment: " + e.getMessage());
        }
    }

    private boolean isLearnerAlreadyEnrolled(UUID learnerId, Integer term, Integer year) {
        if (learnerId == null || term == null || year == null || assignedClubId == null) {
            return false;
        }

        try (Connection conn = DatabaseConnector.getConnection()) {
            String sql = "SELECT is_active FROM club_enrollments " +
                    "WHERE learner_id = ? AND club_id = ? AND term_number = ? AND academic_year = ? " +
                    "AND school_id = ?";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, learnerId);
                ps.setObject(2, assignedClubId);
                ps.setInt(3, term);
                ps.setInt(4, year);
                ps.setObject(5, currentSchoolId);

                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    boolean isActive = rs.getBoolean("is_active");
                    return isActive; // Return true only for active enrollments
                }
                return false;
            }
        } catch (SQLException e) {
            logger.error("Error checking if learner is already enrolled", e);
            return false;
        }
    }

    private void updateLearnerInfo() {
        String selected = learnerComboBox.getValue();

        if (selected != null && !selected.trim().isEmpty()) {
            LearnerInfo learner = learnerInfoList.stream()
                    .filter(l -> l.toString().equals(selected))
                    .findFirst()
                    .orElse(null);

            if (learner != null) {
                selectedLearnerName.setText(learner.getFullName());
                selectedLearnerGrade.setText(learner.getGradeName());
                selectedLearnerAdmission.setText(learner.getAdmissionNumber());
                return;
            }
        }

        selectedLearnerName.setText("Not selected");
        selectedLearnerGrade.setText("-");
        selectedLearnerAdmission.setText("-");
    }

    private void handleClearLearnerSelection() {
        learnerComboBox.setValue(null);
        learnerComboBox.getEditor().clear();
        selectedLearnerName.setText("Not selected");
        selectedLearnerGrade.setText("-");
        selectedLearnerAdmission.setText("-");
    }

    private void validateYearInput() {
        String yearText = yearComboBox.getValue();
        if (yearText == null || yearText.trim().isEmpty()) {
            yearText = yearComboBox.getEditor().getText();
        }

        if (yearText == null || yearText.trim().isEmpty()) {
            setDefaultYearValue();
            return;
        }

        try {
            int year = Integer.parseInt(yearText.trim());
            if (year < 2000 || year > 2100) {
                showAlert(Alert.AlertType.WARNING, "Invalid Year", "Please enter a year between 2000 and 2100");
                setDefaultYearValue();
            } else {
                // Valid year - ensure it's properly formatted
                yearComboBox.setValue(String.valueOf(year));
            }
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.ERROR, "Invalid Input", "Please enter a valid year number");
            setDefaultYearValue();
        }
    }

    private void setDefaultYearValue() {
        int currentYear = Year.now().getValue();
        yearComboBox.setValue(String.valueOf(currentYear));
    }

    private Integer getSelectedYear() {
        try {
            // Get the selected value as String
            String yearValue = yearComboBox.getValue();

            // If value is null, try to get text from editor
            if (yearValue == null || yearValue.trim().isEmpty()) {
                yearValue = yearComboBox.getEditor().getText();
            }

            if (yearValue != null && !yearValue.trim().isEmpty()) {
                return Integer.parseInt(yearValue.trim());
            }

            // Fallback to current year
            return Year.now().getValue();

        } catch (NumberFormatException e) {
            logger.warn("Invalid year format '{}', using current year as fallback", yearComboBox.getValue());
            // Reset to current year
            int currentYear = Year.now().getValue();
            yearComboBox.setValue(String.valueOf(currentYear));
            return currentYear;
        }
    }

    private boolean isTermAndYearSelected() {
        Integer term = termComboBox.getValue();
        Integer year = getSelectedYear();
        return term != null && year != null;
    }

    private boolean isEnrollmentInCurrentSelection(EnrollmentService.Enrollment enrollment) {
        Integer currentTerm = termComboBox.getValue();
        Integer currentYear = getSelectedYear();
        return currentTerm != null && currentYear != null &&
                enrollment.getTermNumber() == currentTerm &&
                enrollment.getAcademicYear() == currentYear;
    }

    private void disableEntireUI() {
        learnerComboBox.setDisable(true);
        termComboBox.setDisable(true);
        yearComboBox.setDisable(true);
        enrollmentsTable.setDisable(true);
    }

    private void showSuccessWithdrawalAlert(EnrollmentService.Enrollment enrollment) {
        Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
        successAlert.setTitle("Withdrawal Successful");
        successAlert.setHeaderText("✅ Enrollment Withdrawn Successfully");

        String contentText = String.format(
                "Learner %s has been successfully withdrawn from %s club.\n\n" +
                        "The enrollment has been marked as withdrawn and an audit log entry has been created.",
                enrollment.getLearnerName(), enrollment.getClubName()
        );

        successAlert.setContentText(contentText);
        successAlert.showAndWait();
    }

    private void refreshEnrollmentsAfterWithdrawal() {
        Integer term = termComboBox.getValue();
        Integer year = getSelectedYear();

        if (term != null && year != null) {
            loadEnrollmentsForTerm(term, year);
        }
    }

    private void publishWithdrawalEvents() {
        EventBus.publish(EventTypes.ENROLLMENT_WITHDRAWN, assignedClubId);
        EventBus.publish(EventTypes.ENROLLMENT_CHANGED);
        EventBus.publish(EventTypes.CLUB_STATS_UPDATED);
    }

    private void showEnrollmentDetails() {
        EnrollmentService.Enrollment selected = enrollmentsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select an enrollment to view details.");
            return;
        }

        StringBuilder details = new StringBuilder();
        details.append("ENROLLMENT DETAILS\n");
        details.append("=================\n\n");
        details.append("Learner Information:\n");
        details.append("• Name: ").append(selected.getLearnerName()).append("\n");
        details.append("• Admission: ").append(selected.getAdmissionNumber()).append("\n");
        details.append("• Grade: ").append(selected.getGradeName()).append("\n");
        details.append("• Gender: ").append(selected.getGender()).append("\n\n");
        details.append("Club Information:\n");
        details.append("• Club: ").append(selected.getClubName()).append("\n");
        details.append("• Term: ").append(selected.getTermNumber()).append("\n");
        details.append("• Year: ").append(selected.getAcademicYear()).append("\n");
        details.append("• Status: ").append(selected.getStatus()).append("\n");
        details.append("• Enrollment Date: ").append(selected.getEnrollmentDate()).append("\n");
        details.append("• Last Updated: ").append(
                selected.getUpdatedAt() != null ?
                        selected.getUpdatedAt().toLocalDateTime().toString() : "N/A"
        ).append("\n");

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Enrollment Details");
        alert.setHeaderText("Complete Enrollment Information");
        alert.setContentText(details.toString());
        alert.getDialogPane().setMinSize(400, 300);
        alert.setResizable(true);
        alert.showAndWait();
    }

    private void showLearnerHistory(UUID learnerId) {
        try {
            List<EnrollmentService.Enrollment> history = enrollmentService.getLearnerEnrollmentHistory(learnerId);

            Dialog<Void> dialog = new Dialog<>();
            dialog.setTitle("Learner Enrollment History");
            dialog.setHeaderText("Enrollment history for selected learner");

            VBox content = new VBox(10);
            content.setPadding(new Insets(10));

            if (history.isEmpty()) {
                Label noDataLabel = new Label("No enrollment history found for this learner.");
                noDataLabel.setStyle("-fx-text-fill: gray; -fx-font-style: italic;");
                content.getChildren().add(noDataLabel);
            } else {
                TableView<EnrollmentService.Enrollment> table = new TableView<>();

                TableColumn<EnrollmentService.Enrollment, String> clubCol = new TableColumn<>("Club");
                clubCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getClubName()));
                clubCol.setPrefWidth(150);

                TableColumn<EnrollmentService.Enrollment, Integer> termCol = new TableColumn<>("Term");
                termCol.setCellValueFactory(new PropertyValueFactory<>("termNumber"));
                termCol.setPrefWidth(80);

                TableColumn<EnrollmentService.Enrollment, Integer> yearCol = new TableColumn<>("Year");
                yearCol.setCellValueFactory(new PropertyValueFactory<>("academicYear"));
                yearCol.setPrefWidth(80);

                TableColumn<EnrollmentService.Enrollment, String> statusCol = new TableColumn<>("Status");
                statusCol.setCellValueFactory(cellData ->
                        new SimpleStringProperty(cellData.getValue().isActive() ? "Active" : "Withdrawn"));
                statusCol.setPrefWidth(100);

                TableColumn<EnrollmentService.Enrollment, String> dateCol = new TableColumn<>("Enrollment Date");
                dateCol.setCellValueFactory(cellData -> {
                    Timestamp timestamp = cellData.getValue().getEnrollmentDate();
                    String dateStr = (timestamp != null) ? timestamp.toLocalDateTime().toLocalDate().toString() : "N/A";
                    return new SimpleStringProperty(dateStr);
                });
                dateCol.setPrefWidth(120);

                TableColumn<EnrollmentService.Enrollment, String> updatedCol = new TableColumn<>("Last Updated");
                updatedCol.setCellValueFactory(cellData -> {
                    Timestamp timestamp = cellData.getValue().getUpdatedAt();
                    String dateStr = (timestamp != null) ? timestamp.toLocalDateTime().toLocalDate().toString() : "N/A";
                    return new SimpleStringProperty(dateStr);
                });
                updatedCol.setPrefWidth(120);

                table.getColumns().addAll(clubCol, termCol, yearCol, statusCol, dateCol, updatedCol);

                table.setItems(FXCollections.observableArrayList(history));
                table.setPrefSize(600, 400);

                content.getChildren().add(table);

                Label summaryLabel = new Label(String.format("Total enrollments: %d (Active: %d, Withdrawn: %d)",
                        history.size(),
                        history.stream().filter(EnrollmentService.Enrollment::isActive).count(),
                        history.stream().filter(e -> !e.isActive()).count()));
                summaryLabel.setStyle("-fx-font-weight: bold;");
                content.getChildren().add(summaryLabel);
            }

            dialog.getDialogPane().setContent(content);
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            dialog.getDialogPane().setPrefSize(650, 450);
            dialog.showAndWait();

        } catch (SQLException e) {
            logger.error("Error loading learner history: {}", e.getMessage(), e);
            showAlert(Alert.AlertType.ERROR, "Database Error", "Error loading history: " + e.getMessage());
        }
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}