package com.cms.clubmanagementsystem.service;

import com.cms.clubmanagementsystem.controller.EnrollmentController.LearnerInfo;
import com.cms.clubmanagementsystem.model.ImportResult;
import com.cms.clubmanagementsystem.model.LearnerImportDTO;
import com.cms.clubmanagementsystem.utils.DatabaseConnector;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.sql.Date;

public class LearnerService {
    private UUID currentSchoolId;

    // Remove connection field from class level since we'll get it when needed
    public LearnerService(UUID schoolId) {
        this.currentSchoolId = schoolId;
    }

    public List<LearnerInfo> getActiveLearners(Connection conn, UUID schoolId) throws SQLException {
        List<LearnerInfo> learners = new ArrayList<>();
        String sql = "SELECT l.learner_id, l.admission_number, " +
                "l.full_name, " +  // Changed from concatenation since we have full_name column
                "g.grade_name " +
                "FROM learners l " +
                "JOIN grades g ON l.grade_id = g.grade_id " +
                "WHERE l.school_id = ? AND l.is_active = true " +
                "ORDER BY l.full_name";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, schoolId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID learnerId = (UUID) rs.getObject("learner_id");
                    String admissionNumber = rs.getString("admission_number");
                    String fullName = rs.getString("full_name");
                    String gradeName = rs.getString("grade_name");

                    learners.add(new LearnerInfo(learnerId, admissionNumber, fullName, gradeName));
                }
            }
        }
        return learners;
    }

    public List<LearnerInfo> searchLearners(Connection conn, UUID schoolId, String searchTerm) throws SQLException {
        List<LearnerInfo> learners = new ArrayList<>();
        String sql = "SELECT l.learner_id, l.admission_number, " +
                "l.full_name, " +  // Changed from concatenation
                "g.grade_name " +
                "FROM learners l " +
                "JOIN grades g ON l.grade_id = g.grade_id " +
                "WHERE l.school_id = ? AND l.is_active = true " +
                "AND (l.full_name ILIKE ? OR l.admission_number ILIKE ?) " +  // Simplified search
                "ORDER BY l.full_name";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, schoolId);
            String likeTerm = "%" + searchTerm + "%";
            stmt.setString(2, likeTerm);
            stmt.setString(3, likeTerm);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID learnerId = (UUID) rs.getObject("learner_id");
                    String admissionNumber = rs.getString("admission_number");
                    String fullName = rs.getString("full_name");
                    String gradeName = rs.getString("grade_name");

                    learners.add(new LearnerInfo(learnerId, admissionNumber, fullName, gradeName));
                }
            }
        }
        return learners;
    }

    public ImportResult importLearners(List<LearnerImportDTO> learners) {
        ImportResult result = new ImportResult();
        result.setTotalRecords(learners.size());

        try (Connection connection = DatabaseConnector.getConnection()) {

            // FIXED SQL - Added gender column and parameter
            String sql = "INSERT INTO learners (admission_number, full_name, grade_id, " +
                    "school_id, date_joined_school, gender, created_at) " + // ← GENDER ADDED!
                    "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP) " + // ← ADDED GENDER PARAMETER!
                    "ON CONFLICT (admission_number, school_id) DO UPDATE SET " +
                    "full_name = EXCLUDED.full_name, grade_id = EXCLUDED.grade_id, " +
                    "date_joined_school = EXCLUDED.date_joined_school, " +
                    "gender = EXCLUDED.gender, " + // ← ADD GENDER TO UPDATE TOO!
                    "is_active = TRUE";

            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                connection.setAutoCommit(false);

                for (LearnerImportDTO learner : learners) {
                    if (learner.isValid()) {
                        try {
                            pstmt.setString(1, learner.getAdmissionNumber());
                            pstmt.setString(2, learner.getFullName());
                            pstmt.setObject(3, learner.getGradeId());
                            pstmt.setObject(4, currentSchoolId);
                            pstmt.setDate(5, Date.valueOf(learner.getDateJoined()));

                            // ADD THIS CRITICAL LINE - Handle gender parameter:
                            String gender = learner.getGender();
                            if (gender == null || gender.trim().isEmpty()) {
                                pstmt.setString(6, "Not specified"); // Default value
                            } else {
                                pstmt.setString(6, gender); // Use the parsed gender
                            }

                            pstmt.addBatch();
                            result.setSuccessfulImports(result.getSuccessfulImports() + 1);
                        } catch (Exception e) {
                            learner.setValid(false);
                            learner.setErrorMessage("Database error: " + e.getMessage());
                            result.getFailedRecords().add(learner);
                        }
                    } else {
                        result.getFailedRecords().add(learner);
                    }
                }

                pstmt.executeBatch();
                connection.commit();
                result.setFailedImports(result.getFailedRecords().size());

            } catch (SQLException e) {
                try { connection.rollback(); } catch (SQLException ex) {}
                throw new RuntimeException("Import failed: " + e.getMessage(), e);
            } finally {
                try { connection.setAutoCommit(true); } catch (SQLException e) {}
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to get database connection: " + e.getMessage(), e);
        }

        return result;
    }
}