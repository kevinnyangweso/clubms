package com.cms.clubmanagementsystem.service;

import com.cms.clubmanagementsystem.utils.DatabaseConnector;
import com.cms.clubmanagementsystem.utils.SessionManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnrollmentService {
    private static final Logger logger = LoggerFactory.getLogger(EnrollmentService.class);

    public static class Enrollment {
        private UUID enrollmentId;
        private UUID learnerId;
        private UUID clubId;
        private int termNumber;
        private int academicYear;
        private Timestamp enrollmentDate;
        private Timestamp updatedAt;
        private boolean isActive;
        private String learnerName;
        private String clubName;
        private String admissionNumber;
        private String gender;
        private String gradeName;

        // Constructors
        public Enrollment() {}

        public Enrollment(UUID enrollmentId, UUID learnerId, UUID clubId,
                          int termNumber, int academicYear, Timestamp enrollmentDate,
                          Timestamp updatedAt, boolean isActive) {
            this.enrollmentId = enrollmentId;
            this.learnerId = learnerId;
            this.clubId = clubId;
            this.termNumber = termNumber;
            this.academicYear = academicYear;
            this.enrollmentDate = enrollmentDate;
            this.updatedAt = updatedAt;
            this.isActive = isActive;
        }

        // Getters and Setters
        public Timestamp getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

        public UUID getEnrollmentId() { return enrollmentId; }
        public void setEnrollmentId(UUID enrollmentId) { this.enrollmentId = enrollmentId; }

        public UUID getLearnerId() { return learnerId; }
        public void setLearnerId(UUID learnerId) { this.learnerId = learnerId; }

        public UUID getClubId() { return clubId; }
        public void setClubId(UUID clubId) { this.clubId = clubId; }

        public int getTermNumber() { return termNumber; }
        public void setTermNumber(int termNumber) { this.termNumber = termNumber; }

        public int getAcademicYear() { return academicYear; }
        public void setAcademicYear(int academicYear) { this.academicYear = academicYear; }

        public Timestamp getEnrollmentDate() { return enrollmentDate; }
        public void setEnrollmentDate(Timestamp enrollmentDate) { this.enrollmentDate = enrollmentDate; }

        public boolean isActive() { return isActive; }
        public void setActive(boolean active) { isActive = active; }

        public String getLearnerName() { return learnerName != null ? learnerName : "N/A"; }
        public void setLearnerName(String learnerName) { this.learnerName = learnerName; }

        public String getClubName() { return clubName != null ? clubName : "N/A"; }
        public void setClubName(String clubName) { this.clubName = clubName; }

        public String getAdmissionNumber() { return admissionNumber != null ? admissionNumber : "N/A"; }
        public void setAdmissionNumber(String admissionNumber) { this.admissionNumber = admissionNumber; }

        public String getGender() { return gender != null ? gender : "Not specified"; }
        public void setGender(String gender) { this.gender = gender; }

        public String getGradeName() { return gradeName != null ? gradeName : "N/A"; }
        public void setGradeName(String gradeName) { this.gradeName = gradeName; }

        public String getStatus() { return isActive ? "Active" : "Withdrawn"; }
        public boolean canBeWithdrawn() { return isActive; }

        @Override
        public String toString() {
            return String.format("Enrollment[%s, %s, %s, Term %d/%d, Active: %s, Updated: %s]",
                    learnerName, admissionNumber, clubName, termNumber, academicYear, isActive,
                    updatedAt != null ? updatedAt.toLocalDateTime().toString() : "N/A");
        }
    }

    public boolean enrollLearner(Connection conn, UUID learnerId, UUID clubId,
                                 int termNumber, int academicYear, UUID coordinatorId) throws SQLException {
        logger.info("Attempting to enroll learner {} in club {} for term {}/{}",
                learnerId, clubId, termNumber, academicYear);

        // First check if there's an existing enrollment (active or withdrawn)
        EnrollmentStatus existingStatus = checkExistingEnrollmentStatus(conn, learnerId, clubId, termNumber, academicYear);

        switch (existingStatus) {
            case ACTIVE:
                String activeErrorMsg = "Learner is already actively enrolled in this club for the selected term and year";
                logger.info(activeErrorMsg);
                throw new SQLException(activeErrorMsg);

            case WITHDRAWN:
                logger.info("Re-enrolling previously withdrawn learner {} in club {} for term {}/{}",
                        learnerId, clubId, termNumber, academicYear);
                return reenrollLearner(conn, learnerId, clubId, termNumber, academicYear, coordinatorId);

            case NONE:
                // Check if learner is enrolled in another club for this term
                if (isLearnerEnrolledInOtherClub(conn, learnerId, clubId, termNumber, academicYear)) {
                    String otherClubMsg = "Learner is already enrolled in another club for this term";
                    logger.warn(otherClubMsg);
                    throw new SQLException(otherClubMsg);
                }
                return createNewEnrollment(conn, learnerId, clubId, termNumber, academicYear, coordinatorId);

            default:
                throw new SQLException("Unexpected enrollment status");
        }
    }

    public enum EnrollmentStatus {
        ACTIVE, WITHDRAWN, NONE
    }

    private EnrollmentStatus checkExistingEnrollmentStatus(Connection conn, UUID learnerId, UUID clubId,
                                                           int termNumber, int academicYear) throws SQLException {
        String sql = "SELECT is_active FROM club_enrollments " +
                "WHERE learner_id = ? AND club_id = ? AND term_number = ? AND academic_year = ? " +
                "AND school_id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, learnerId);
            stmt.setObject(2, clubId);
            stmt.setInt(3, termNumber);
            stmt.setInt(4, academicYear);
            stmt.setObject(5, SessionManager.getCurrentSchoolId());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean("is_active") ? EnrollmentStatus.ACTIVE : EnrollmentStatus.WITHDRAWN;
                }
                return EnrollmentStatus.NONE;
            }
        }
    }

    private boolean reenrollLearner(Connection conn, UUID learnerId, UUID clubId,
                                    int termNumber, int academicYear, UUID coordinatorId) throws SQLException {
        String sql = """
        UPDATE club_enrollments 
        SET is_active = true, 
            coordinator_id = ?,
            enrollment_date = CURRENT_TIMESTAMP,
            updated_at = CURRENT_TIMESTAMP
        WHERE learner_id = ? 
        AND club_id = ?
        AND term_number = ? 
        AND academic_year = ?
        AND school_id = ?
        """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, coordinatorId);
            stmt.setObject(2, learnerId);
            stmt.setObject(3, clubId);
            stmt.setInt(4, termNumber);
            stmt.setInt(5, academicYear);
            stmt.setObject(6, SessionManager.getCurrentSchoolId());

            int affectedRows = stmt.executeUpdate();

            if (affectedRows > 0) {
                logger.info("Successfully re-enrolled learner {} in club {} for term {}/{}",
                        learnerId, clubId, termNumber, academicYear);

                // Update club_learners table
                updateClubLearnersOnReenrollment(conn, learnerId, clubId, coordinatorId);
                return true;
            } else {
                logger.warn("No rows affected when re-enrolling learner {}", learnerId);
                return false;
            }
        }
    }

    private boolean createNewEnrollment(Connection conn, UUID learnerId, UUID clubId,
                                        int termNumber, int academicYear, UUID coordinatorId) throws SQLException {
        String sql = """
        INSERT INTO club_enrollments (enrollment_id, learner_id, club_id, term_number, 
                                     academic_year, coordinator_id, school_id, enrollment_date, updated_at, is_active) 
        VALUES (uuid_generate_v4(), ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, true)
        """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, learnerId);
            stmt.setObject(2, clubId);
            stmt.setInt(3, termNumber);
            stmt.setInt(4, academicYear);
            stmt.setObject(5, coordinatorId);
            stmt.setObject(6, SessionManager.getCurrentSchoolId());

            int affectedRows = stmt.executeUpdate();

            if (affectedRows > 0) {
                logger.info("Successfully enrolled learner {} in club {} for term {}/{}",
                        learnerId, clubId, termNumber, academicYear);
                // Ensure club_learners record is created
                updateClubLearnersOnReenrollment(conn, learnerId, clubId, coordinatorId);
                return true;
            } else {
                logger.warn("No rows affected when enrolling learner {}", learnerId);
                return false;
            }
        } catch (SQLException e) {
            if (e.getMessage().contains("duplicate key value violates unique constraint")) {
                logger.info("Duplicate enrollment attempted for learner {} in club {} term {}/{}",
                        learnerId, clubId, termNumber, academicYear);
                throw new SQLException("Learner is already enrolled in this club for the selected term and academic year");
            }
            throw e;
        }
    }

    private void updateClubLearnersOnReenrollment(Connection conn, UUID learnerId, UUID clubId, UUID coordinatorId) throws SQLException {
        String checkSql = "SELECT COUNT(*) FROM club_learners WHERE learner_id = ? AND club_id = ? AND school_id = ?";

        try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
            checkStmt.setObject(1, learnerId);
            checkStmt.setObject(2, clubId);
            checkStmt.setObject(3, SessionManager.getCurrentSchoolId());

            ResultSet rs = checkStmt.executeQuery();
            if (rs.next() && rs.getInt(1) > 0) {
                // Update existing record
                String updateSql = """
                UPDATE club_learners 
                SET is_active = true, 
                    updated_at = CURRENT_TIMESTAMP, 
                    enrolled_by = ?
                WHERE learner_id = ? 
                AND club_id = ? 
                AND school_id = ?
                """;
                try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                    updateStmt.setObject(1, coordinatorId);
                    updateStmt.setObject(2, learnerId);
                    updateStmt.setObject(3, clubId);
                    updateStmt.setObject(4, SessionManager.getCurrentSchoolId());
                    int affectedRows = updateStmt.executeUpdate();
                    logger.info("Updated {} club_learners records for learner {}", affectedRows, learnerId);
                }
            } else {
                // Insert new record
                String insertSql = """
                INSERT INTO club_learners (club_learner_id, learner_id, club_id, school_id, is_active, updated_at, enrolled_by) 
                VALUES (uuid_generate_v4(), ?, ?, ?, true, CURRENT_TIMESTAMP, ?)
                """;
                try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                    insertStmt.setObject(1, learnerId);
                    insertStmt.setObject(2, clubId);
                    insertStmt.setObject(3, SessionManager.getCurrentSchoolId());
                    insertStmt.setObject(4, coordinatorId);
                    int affectedRows = insertStmt.executeUpdate();
                    logger.info("Inserted {} club_learners record for learner {}", affectedRows, learnerId);
                }
            }
        }
    }

    public List<Enrollment> getEnrollmentsByClub(Connection conn, UUID clubId,
                                                 int termNumber, int academicYear) throws SQLException {
        List<Enrollment> enrollments = new ArrayList<>();

        String sql = """
            SELECT ce.enrollment_id, ce.learner_id, ce.club_id, ce.term_number, 
                   ce.academic_year, ce.enrollment_date, ce.updated_at, ce.is_active, 
                   ce.coordinator_id, l.full_name as learner_name, 
                   l.admission_number, g.grade_name, l.gender, c.club_name
            FROM club_enrollments ce
            JOIN learners l ON ce.learner_id = l.learner_id AND ce.school_id = l.school_id
            JOIN grades g ON l.grade_id = g.grade_id AND l.school_id = g.school_id
            JOIN clubs c ON ce.club_id = c.club_id AND ce.school_id = c.school_id
            WHERE ce.club_id = ? 
            AND ce.term_number = ? 
            AND ce.academic_year = ?
            AND ce.school_id = ?
            AND ce.is_active = true
            ORDER BY l.full_name
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, clubId);
            stmt.setInt(2, termNumber);
            stmt.setInt(3, academicYear);
            stmt.setObject(4, SessionManager.getCurrentSchoolId());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Enrollment enrollment = new Enrollment(
                            (UUID) rs.getObject("enrollment_id"),
                            (UUID) rs.getObject("learner_id"),
                            (UUID) rs.getObject("club_id"),
                            rs.getInt("term_number"),
                            rs.getInt("academic_year"),
                            rs.getTimestamp("enrollment_date"),
                            rs.getTimestamp("updated_at"),
                            rs.getBoolean("is_active")
                    );
                    enrollment.setLearnerName(rs.getString("learner_name"));
                    enrollment.setClubName(rs.getString("club_name"));
                    enrollment.setAdmissionNumber(rs.getString("admission_number"));
                    enrollment.setGender(rs.getString("gender"));
                    enrollment.setGradeName(rs.getString("grade_name"));
                    enrollments.add(enrollment);
                }
            }
        }
        return enrollments;
    }

    public boolean withdrawEnrollment(Connection conn, UUID enrollmentId, UUID teacherId) throws SQLException {
        logger.info("Attempting to withdraw enrollment {} by teacher {}", enrollmentId, teacherId);

        if (!isTeacherAuthorizedForEnrollment(conn, enrollmentId, teacherId)) {
            String errorMsg = "Teacher is not authorized to withdraw this enrollment";
            logger.warn(errorMsg);
            throw new SecurityException(errorMsg);
        }

        if (!isEnrollmentActive(conn, enrollmentId)) {
            String errorMsg = "Enrollment is already withdrawn or does not exist";
            logger.info(errorMsg);
            throw new SQLException(errorMsg);
        }

        try {
            conn.setAutoCommit(false);

            // First, withdraw from club_enrollments
            boolean enrollmentWithdrawn = withdrawFromClubEnrollments(conn, enrollmentId, teacherId);
            logger.info("Withdrawn from club_enrollments: {}", enrollmentWithdrawn);

            if (!enrollmentWithdrawn) {
                logger.error("Failed to withdraw enrollment {} from club_enrollments table", enrollmentId);
                conn.rollback();
                return false;
            }

            // Then update club_learners status (this might not always find matching records)
            boolean learnerClubUpdated = updateClubLearnersStatus(conn, enrollmentId);
            logger.info("Updated club_learners: {}", learnerClubUpdated);

            // Log to audit
            logWithdrawalToAudit(conn, enrollmentId, teacherId);

            conn.commit();
            logger.info("Successfully withdrew enrollment {} by teacher {}", enrollmentId, teacherId);
            return true;

        } catch (SQLException e) {
            conn.rollback();
            logger.error("Transaction error withdrawing enrollment {}: {}", enrollmentId, e.getMessage(), e);
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    public List<Enrollment> getLearnerEnrollmentHistory(UUID learnerId) throws SQLException {
        List<Enrollment> history = new ArrayList<>();
        UUID schoolId = SessionManager.getCurrentSchoolId();
        UUID userId = SessionManager.getCurrentUserId();

        if (schoolId == null || userId == null) {
            logger.error("No tenant context set: school_id={}, user_id={}", schoolId, userId);
            throw new SQLException("No tenant context set. Please ensure a user is logged in.");
        }

        logger.info("Fetching enrollment history for learner_id: {}", learnerId);

        try (Connection conn = DatabaseConnector.getConnection()) {
            // Apply tenant context using your DatabaseConnector utility
            // DatabaseConnector.applyTenantContext(conn); // Uncomment if you have this method

            String sql = """
                SELECT ce.enrollment_id, ce.learner_id, ce.club_id, ce.term_number, ce.academic_year, 
                       ce.enrollment_date, ce.updated_at, ce.is_active, c.club_name, 
                       l.full_name, l.admission_number, l.gender, g.grade_name
                FROM club_enrollments ce
                JOIN clubs c ON ce.club_id = c.club_id AND ce.school_id = c.school_id
                JOIN learners l ON ce.learner_id = l.learner_id AND ce.school_id = l.school_id
                JOIN grades g ON l.grade_id = g.grade_id AND l.school_id = g.school_id
                WHERE ce.learner_id = ?
                AND ce.school_id = ?
                ORDER BY ce.academic_year DESC, ce.term_number DESC, ce.updated_at DESC
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, learnerId);
                stmt.setObject(2, schoolId);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        UUID enrollmentId = rs.getObject("enrollment_id", UUID.class);
                        UUID clubId = rs.getObject("club_id", UUID.class);
                        String clubName = rs.getString("club_name");
                        String learnerName = rs.getString("full_name");
                        String admissionNumber = rs.getString("admission_number");
                        String gender = rs.getString("gender");
                        String gradeName = rs.getString("grade_name");

                        if (enrollmentId == null || clubId == null || clubName == null ||
                                learnerName == null || admissionNumber == null) {
                            logger.warn("Skipping invalid enrollment record for learner_id: {}", learnerId);
                            continue;
                        }

                        Enrollment enrollment = new Enrollment(
                                enrollmentId,
                                rs.getObject("learner_id", UUID.class),
                                clubId,
                                rs.getInt("term_number"),
                                rs.getInt("academic_year"),
                                rs.getTimestamp("enrollment_date"),
                                rs.getTimestamp("updated_at"),
                                rs.getBoolean("is_active")
                        );
                        enrollment.setClubName(clubName);
                        enrollment.setLearnerName(learnerName);
                        enrollment.setAdmissionNumber(admissionNumber);
                        enrollment.setGender(gender);
                        enrollment.setGradeName(gradeName);
                        history.add(enrollment);
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Database error in getLearnerEnrollmentHistory for learner_id: {}: {}",
                    learnerId, e.getMessage(), e);
            throw e;
        }

        return history;
    }

    // ========== PRIVATE HELPER METHODS ==========

    private boolean isLearnerEnrolledInClub(Connection conn, UUID learnerId, UUID clubId,
                                            int termNumber, int academicYear) throws SQLException {
        String sql = "SELECT COUNT(*) FROM club_enrollments " +
                "WHERE learner_id = ? AND club_id = ? AND term_number = ? AND academic_year = ? " +
                "AND is_active = true AND school_id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, learnerId);
            stmt.setObject(2, clubId);
            stmt.setInt(3, termNumber);
            stmt.setInt(4, academicYear);
            stmt.setObject(5, SessionManager.getCurrentSchoolId());

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private boolean isLearnerEnrolledInOtherClub(Connection conn, UUID learnerId, UUID excludeClubId,
                                                 int termNumber, int academicYear) throws SQLException {
        String sql = "SELECT COUNT(*) FROM club_enrollments " +
                "WHERE learner_id = ? AND club_id != ? AND term_number = ? AND academic_year = ? " +
                "AND is_active = true AND school_id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, learnerId);
            stmt.setObject(2, excludeClubId);
            stmt.setInt(3, termNumber);
            stmt.setInt(4, academicYear);
            stmt.setObject(5, SessionManager.getCurrentSchoolId());

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private boolean withdrawFromClubEnrollments(Connection conn, UUID enrollmentId, UUID teacherId) throws SQLException {
        // First verify the enrollment exists and get its details
        String selectSql = """
        SELECT ce.term_number, ce.academic_year, c.club_name, l.full_name 
        FROM club_enrollments ce
        JOIN clubs c ON ce.club_id = c.club_id AND ce.school_id = c.school_id
        JOIN learners l ON ce.learner_id = l.learner_id AND ce.school_id = l.school_id
        WHERE ce.enrollment_id = ? 
        AND ce.school_id = ?
        AND ce.is_active = true
        """;

        Integer termNumber = null;
        Integer academicYear = null;
        String clubName = null;
        String learnerName = null;

        try (PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
            selectStmt.setObject(1, enrollmentId);
            selectStmt.setObject(2, SessionManager.getCurrentSchoolId());

            try (ResultSet rs = selectStmt.executeQuery()) {
                if (rs.next()) {
                    termNumber = rs.getInt("term_number");
                    academicYear = rs.getInt("academic_year");
                    clubName = rs.getString("club_name");
                    learnerName = rs.getString("full_name");
                    logger.info("Withdrawing enrollment - Learner: {}, Club: {}, Term: {}, Year: {}",
                            learnerName, clubName, termNumber, academicYear);
                } else {
                    logger.warn("Enrollment {} not found or already withdrawn", enrollmentId);
                    return false;
                }
            }
        }

        // Perform the withdrawal with updated_at
        String updateSql = """
        UPDATE club_enrollments 
        SET is_active = false,
            coordinator_id = ?,
            updated_at = CURRENT_TIMESTAMP
        WHERE enrollment_id = ? 
        AND term_number = ?
        AND academic_year = ?
        AND school_id = ?
        AND is_active = true
        """;

        try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
            stmt.setObject(1, teacherId);  // Set the teacher who is withdrawing
            stmt.setObject(2, enrollmentId);
            stmt.setInt(3, termNumber);
            stmt.setInt(4, academicYear);
            stmt.setObject(5, SessionManager.getCurrentSchoolId());

            int rowsAffected = stmt.executeUpdate();
            logger.info("Withdrawal update - Rows affected: {}", rowsAffected);

            return rowsAffected > 0;
        }
    }

    private boolean updateClubLearnersStatus(Connection conn, UUID enrollmentId) throws SQLException {
        String sql = """
            UPDATE club_learners cl
            SET is_active = false, updated_at = CURRENT_TIMESTAMP
            FROM club_enrollments ce
            WHERE cl.learner_id = ce.learner_id 
            AND cl.club_id = ce.club_id
            AND cl.school_id = ce.school_id
            AND ce.enrollment_id = ?
            AND cl.is_active = true
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, enrollmentId);
            int affectedRows = stmt.executeUpdate();
            logger.debug("Updated {} club_learners records for enrollment {}", affectedRows, enrollmentId);
            return affectedRows >= 0;
        }
    }

    private boolean isTeacherAuthorizedForEnrollment(Connection conn, UUID enrollmentId, UUID teacherId) throws SQLException {
        String sql = """
            SELECT COUNT(*) 
            FROM club_enrollments ce
            JOIN club_teachers ct ON ce.club_id = ct.club_id AND ce.school_id = ct.school_id
            WHERE ce.enrollment_id = ? 
            AND ct.teacher_id = ?
            AND ce.school_id = ?
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, enrollmentId);
            stmt.setObject(2, teacherId);
            stmt.setObject(3, SessionManager.getCurrentSchoolId());

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private boolean isEnrollmentActive(Connection conn, UUID enrollmentId) throws SQLException {
        String sql = """
        SELECT is_active 
        FROM club_enrollments 
        WHERE enrollment_id = ? 
        AND school_id = ?
        """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, enrollmentId);
            stmt.setObject(2, SessionManager.getCurrentSchoolId());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    boolean isActive = rs.getBoolean("is_active");
                    logger.info("Enrollment {} is active: {}", enrollmentId, isActive);
                    return isActive;
                } else {
                    logger.warn("Enrollment {} not found", enrollmentId);
                    return false;
                }
            }
        }
    }

    private void logWithdrawalToAudit(Connection conn, UUID enrollmentId, UUID teacherId) throws SQLException {
        String enrollmentSql = """
            SELECT ce.learner_id, ce.club_id, l.full_name, c.club_name, ce.updated_at
            FROM club_enrollments ce
            JOIN learners l ON ce.learner_id = l.learner_id AND ce.school_id = l.school_id
            JOIN clubs c ON ce.club_id = c.club_id AND ce.school_id = c.school_id
            WHERE ce.enrollment_id = ?
            AND ce.school_id = ?
            """;

        UUID learnerId = null;
        UUID clubId = null;
        String learnerName = null;
        String clubName = null;
        Timestamp updatedAt = null;

        try (PreparedStatement stmt = conn.prepareStatement(enrollmentSql)) {
            stmt.setObject(1, enrollmentId);
            stmt.setObject(2, SessionManager.getCurrentSchoolId());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    learnerId = (UUID) rs.getObject("learner_id");
                    clubId = (UUID) rs.getObject("club_id");
                    learnerName = rs.getString("full_name");
                    clubName = rs.getString("club_name");
                    updatedAt = rs.getTimestamp("updated_at");
                }
            }
        }

        String auditSql = """
            INSERT INTO audit_logs (
                action_type, table_name, record_id, old_values, new_values,
                performed_by, school_id, performed_at
            ) VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, CURRENT_TIMESTAMP)
            """;

        try (PreparedStatement stmt = conn.prepareStatement(auditSql)) {
            String oldValues = String.format(
                    "{\"enrollment_id\": \"%s\", \"learner_id\": \"%s\", \"club_id\": \"%s\", \"is_active\": true, \"updated_at\": \"%s\"}",
                    enrollmentId, learnerId, clubId, updatedAt != null ? updatedAt.toString() : "null"
            );

            String newValues = String.format(
                    "{\"enrollment_id\": \"%s\", \"learner_id\": \"%s\", \"club_id\": \"%s\", \"is_active\": false, \"updated_at\": \"%s\"}",
                    enrollmentId, learnerId, clubId, new Timestamp(System.currentTimeMillis())
            );

            stmt.setString(1, "WITHDRAW_ENROLLMENT");
            stmt.setString(2, "club_enrollments");
            stmt.setString(3, enrollmentId.toString());
            stmt.setString(4, oldValues);
            stmt.setString(5, newValues);
            stmt.setObject(6, teacherId);
            stmt.setObject(7, SessionManager.getCurrentSchoolId());

            stmt.executeUpdate();
            logger.debug("Audit log created for enrollment withdrawal: {}", enrollmentId);
        }
    }
}