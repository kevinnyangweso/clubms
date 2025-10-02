package com.cms.clubmanagementsystem.controller;

import com.cms.clubmanagementsystem.utils.DatabaseConnector;
import com.cms.clubmanagementsystem.utils.SessionManager;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import org.mindrot.jbcrypt.BCrypt;

import java.net.URL;
import java.security.SecureRandom;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javafx.scene.control.Tooltip;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;

public class TeacherManagementController implements Initializable {

    // UI Components
    @FXML private TableView<Teacher> teachersTable;
    @FXML private TableColumn<Teacher, String> nameColumn;
    @FXML private TableColumn<Teacher, String> emailColumn;
    @FXML private TableColumn<Teacher, String> phoneColumn;
    @FXML private TableColumn<Teacher, String> statusColumn;
    @FXML private TableColumn<Teacher, String> clubColumn;
    @FXML private Button activateButton;
    @FXML private Button deactivateButton;
    @FXML private Button refreshButton;
    @FXML private HBox statusContainer;
    @FXML private Button updateButton;
    @FXML private TableColumn<Teacher, String> gradeColumn;

    // Data collections
    private ObservableList<Teacher> teachers = FXCollections.observableArrayList();
    private ObservableList<String> clubs = FXCollections.observableArrayList();

    // Grade-related variables
    private ObservableList<String> grades = FXCollections.observableArrayList();
    private Map<String, UUID> gradeNameToIdMap = new HashMap<>();

