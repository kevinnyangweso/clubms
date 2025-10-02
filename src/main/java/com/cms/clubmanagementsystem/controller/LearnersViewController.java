package com.cms.clubmanagementsystem.controller;

import com.cms.clubmanagementsystem.utils.DatabaseConnector;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ResourceBundle;
import java.util.UUID;

public class LearnersViewController implements Initializable {

    @FXML private TableView<Learner> learnersTable;
    @FXML private TableColumn<Learner, String> fullNameColumn;
    @FXML private TableColumn<Learner, String> admissionNumberColumn;
    @FXML private TableColumn<Learner, String> gradeColumn;
    @FXML private Label titleLabel;
    @FXML private Label summaryLabel;

    private UUID clubId;
    private String clubName;
    private int selectedYear = 2025; // Default to current year
    private int selectedTerm = 3;    // Default to current term
    private ObservableList<Learner> learnersList = FXCollections.observableArrayList();
    private boolean dataLoaded = false;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        System.out.println("DEBUG: LearnersViewController initialized");

        // Set up the table columns
        fullNameColumn.setCellValueFactory(new PropertyValueFactory<>("fullName"));
        admissionNumberColumn.setCellValueFactory(new PropertyValueFactory<>("admissionNumber"));
        gradeColumn.setCellValueFactory(new PropertyValueFactory<>("grade"));

        learnersTable.setItems(learnersList);

        // Don't load data here - wait for all parameters to be set
        System.out.println("DEBUG: Table setup complete, waiting for parameters...");
    }

    public void setClubId(UUID clubId) {
        this.clubId = clubId;
        System.out.println("DEBUG: setClubId called with: " + clubId);
        tryLoadData();
    }

    public void setClubName(String clubName) {
        this.clubName = clubName;
        System.out.println("DEBUG: setClubName called with: " + clubName);
        tryLoadData();
    }

    public void setYearAndTerm(int year, int term) {
        this.selectedYear = year;
        this.selectedTerm = term;
        System.out.println("DEBUG: setYearAndTerm called with - Year: " + year + ", Term: " + term);
        tryLoadData();
    }

    private void tryLoadData() {
        // Only load data if all required parameters are set and data hasn't been loaded yet
        if (clubId != null && clubName != null && selectedYear > 0 && selectedTerm > 0 && !dataLoaded) {
            System.out.println("DEBUG: All parameters ready, loading data...");
            dataLoaded = true;

            // Load data after a short delay to ensure UI is ready
            javafx.application.Platform.runLater(() -> {
                loadLearnersData();
            });
        }
    }

    private void loadLearnersData() {
        System.out.println("DEBUG: loadLearnersData started");
        learnersList.clear();

        if (clubId == null) {
            System.out.println("DEBUG: clubId is null - cannot load learners");
            updateDisplayInfo(0, "No club selected");
            return;
        }

        try (Connection conn = DatabaseConnector.getConnection()) {
            String sql = """
                SELECT l.full_name, l.admission_number, g.grade_name
                FROM club_enrollments ce
                JOIN learners l ON ce.learner_id = l.learner_id
                JOIN grades g ON l.grade_id = g.grade_id
                WHERE ce.club_id = ? AND ce.is_active = true
                AND ce.academic_year = ?
                AND ce.term_number = ?
                ORDER BY g.grade_name, l.full_name
            """;

            System.out.println("DEBUG: Executing SQL with clubId: " + clubId +
                    ", year: " + selectedYear + ", term: " + selectedTerm);

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, clubId);
                ps.setInt(2, selectedYear);
                ps.setInt(3, selectedTerm);

                ResultSet rs = ps.executeQuery();

                int count = 0;
                while (rs.next()) {
                    String fullName = rs.getString("full_name");
                    String admissionNumber = rs.getString("admission_number");
                    String gradeName = rs.getString("grade_name");

                    System.out.println("DEBUG: Adding learner - " + fullName +
                            " (" + admissionNumber + ") - " + gradeName);

                    learnersList.add(new Learner(fullName, admissionNumber, gradeName));
                    count++;
                }

                System.out.println("DEBUG: Total learners added to table: " + count);
                updateDisplayInfo(count);

                if (count == 0) {
                    System.out.println("DEBUG: No learners found in database for the given criteria");
                    showInfo("No learners found for " + clubName +
                            " in Academic Year " + selectedYear + ", Term " + selectedTerm);
                }
            }
        } catch (Exception e) {
            System.out.println("DEBUG: Error loading learners: " + e.getMessage());
            e.printStackTrace();
            showError("Error loading learners data: " + e.getMessage());
        }
    }

    private void updateDisplayInfo(int learnerCount) {
        String title = "Learners in " + (clubName != null ? clubName : "Club");
        String summary = String.format("Showing %d learners for Academic Year %d, Term %d",
                learnerCount, selectedYear, selectedTerm);

        if (titleLabel != null) {
            titleLabel.setText(title);
        } else {
            System.out.println("DEBUG: titleLabel is null");
        }

        if (summaryLabel != null) {
            summaryLabel.setText(summary);
        } else {
            System.out.println("DEBUG: summaryLabel is null");
        }

        // Update window title
        if (learnersTable != null && learnersTable.getScene() != null) {
            Stage stage = (Stage) learnersTable.getScene().getWindow();
            stage.setTitle(String.format("%s - Year %d Term %d", title, selectedYear, selectedTerm));
        }

        System.out.println("DEBUG: Display updated - " + summary);
    }

    private void updateDisplayInfo(int learnerCount, String message) {
        if (titleLabel != null) {
            titleLabel.setText("Learners in " + (clubName != null ? clubName : "Club"));
        }

        if (summaryLabel != null) {
            summaryLabel.setText(message);
        }
    }

    @FXML
    private void refreshData() {
        System.out.println("DEBUG: Refresh button clicked");
        dataLoaded = false; // Reset flag to allow reloading
        tryLoadData();
        showInfo("Learners list refreshed successfully!");
    }

    @FXML
    private void close() {
        Stage stage = (Stage) learnersTable.getScene().getWindow();
        stage.close();
    }

    private void showError(String message) {
        javafx.application.Platform.runLater(() -> {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private void showInfo(String message) {
        javafx.application.Platform.runLater(() -> {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
            alert.setTitle("Information");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    // Learner class
    public static class Learner {
        private final String fullName;
        private final String admissionNumber;
        private final String grade;

        public Learner(String fullName, String admissionNumber, String grade) {
            this.fullName = fullName;
            this.admissionNumber = admissionNumber;
            this.grade = grade;
        }

        public String getFullName() { return fullName; }
        public String getAdmissionNumber() { return admissionNumber; }
        public String getGrade() { return grade; }
    }
}