    // Constants
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w-.]+@([\\w-]+\\.)+[\\w-]{2,4}$");
    private static final String PHONE_FORMAT_HINT = "+254712345678";

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Check if user has at least coordinator access
        if (!SessionManager.isCoordinator()) {
            showAlert(Alert.AlertType.ERROR, "Access Denied",
                    "You do not have coordinator permissions to access teacher management.");
            // Optionally disable the entire UI
            teachersTable.setDisable(true);
            refreshButton.setDisable(true);
            activateButton.setDisable(true);
            deactivateButton.setDisable(true);
            updateButton.setDisable(true);
            return;
        }

        setupTable();
        loadData();
        updateUIForPermissions();
        addPermissionStatusIndicator();
    }

    // ===== INITIALIZATION METHODS =====

    private void setupTable() {
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("fullName"));
        emailColumn.setCellValueFactory(new PropertyValueFactory<>("email"));
        phoneColumn.setCellValueFactory(new PropertyValueFactory<>("phone"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        clubColumn.setCellValueFactory(new PropertyValueFactory<>("clubName"));

        teachersTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSelection, newSelection) -> updateButtonStates(newSelection)
        );

        gradeColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getGradesFormatted()));
        gradeColumn.setCellFactory(column -> new TableCell<Teacher, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setTooltip(empty ? null : new Tooltip(item));
            }
        });
    }

    private void loadData() {
        loadTeachers();
        loadClubs();
        loadGrades();
    }

    // Method to load grades
    private void loadGrades() {
        grades.clear();
        gradeNameToIdMap.clear();

        String sql = "SELECT grade_id, grade_name FROM grades WHERE school_id = ? ORDER BY grade_name";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, SessionManager.getCurrentSchoolId());
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String gradeName = rs.getString("grade_name");
                UUID gradeId = (UUID) rs.getObject("grade_id");
                grades.add(gradeName);
                gradeNameToIdMap.put(gradeName, gradeId);
            }
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to load grades: " + e.getMessage());
        }
    }

    // ===== DATA LOADING METHODS =====

    // Update the loadTeachers method to load grade information
    private void loadTeachers() {
        teachers.clear();
        String sql = """
    SELECT u.user_id, u.username, u.full_name, u.email, u.phone, 
           u.is_active, c.club_name, u.created_at, c.club_id
    FROM users u
    LEFT JOIN club_teachers ct ON u.user_id = ct.teacher_id
    LEFT JOIN clubs c ON ct.club_id = c.club_id
    WHERE u.school_id = ? 
    AND u.role = 'teacher'
    ORDER BY u.full_name
    """;

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, SessionManager.getCurrentSchoolId());
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                Teacher teacher = new Teacher(
                        (UUID) rs.getObject("user_id"),
                        rs.getString("username"),
                        rs.getString("full_name"),
                        rs.getString("email"),
                        rs.getString("phone"),
                        rs.getBoolean("is_active"),
                        rs.getString("club_name"),
                        rs.getTimestamp("created_at"),
                        (UUID) rs.getObject("club_id")
                );

                // Load assigned grades for this teacher
                loadTeacherGrades(teacher);
                teachers.add(teacher);
            }

            teachersTable.setItems(teachers);
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to load teachers: " + e.getMessage());
        }
    }

    // Method to load grades for a specific teacher
    private void loadTeacherGrades(Teacher teacher) {
        String sql = """
        SELECT g.grade_name 
        FROM teacher_grades tg
        JOIN grades g ON tg.grade_id = g.grade_id
        WHERE tg.teacher_id = ? AND tg.school_id = ?
        ORDER BY g.grade_name
    """;

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, teacher.getId());
            stmt.setObject(2, SessionManager.getCurrentSchoolId());
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                teacher.getAssignedGrades().add(rs.getString("grade_name"));
            }
        } catch (SQLException e) {
            // Log error
        }
    }

    private void loadClubs() {
        clubs.clear();
        String sql = "SELECT club_id, club_name FROM clubs WHERE school_id = ? AND is_active = true ORDER BY club_name";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, SessionManager.getCurrentSchoolId());
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                clubs.add(rs.getString("club_name"));
            }
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to load clubs: " + e.getMessage());
        }
    }

    // ===== PERMISSION METHODS =====

    private void updateUIForPermissions() {
        boolean isActiveCoordinator = SessionManager.isActiveCoordinator();
        boolean isCoordinator = SessionManager.isCoordinator();

        activateButton.setDisable(!isActiveCoordinator);
        deactivateButton.setDisable(!isActiveCoordinator);
        updateButton.setDisable(!isActiveCoordinator);

        // Allow view access for any coordinator
        refreshButton.setDisable(!isCoordinator);
        teachersTable.setDisable(!isCoordinator);

        if (!isActiveCoordinator) {
            Tooltip permissionTooltip = new Tooltip("Only active coordinators can perform this action");
            activateButton.setTooltip(permissionTooltip);
            deactivateButton.setTooltip(permissionTooltip);
            updateButton.setTooltip(permissionTooltip);

            if (!isCoordinator) {
                Tooltip viewTooltip = new Tooltip("Coordinator access required to view teacher management");
                refreshButton.setTooltip(viewTooltip);
                teachersTable.setTooltip(viewTooltip);
            } else {
                refreshButton.setTooltip(null);
                teachersTable.setTooltip(null);
            }
        } else {
            activateButton.setTooltip(null);
            deactivateButton.setTooltip(null);
            updateButton.setTooltip(null);
            refreshButton.setTooltip(null);
            teachersTable.setTooltip(null);
        }
    }

    private void addPermissionStatusIndicator() {
        boolean isActiveCoordinator = SessionManager.isActiveCoordinator();
        boolean isCoordinator = SessionManager.isCoordinator();

        String statusText;
        if (isActiveCoordinator) {
            statusText = "Active Coordinator (Full Permissions)";
        } else if (isCoordinator) {
            statusText = "Inactive Coordinator (View Only)";
        } else {
            statusText = "No Coordinator Access";
        }

        Label permissionLabel = new Label(statusText);
        if (isActiveCoordinator) {
            permissionLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
        } else if (isCoordinator) {
            permissionLabel.setStyle("-fx-text-fill: orange; -fx-font-weight: bold;");
        } else {
            permissionLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
        }

        statusContainer.getChildren().clear();
        statusContainer.getChildren().add(permissionLabel);
    }

    private void updateButtonStates(Teacher selectedTeacher) {
        if (selectedTeacher != null) {
            activateButton.setDisable(selectedTeacher.isActive());
            deactivateButton.setDisable(!selectedTeacher.isActive());
            updateButton.setDisable(false);
        } else {
            activateButton.setDisable(true);
            deactivateButton.setDisable(true);
            updateButton.setDisable(true);
        }
    }

    // ===== TEACHER CREATION =====

    @FXML
    private void handleCreateTeacher() {
        if (!SessionManager.isActiveCoordinator()) {
            showAlert(Alert.AlertType.ERROR, "Permission Denied",
                    "Only active coordinators can create new teachers.");
            return;
        }

        String tempPassword = generateTemporaryPassword();
        Dialog<TeacherCreationData> dialog = createTeacherDialog(tempPassword);

        dialog.showAndWait().ifPresent(result -> {
            boolean success = createTeacher(
                    result.fullName(),
                    result.email(),
                    result.username(),
                    tempPassword,
                    result.phone(),
                    result.clubId(),
                    result.gradeIds()
            );
        });
    }

    private Dialog<TeacherCreationData> createTeacherDialog(String tempPassword) {
        Dialog<TeacherCreationData> dialog = new Dialog<>();
        dialog.setTitle("Create New Teacher");
        dialog.setHeaderText("Enter teacher details. A temporary password will be generated.");

        ButtonType createButtonType = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createButtonType, ButtonType.CANCEL);

        // Get the form components map
        Map<String, Object> formComponents = createTeacherFormGrid(tempPassword);
        GridPane grid = (GridPane) formComponents.get("grid");
        dialog.getDialogPane().setContent(grid);

        Node createButtonNode = dialog.getDialogPane().lookupButton(createButtonType);

        // Pass the components map to the validation method
        setupFormValidation(formComponents, createButtonNode);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == createButtonType) {
                // Pass the components map to the extract method
                return extractTeacherCreationData(formComponents);
            }
            return null;
        });

        return dialog;
    }

    private Map<String, Object> createTeacherFormGrid(String tempPassword) {
        Map<String, Object> components = new HashMap<>();
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField fullNameField = new TextField();
        TextField emailField = new TextField();
        TextField usernameField = new TextField();
        TextField phoneField = new TextField();
        phoneField.setPromptText(PHONE_FORMAT_HINT);
        ComboBox<String> clubComboBox = new ComboBox<>(clubs);

        TextField tempPasswordField = new TextField(tempPassword);
        tempPasswordField.setEditable(false);
        tempPasswordField.setStyle("-fx-background-color: #f0f0f0;");

        Label fullNameError = createErrorLabel();
        Label emailError = createErrorLabel();
        Label usernameError = createErrorLabel();
        Label phoneError = createErrorLabel();
        Label clubError = createErrorLabel();

        Label phoneHint = new Label("Format: +[country code][number]");
        phoneHint.setStyle("-fx-font-size: 10px; -fx-text-fill: gray;");

        // Grade selection components
        ListView<String> gradesListView = new ListView<>(grades);
        gradesListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        gradesListView.setPrefHeight(120);

        Label gradesLabel = new Label("Grades (Optional):");
        Label gradesHint = new Label("Select grades this teacher is responsible for. Leave empty for all grades.");
        gradesHint.setStyle("-fx-font-size: 10px; -fx-text-fill: gray;");

        // Add components to grid
        addFormRow(grid, "Full Name*:", fullNameField, fullNameError, 0);
        addFormRow(grid, "Email*:", emailField, emailError, 1);
        addFormRow(grid, "Username*:", usernameField, usernameError, 2);
        addFormRow(grid, "Phone*:", phoneField, phoneHint, 3);
        grid.add(phoneError, 1, 4);
        addFormRow(grid, "Club*:", clubComboBox, clubError, 5);

        grid.add(new Label("Temporary Password:"), 0, 6);
        grid.add(tempPasswordField, 1, 6);
        grid.add(new Label("Share this with the teacher"), 2, 6);
        grid.add(gradesLabel, 0, 7);
        grid.add(gradesListView, 1, 7);
        grid.add(gradesHint, 2, 7);

        // Store all components in the map
        components.put("grid", grid);
        components.put("fullNameField", fullNameField);
        components.put("emailField", emailField);
        components.put("usernameField", usernameField);
        components.put("phoneField", phoneField);
        components.put("clubComboBox", clubComboBox);
        components.put("tempPasswordField", tempPasswordField);
        components.put("fullNameError", fullNameError);
        components.put("emailError", emailError);
        components.put("usernameError", usernameError);
        components.put("phoneError", phoneError);
        components.put("clubError", clubError);
        components.put("gradesListView", gradesListView);

        return components;
    }

    private void addFormRow(GridPane grid, String label, Control field, Node error, int row) {
        grid.add(new Label(label), 0, row);
        grid.add(field, 1, row);
        if (error != null) {
            grid.add(error, 2, row);
        }
    }

    private void setupFormValidation(Map<String, Object> components, Node createButtonNode) {
        // Extract components from the map
        TextField fullNameField = (TextField) components.get("fullNameField");
        TextField emailField = (TextField) components.get("emailField");
        TextField usernameField = (TextField) components.get("usernameField");
        TextField phoneField = (TextField) components.get("phoneField");
        ComboBox<String> clubComboBox = (ComboBox<String>) components.get("clubComboBox");
        Label fullNameError = (Label) components.get("fullNameError");
        Label emailError = (Label) components.get("emailError");
        Label usernameError = (Label) components.get("usernameError");
        Label phoneError = (Label) components.get("phoneError");
        Label clubError = (Label) components.get("clubError");

        createButtonNode.setDisable(true);

        ChangeListener<String> validationListener = (observable, oldValue, newValue) -> {
            boolean isValid = validateCreateForm(
                    fullNameField.getText().trim(),
                    emailField.getText().trim(),
                    usernameField.getText().trim(),
                    phoneField.getText().trim(),
                    clubComboBox.getValue(),
                    fullNameError,
                    emailError,
                    usernameError,
                    phoneError,
                    clubError
            );
            createButtonNode.setDisable(!isValid);
        };

        fullNameField.textProperty().addListener(validationListener);
        emailField.textProperty().addListener(validationListener);
        usernameField.textProperty().addListener(validationListener);
        phoneField.textProperty().addListener(validationListener);
        clubComboBox.valueProperty().addListener((obs, oldVal, newVal) -> validationListener.changed(null, null, null));

        validationListener.changed(null, null, null);
    }

    private TeacherCreationData extractTeacherCreationData(Map<String, Object> components) {
        // Extract components from the map
        TextField fullNameField = (TextField) components.get("fullNameField");
        TextField emailField = (TextField) components.get("emailField");
        TextField usernameField = (TextField) components.get("usernameField");
        TextField phoneField = (TextField) components.get("phoneField");
        ComboBox<String> clubComboBox = (ComboBox<String>) components.get("clubComboBox");
        ListView<String> gradesListView = (ListView<String>) components.get("gradesListView");

        UUID clubId = getClubIdByName(clubComboBox.getValue());
        if (clubId == null) {
            showAlert(Alert.AlertType.ERROR, "Error", "Selected club not found");
            return null;
        }

        // Extract selected grades and sort them alphabetically
        List<UUID> selectedGradeIds = gradesListView.getSelectionModel().getSelectedItems().stream()
                .sorted()
                .map(gradeName -> gradeNameToIdMap.get(gradeName))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return new TeacherCreationData(
                fullNameField.getText().trim(),
                emailField.getText().trim(),
                usernameField.getText().trim(),
                phoneField.getText().trim(),
                clubId,
                selectedGradeIds
        );
    }

    private boolean createTeacher(String fullName, String email, String username, String tempPassword,
                                  String phone, UUID clubId, List<UUID> gradeIds) {

        Connection conn = null;
        try {
            conn = DatabaseConnector.getConnection();
            conn.setAutoCommit(false);

            // Call the stored procedure to create teacher
            String sql = "SELECT create_teacher_with_club(?, ?, ?, ?, ?, ?, ?)";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, fullName);
                stmt.setString(2, email);
                stmt.setString(3, username);
                stmt.setString(4, tempPassword);
                stmt.setString(5, phone);
                stmt.setObject(6, clubId);
                stmt.setObject(7, SessionManager.getCurrentSchoolId());

                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    UUID userId = (UUID) rs.getObject(1);
                    if (userId != null) {
                        // Assign grades if any are selected
                        if (gradeIds != null && !gradeIds.isEmpty()) {
                            assignGradesToTeacher(conn, userId, gradeIds);
                        }

                        conn.commit();
                        loadTeachers();
                        showAlert(Alert.AlertType.INFORMATION, "Success",
                                "Teacher created successfully and assigned to club!" +
                                        (gradeIds != null && !gradeIds.isEmpty() ? " Grades assigned." : ""));
                        return true;
                    }
                }
                conn.rollback();
                return false;
            }
        } catch (SQLException e) {
            rollbackTransaction(conn);
            handleDatabaseError(e);
            return false;
        } finally {
            closeConnection(conn);
        }
    }

    // Method to assign grades to teacher
    private boolean assignGradesToTeacher(Connection conn, UUID teacherId, List<UUID> gradeIds) throws SQLException {
        String sql = "INSERT INTO teacher_grades (teacher_id, grade_id, school_id) VALUES (?, ?, ?)";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (UUID gradeId : gradeIds) {
                stmt.setObject(1, teacherId);
                stmt.setObject(2, gradeId);
                stmt.setObject(3, SessionManager.getCurrentSchoolId());
                stmt.addBatch();
            }
            stmt.executeBatch();
            return true;
        }
    }

    // ===== TEACHER UPDATE =====

    @FXML
    private void handleUpdateTeacher() {
        if (!SessionManager.isActiveCoordinator()) {
            showAlert(Alert.AlertType.ERROR, "Permission Denied",
                    "Only active coordinators can update teachers.");
            return;
        }

        Teacher selectedTeacher = teachersTable.getSelectionModel().getSelectedItem();
        if (selectedTeacher == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select a teacher to update.");
            return;
        }

        Dialog<TeacherUpdateData> dialog = createUpdateDialog(selectedTeacher);
        dialog.showAndWait().ifPresent(updateData -> {
            boolean success = updateTeacher(updateData);
            if (success) {
                showAlert(Alert.AlertType.INFORMATION, "Success",
                        "Teacher details updated successfully!");
                loadTeachers();
            }
        });
    }

    private Dialog<TeacherUpdateData> createUpdateDialog(Teacher selectedTeacher) {
        Dialog<TeacherUpdateData> dialog = new Dialog<>();
        dialog.setTitle("Update Teacher Details");
        dialog.setHeaderText("Update teacher information for: " + selectedTeacher.getFullName());

        ButtonType updateButtonType = new ButtonType("Update", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(updateButtonType, ButtonType.CANCEL);

        // Get the form components map instead of just the grid
        Map<String, Object> formComponents = createUpdateFormGrid(selectedTeacher);
        GridPane grid = (GridPane) formComponents.get("grid");
        dialog.getDialogPane().setContent(grid);

        Node updateButtonNode = dialog.getDialogPane().lookupButton(updateButtonType);

        // Pass the components map to the validation method
        setupUpdateFormValidation(formComponents, updateButtonNode, selectedTeacher);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == updateButtonType) {
                // Pass the components map to the extract method
                return extractUpdateData(formComponents, selectedTeacher);
            }
            return null;
        });

        return dialog;
    }

    private Map<String, Object> createUpdateFormGrid(Teacher selectedTeacher) {
        Map<String, Object> components = new HashMap<>();
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField fullNameField = new TextField(selectedTeacher.getFullName());
        TextField emailField = new TextField(selectedTeacher.getEmail());
        TextField phoneField = new TextField(selectedTeacher.getPhone());
        phoneField.setPromptText(PHONE_FORMAT_HINT);
        ComboBox<String> clubComboBox = new ComboBox<>(clubs);
        clubComboBox.setValue(selectedTeacher.getClubName());

        Label fullNameError = createErrorLabel();
        Label emailError = createErrorLabel();
        Label phoneError = createErrorLabel();
        Label clubError = createErrorLabel();

        Label phoneHint = new Label("Format: +[country code][number]");
        phoneHint.setStyle("-fx-font-size: 10px; -fx-text-fill: gray;");

        // Add components to grid with proper identification
        addFormRow(grid, "Full Name*:", fullNameField, fullNameError, 0);
        addFormRow(grid, "Email*:", emailField, emailError, 1);
        addFormRow(grid, "Phone*:", phoneField, phoneHint, 2);
        grid.add(phoneError, 1, 3);
        addFormRow(grid, "Club*:", clubComboBox, clubError, 4);

        // Grade selection components
        ListView<String> gradesListView = new ListView<>(grades);
        gradesListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        gradesListView.setPrefHeight(120);

        // Clear selection button
        Button clearGradesButton = new Button("Clear Selection");
        clearGradesButton.setOnAction(e -> gradesListView.getSelectionModel().clearSelection());

        // Load currently assigned grades and sort them
        List<String> currentGrades = getTeacherGrades(selectedTeacher.getId());
        Collections.sort(currentGrades);

        for (String grade : currentGrades) {
            if (grades.contains(grade)) {
                gradesListView.getSelectionModel().select(grade);
            }
        }

        Label gradesLabel = new Label("Grades (Optional):");
        Label gradesHint = new Label("Select grades this teacher is responsible for. Leave empty for all grades.");
        gradesHint.setStyle("-fx-font-size: 10px; -fx-text-fill: gray;");

        // Add grade components to grid
        grid.add(gradesLabel, 0, 5);
        grid.add(gradesListView, 1, 5);
        grid.add(clearGradesButton, 2, 5);
        grid.add(gradesHint, 0, 6);
        GridPane.setColumnSpan(gradesHint, 3);

        // Store all components in the map
        components.put("grid", grid);
        components.put("fullNameField", fullNameField);
        components.put("emailField", emailField);
        components.put("phoneField", phoneField);
        components.put("clubComboBox", clubComboBox);
        components.put("fullNameError", fullNameError);
        components.put("emailError", emailError);
        components.put("phoneError", phoneError);
        components.put("clubError", clubError);
        components.put("gradesListView", gradesListView);
        components.put("clearGradesButton", clearGradesButton);

        return components;
    }

    // Method to get teacher's current grades
    private List<String> getTeacherGrades(UUID teacherId) {
        List<String> teacherGrades = new ArrayList<>();
        String sql = """
        SELECT g.grade_name 
        FROM teacher_grades tg
        JOIN grades g ON tg.grade_id = g.grade_id
        WHERE tg.teacher_id = ? AND tg.school_id = ?
        ORDER BY g.grade_name
    """;

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, teacherId);
            stmt.setObject(2, SessionManager.getCurrentSchoolId());
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                teacherGrades.add(rs.getString("grade_name"));
            }
        } catch (SQLException e) {
            // Log error
        }
        return teacherGrades;
    }

    private void setupUpdateFormValidation(Map<String, Object> components, Node updateButtonNode, Teacher selectedTeacher) {
        // Extract components from the map
        TextField fullNameField = (TextField) components.get("fullNameField");
        TextField emailField = (TextField) components.get("emailField");
        TextField phoneField = (TextField) components.get("phoneField");
        ComboBox<String> clubComboBox = (ComboBox<String>) components.get("clubComboBox");
        Label fullNameError = (Label) components.get("fullNameError");
        Label emailError = (Label) components.get("emailError");
        Label phoneError = (Label) components.get("phoneError");
        Label clubError = (Label) components.get("clubError");

        updateButtonNode.setDisable(true);

        ChangeListener<String> validationListener = (observable, oldValue, newValue) -> {
            boolean isValid = validateUpdateForm(
                    selectedTeacher,
                    fullNameField.getText().trim(),
                    emailField.getText().trim(),
                    phoneField.getText().trim(),
                    clubComboBox.getValue(),
                    fullNameError,
                    emailError,
                    phoneError,
                    clubError
            );
            updateButtonNode.setDisable(!isValid);
        };

        fullNameField.textProperty().addListener(validationListener);
        emailField.textProperty().addListener(validationListener);
        phoneField.textProperty().addListener(validationListener);
        clubComboBox.valueProperty().addListener((obs, oldVal, newVal) -> validationListener.changed(null, null, null));

        validationListener.changed(null, null, null);
    }

    private TeacherUpdateData extractUpdateData(Map<String, Object> components, Teacher selectedTeacher) {
        // Extract components from the map
        TextField fullNameField = (TextField) components.get("fullNameField");
        TextField emailField = (TextField) components.get("emailField");
        TextField phoneField = (TextField) components.get("phoneField");
        ComboBox<String> clubComboBox = (ComboBox<String>) components.get("clubComboBox");

        // Extract selected grades and sort them alphabetically
        ListView<String> gradesListView = (ListView<String>) components.get("gradesListView");
        List<UUID> selectedGradeIds = gradesListView.getSelectionModel().getSelectedItems().stream()
                .sorted()
                .map(gradeName -> gradeNameToIdMap.get(gradeName))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        UUID clubId = getClubIdByName(clubComboBox.getValue());
        return new TeacherUpdateData(
                selectedTeacher.getId(),
                fullNameField.getText().trim(),
                emailField.getText().trim(),
                phoneField.getText().trim(),
                clubId,
                selectedGradeIds
        );
    }

    // Method to update grade assignments
    private void updateGradeAssignments(Connection conn, TeacherUpdateData updateData) throws SQLException {
        // Remove existing grade assignments
        String deleteSql = "DELETE FROM teacher_grades WHERE teacher_id = ? AND school_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
            stmt.setObject(1, updateData.teacherId());
            stmt.setObject(2, SessionManager.getCurrentSchoolId());
            stmt.executeUpdate();
        }

        // Add new grade assignments if any are selected
        if (updateData.gradeIds() != null && !updateData.gradeIds().isEmpty()) {
            assignGradesToTeacher(conn, updateData.teacherId(), updateData.gradeIds());
        }
    }

    private boolean updateTeacher(TeacherUpdateData updateData) {
        Connection conn = null;
        try {
            conn = DatabaseConnector.getConnection();
            conn.setAutoCommit(false);

            // Update user details
            String userSql = """
        UPDATE users 
        SET full_name = ?, email = ?, phone = ?, updated_at = CURRENT_TIMESTAMP 
        WHERE user_id = ?
        """;

            try (PreparedStatement stmt = conn.prepareStatement(userSql)) {
                stmt.setString(1, updateData.fullName());
                stmt.setString(2, updateData.email().toLowerCase());
                stmt.setString(3, updateData.phone());
                stmt.setObject(4, updateData.teacherId());

                if (stmt.executeUpdate() == 0) {
                    conn.rollback();
                    return false;
                }
            }

            // Update club assignment
            updateClubAssignment(conn, updateData);

            // Update grade assignments
            updateGradeAssignments(conn, updateData);

            conn.commit();
            return true;

        } catch (SQLException e) {
            rollbackTransaction(conn);
            handleDatabaseError(e);
            return false;
        } finally {
            closeConnection(conn);
        }
    }

    private void updateClubAssignment(Connection conn, TeacherUpdateData updateData) throws SQLException {
        String deleteClubSql = "DELETE FROM club_teachers WHERE teacher_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(deleteClubSql)) {
            stmt.setObject(1, updateData.teacherId());
            stmt.executeUpdate();
        }

        if (updateData.clubId() != null) {
            if (!assignTeacherToClub(conn, updateData.teacherId(), updateData.clubId())) {
                throw new SQLException("Failed to assign teacher to club");
            }
        }
    }

    // ===== VALIDATION METHODS =====

    private boolean validateCreateForm(String fullName, String email, String username, String phone, String club,
                                       Label fullNameError, Label emailError, Label usernameError,
                                       Label phoneError, Label clubError) {

        boolean isValid = true;

        isValid &= validateFullName(fullName, fullNameError);
        isValid &= validateEmail(email, emailError);
        isValid &= validateUsername(username, usernameError);
        isValid &= validatePhone(phone, phoneError);
        isValid &= validateClub(club, clubError);

        return isValid;
    }

    private boolean validateUpdateForm(Teacher selectedTeacher, String fullName, String email,
                                       String phone, String club, Label fullNameError,
                                       Label emailError, Label phoneError, Label clubError) {

        boolean isValid = true;

        isValid &= validateFullName(fullName, fullNameError);
        isValid &= validateEmailForUpdate(email, selectedTeacher, emailError);
        isValid &= validatePhone(phone, phoneError);
        isValid &= validateClub(club, clubError);

        return isValid;
    }

    private boolean validateFullName(String fullName, Label errorLabel) {
        if (fullName.isEmpty()) {
            errorLabel.setText("Full name is required");
            return false;
        } else if (fullName.length() < 2) {
            errorLabel.setText("Full name must be at least 2 characters");
            return false;
        } else {
            errorLabel.setText("");
            return true;
        }
    }

    private boolean validateEmail(String email, Label errorLabel) {
        if (email.isEmpty()) {
            errorLabel.setText("Email is required");
            return false;
        } else if (!EMAIL_PATTERN.matcher(email).matches()) {
            errorLabel.setText("Invalid email format");
            return false;
        } else if (!isEmailAvailable(email)) {
            errorLabel.setText("Email already exists");
            return false;
        } else {
            errorLabel.setText("");
            return true;
        }
    }

    private boolean validateEmailForUpdate(String email, Teacher selectedTeacher, Label errorLabel) {
        if (email.isEmpty()) {
            errorLabel.setText("Email is required");
            return false;
        } else if (!EMAIL_PATTERN.matcher(email).matches()) {
            errorLabel.setText("Invalid email format");
            return false;
        } else if (!email.equalsIgnoreCase(selectedTeacher.getEmail()) &&
                !isEmailAvailableForUpdate(email, selectedTeacher.getId())) {
            errorLabel.setText("Email already exists");
            return false;
        } else {
            errorLabel.setText("");
            return true;
        }
    }

    private boolean validateUsername(String username, Label errorLabel) {
        if (username.isEmpty()) {
            errorLabel.setText("Username is required");
            return false;
        } else if (username.length() < 3) {
            errorLabel.setText("Username must be at least 3 characters");
            return false;
        } else if (!isUsernameAvailable(username)) {
            errorLabel.setText("Username already exists");
            return false;
        } else {
            errorLabel.setText("");
            return true;
        }
    }

    private boolean validatePhone(String phone, Label errorLabel) {
        if (phone.isEmpty()) {
            errorLabel.setText("Phone is required");
            return false;
        } else {
            String validationError = validatePhoneNumber(phone);
            if (validationError != null) {
                errorLabel.setText(validationError);
                return false;
            } else {
                errorLabel.setText("");
                return true;
            }
        }
    }

    private boolean validateClub(String club, Label errorLabel) {
        if (club == null || club.isEmpty()) {
            errorLabel.setText("Club selection is required");
            return false;
        } else if (!validateClubSelection(club)) {
            errorLabel.setText("Selected club is not available");
            return false;
        } else {
            errorLabel.setText("");
            return true;
        }
    }

    private String validatePhoneNumber(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return "Phone number is required";
        }

        try {
            PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
            String cleanedPhone = phone.trim();

            if (!cleanedPhone.startsWith("+")) {
                cleanedPhone = "+" + cleanedPhone;
            }

            Phonenumber.PhoneNumber phoneNumber = phoneUtil.parse(cleanedPhone, null);
            String regionCode = phoneUtil.getRegionCodeForNumber(phoneNumber);

            if ("ZZ".equals(regionCode)) {
                if (!phoneUtil.isValidNumber(phoneNumber)) {
                    return "Invalid phone number format";
                }
            } else {
                if (!phoneUtil.isValidNumberForRegion(phoneNumber, regionCode)) {
                    return "Invalid phone number for region: " + regionCode;
                }
            }

            return null;

        } catch (NumberParseException e) {
            switch (e.getErrorType()) {
                case INVALID_COUNTRY_CODE: return "Invalid country code";
                case NOT_A_NUMBER: return "Not a valid phone number";
                case TOO_SHORT_NSN: return "Phone number is too short";
                case TOO_LONG: return "Phone number is too long";
                default: return "Invalid phone number format";
            }
        }
    }

    // ===== UTILITY METHODS =====

    private Label createErrorLabel() {
        Label errorLabel = new Label();
        errorLabel.setTextFill(Color.RED);
        errorLabel.setStyle("-fx-font-size: 10px;");
        return errorLabel;
    }

    private boolean validateClubSelection(String clubName) {
        return clubName != null && !clubName.trim().isEmpty() && getClubIdByName(clubName) != null;
    }

    private boolean assignTeacherToClub(Connection conn, UUID teacherId, UUID clubId) throws SQLException {
        String sql = "INSERT INTO club_teachers (teacher_id, club_id, school_id) VALUES (?, ?, ?)";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, teacherId);
            stmt.setObject(2, clubId);
            stmt.setObject(3, SessionManager.getCurrentSchoolId());
            return stmt.executeUpdate() > 0;
        }
    }

    private UUID getClubIdByName(String clubName) {
        String sql = "SELECT club_id FROM clubs WHERE club_name = ? AND school_id = ?";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, clubName);
            stmt.setObject(2, SessionManager.getCurrentSchoolId());
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return (UUID) rs.getObject("club_id");
            }
        } catch (SQLException e) {
            // Log error if needed
        }
        return null;
    }

    private boolean isEmailAvailableForUpdate(String email, UUID excludeTeacherId) {
        UUID schoolId = SessionManager.getCurrentSchoolId();
        if (schoolId == null) {
            showAlert(Alert.AlertType.ERROR, "Error", "No school session found");
            return false;
        }

        String sql = "SELECT COUNT(*) FROM users WHERE email = ? AND school_id = ? AND user_id != ?";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, email.toLowerCase());
            stmt.setObject(2, schoolId);
            stmt.setObject(3, excludeTeacherId);

            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getInt(1) == 0;
        } catch (SQLException e) {
            return false;
        }
    }

    private boolean isUsernameAvailable(String username) {
        UUID schoolId = SessionManager.getCurrentSchoolId();
        if (schoolId == null) {
            showAlert(Alert.AlertType.ERROR, "Error", "No school session found");
            return false;
        }

        String sql = "SELECT COUNT(*) FROM users WHERE username = ? AND school_id = ?";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username.toLowerCase());
            stmt.setObject(2, schoolId);

            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getInt(1) == 0;
        } catch (SQLException e) {
            return false;
        }
    }

    private boolean isEmailAvailable(String email) {
        UUID schoolId = SessionManager.getCurrentSchoolId();
        if (schoolId == null) {
            showAlert(Alert.AlertType.ERROR, "Error", "No school session found");
            return false;
        }

        String sql = "SELECT COUNT(*) FROM users WHERE email = ? AND school_id = ?";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, email.toLowerCase());
            stmt.setObject(2, schoolId);

            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getInt(1) == 0;
        } catch (SQLException e) {
            return false;
        }
    }

    private String generateTemporaryPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        SecureRandom random = new SecureRandom();
        StringBuilder password = new StringBuilder();

        for (int i = 0; i < 8; i++) {
            password.append(chars.charAt(random.nextInt(chars.length())));
        }

        return password.toString();
    }

    private void handleDatabaseError(SQLException e) {
        String errorMessage;
        String sqlMessage = e.getMessage();

        if (sqlMessage.contains("Username already exists")) {
            errorMessage = "Username already exists. Please choose a different username.";
        } else if (sqlMessage.contains("Email already exists")) {
            errorMessage = "Email already exists. Please use a different email address.";
        } else if (sqlMessage.contains("Phone number already exists")) {
            errorMessage = "Phone number already exists. Please use a different phone number.";
        } else if (sqlMessage.contains("Permission denied")) {
            errorMessage = "Permission denied: Only active coordinators can create teachers.";
        } else if (sqlMessage.contains("Club not found")) {
            errorMessage = "Selected club not found or is not active.";
        } else if (sqlMessage.contains("unique_lower_username")) {
            errorMessage = "Username already exists. Please choose a different username.";
        } else if (sqlMessage.contains("users_email_lower_idx")) {
            errorMessage = "Email already exists. Please use a different email address.";
        } else if (sqlMessage.contains("unique_phone")) {
            errorMessage = "Phone number already exists. Please use a different phone number.";
        } else {
            errorMessage = "Failed to create teacher: " + e.getMessage();
        }

        showAlert(Alert.AlertType.ERROR, "Error", errorMessage);
    }

    private void rollbackTransaction(Connection conn) {
        try {
            if (conn != null) {
                conn.rollback();
            }
        } catch (SQLException rollbackEx) {
            // Log rollback error
        }
    }

    private void closeConnection(Connection conn) {
        try {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        } catch (SQLException e) {
            // Log error
        }
    }

    // ===== ACTION HANDLERS =====

    @FXML
    private void handleActivateTeacher() {
        if (!SessionManager.isActiveCoordinator()) {
            showAlert(Alert.AlertType.ERROR, "Permission Denied",
                    "Only active coordinators can activate teachers.");
            return;
        }

        Teacher selected = teachersTable.getSelectionModel().getSelectedItem();
        if (selected != null && updateTeacherStatus(selected.getId(), true)) {
            showAlert(Alert.AlertType.INFORMATION, "Success", "Teacher activated successfully");
            loadTeachers();
        } else {
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to activate teacher");
        }
    }

    @FXML
    private void handleDeactivateTeacher() {
        if (!SessionManager.isActiveCoordinator()) {
            showAlert(Alert.AlertType.ERROR, "Permission Denied",
                    "Only active coordinators can deactivate teachers.");
            return;
        }

        Teacher selected = teachersTable.getSelectionModel().getSelectedItem();
        if (selected != null && updateTeacherStatus(selected.getId(), false)) {
            showAlert(Alert.AlertType.INFORMATION, "Success", "Teacher deactivated successfully");
            loadTeachers();
        } else {
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to deactivate teacher");
        }
    }

    @FXML
    private void handleRefresh() {
        // Check if user has coordinator access
        if (!SessionManager.isCoordinator()) {
            showAlert(Alert.AlertType.ERROR, "Access Denied",
                    "Coordinator access required to refresh data.");
            return;
        }
        loadData();
    }

    private boolean updateTeacherStatus(UUID userId, boolean makeActive) {
        String sql = "UPDATE users SET is_active = ? WHERE user_id = ?";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setBoolean(1, makeActive);
            stmt.setObject(2, userId);
            return stmt.executeUpdate() > 0;

        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to update teacher: " + e.getMessage());
            return false;
        }
    }

    private void showAlert(Alert.AlertType alertType, String title, String message) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // ===== DATA CLASSES =====

    private record TeacherCreationData(
            String fullName,
            String email,
            String username,
            String phone,
            UUID clubId,
            List<UUID> gradeIds
    ) {}

    private record TeacherUpdateData(
            UUID teacherId,
            String fullName,
            String email,
            String phone,
            UUID clubId,
            List<UUID> gradeIds
    ) {}

    // Teacher model class
    public static class Teacher {
        private final UUID id;
        private final String username;
        private final String fullName;
        private final String email;
        private final String phone;
        private final boolean isActive;
        private final String clubName;
        private final Timestamp createdAt;
        private final UUID clubId;
        private final List<String> assignedGrades;

        public Teacher(UUID id, String username, String fullName, String email, String phone,
                       boolean isActive, String clubName, Timestamp createdAt) {
            this(id, username, fullName, email, phone, isActive, clubName, createdAt, null);
        }

        public Teacher(UUID id, String username, String fullName, String email, String phone,
                       boolean isActive, String clubName, Timestamp createdAt, UUID clubId) {
            this.id = id;
            this.username = username;
            this.fullName = fullName;
            this.email = email;
            this.phone = phone;
            this.isActive = isActive;
            this.clubName = clubName;
            this.createdAt = createdAt;
            this.clubId = clubId;
            this.assignedGrades = new ArrayList<>();
        }

        // Method to get assigned grades
        public List<String> getAssignedGrades() {
            return assignedGrades;
        }

        // Method to get grades as formatted string
        public String getGradesFormatted() {
            if (assignedGrades.isEmpty()) {
                return "All Grades";
            }
            // Sort the grades alphabetically before displaying
            List<String> sortedGrades = new ArrayList<>(assignedGrades);
            Collections.sort(sortedGrades);
            return String.join(", ", sortedGrades);
        }

        public UUID getClubId() { return clubId; }
        public UUID getId() { return id; }
        public String getUsername() { return username; }
        public String getFullName() { return fullName; }
        public String getEmail() { return email; }
        public String getPhone() { return phone; }
        public boolean isActive() { return isActive; }
        public String getStatus() { return isActive ? "Active" : "Inactive"; }
        public String getClubName() { return clubName != null ? clubName : "Not assigned"; }
        public Timestamp getCreatedAt() { return createdAt; }
        public String getCreatedAtFormatted() {
            if (createdAt == null) return "N/A";
            return new SimpleDateFormat("MMM dd, yyyy").format(createdAt);
        }
    }

    private boolean validateGrades(List<UUID> gradeIds) {
        if (gradeIds == null || gradeIds.isEmpty()) {
            return true; // No grades selected is valid (means all grades)
        }

        String sql = "SELECT COUNT(*) FROM grades WHERE grade_id = ? AND school_id = ?";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (UUID gradeId : gradeIds) {
                stmt.setObject(1, gradeId);
                stmt.setObject(2, SessionManager.getCurrentSchoolId());
                ResultSet rs = stmt.executeQuery();

                if (rs.next() && rs.getInt(1) == 0) {
                    return false; // Grade not found
                }
            }
            return true;

        } catch (SQLException e) {
            return false;
        }
    }
}