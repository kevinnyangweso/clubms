package com.cms.clubmanagementsystem.service;

import com.cms.clubmanagementsystem.utils.SessionManager;

import java.sql.*;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EnrollmentService {
    private static final Logger LOGGER = Logger.getLogger(EnrollmentService.class.getName());

    public static class Enrollment {
        private UUID enrollmentId;
        private UUID learnerId;
        private UUID clubId;
        private int termNumber;
        private int academicYear;
        private Timestamp enrollmentDate;
        private boolean isActive;
        private String learnerName;
        private String clubName;
        private String admissionNumber;

        // Constructors, getters, and setters
        public Enrollment() {}

        public Enrollment(UUID enrollmentId, UUID learnerId, UUID clubId,
                          int termNumber, int academicYear, Timestamp enrollmentDate,
                          boolean isActive) {
            this.enrollmentId = enrollmentId;
            this.learnerId = learnerId;
            this.clubId = clubId;
            this.termNumber = termNumber;
            this.academicYear = academicYear;
            this.enrollmentDate = enrollmentDate;
            this.isActive = isActive;
        }

        // Getters and Setters
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

        public String getLearnerName() { return learnerName; }
        public void setLearnerName(String learnerName) { this.learnerName = learnerName; }

        public String getClubName() { return clubName; }
        public void setClubName(String clubName) { this.clubName = clubName; }

        public String getAdmissionNumber() { return admissionNumber; }
        public void setAdmissionNumber(String admissionNumber) { this.admissionNumber = admissionNumber; }
    }

    public boolean enrollLearner(Connection conn, UUID learnerId, UUID clubId,
                                 int termNumber, int academicYear, UUID coordinatorId) throws SQLException {

        // Check if learner is already enrolled in this term
        if (isLearnerEnrolledInTerm(conn, learnerId, termNumber, academicYear)) {
            throw new SQLException("Learner is already enrolled in a club for this term");
        }

        // Check club capacity
        if (!hasAvailableCapacity(conn, clubId, termNumber, academicYear)) {
            throw new SQLException("Club has reached maximum capacity for this term");
        }

        String sql = "INSERT INTO club_enrollments (learner_id, club_id, term_number, " +
                "academic_year, coordinator_id, school_id) " +
                "VALUES (?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, learnerId);
            stmt.setObject(2, clubId);
            stmt.setInt(3, termNumber);
            stmt.setInt(4, academicYear);
            stmt.setObject(5, coordinatorId);
            stmt.setObject(6, SessionManager.getCurrentSchoolId());

            int affectedRows = stmt.executeUpdate();
            LOGGER.log(Level.INFO, "Enrolled learner {0} in club {1} for term {2}/{3}",
                    new Object[]{learnerId, clubId, termNumber, academicYear});
            return affectedRows > 0;
        }
    }

    public List<Enrollment> getEnrollmentsByClub(Connection conn, UUID clubId,
                                                 int termNumber, int academicYear) throws SQLException {
        List<Enrollment> enrollments = new ArrayList<>();
        String sql = "SELECT ce.*, l.first_name || ' ' || l.last_name as learner_name, " +
                "c.club_name, l.admission_number " +
                "FROM club_enrollments ce " +
                "JOIN learners l ON ce.learner_id = l.learner_id " +
                "JOIN clubs c ON ce.club_id = c.club_id " +
                "WHERE ce.club_id = ? AND ce.term_number = ? AND ce.academic_year = ? " +
                "AND ce.is_active = true " +
                "ORDER BY l.first_name, l.last_name";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, clubId);
            stmt.setInt(2, termNumber);
            stmt.setInt(3, academicYear);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Enrollment enrollment = new Enrollment(
                            (UUID) rs.getObject("enrollment_id"),
                            (UUID) rs.getObject("learner_id"),
                            (UUID) rs.getObject("club_id"),
                            rs.getInt("term_number"),
                            rs.getInt("academic_year"),
                            rs.getTimestamp("enrollment_date"),
                            rs.getBoolean("is_active")
                    );
                    enrollment.setLearnerName(rs.getString("learner_name"));
                    enrollment.setClubName(rs.getString("club_name"));
                    enrollment.setAdmissionNumber(rs.getString("admission_number"));
                    enrollments.add(enrollment);
                }
            }
        }
        return enrollments;
    }

    public boolean withdrawLearner(Connection conn, UUID enrollmentId) throws SQLException {
        String sql = "UPDATE club_enrollments SET is_active = false " +
                "WHERE enrollment_id = ? AND is_active = true";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, enrollmentId);
            return stmt.executeUpdate() > 0;
        }
    }

    public boolean isLearnerEnrolledInTerm(Connection conn, UUID learnerId,
                                           int termNumber, int academicYear) throws SQLException {
        String sql = "SELECT COUNT(*) FROM club_enrollments " +
                "WHERE learner_id = ? AND term_number = ? AND academic_year = ? " +
                "AND is_active = true";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, learnerId);
            stmt.setInt(2, termNumber);
            stmt.setInt(3, academicYear);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    public boolean hasAvailableCapacity(Connection conn, UUID clubId,
                                        int termNumber, int academicYear) throws SQLException {
        // Get current enrollment count
        String countSql = "SELECT COUNT(*) FROM club_enrollments " +
                "WHERE club_id = ? AND term_number = ? AND academic_year = ? " +
                "AND is_active = true";

        int currentEnrollments;
        try (PreparedStatement stmt = conn.prepareStatement(countSql)) {
            stmt.setObject(1, clubId);
            stmt.setInt(2, termNumber);
            stmt.setInt(3, academicYear);

            try (ResultSet rs = stmt.executeQuery()) {
                currentEnrollments = rs.next() ? rs.getInt(1) : 0;
            }
        }

        // Get club capacity
        String capacitySql = "SELECT capacity FROM clubs WHERE club_id = ?";
        int clubCapacity;
        try (PreparedStatement stmt = conn.prepareStatement(capacitySql)) {
            stmt.setObject(1, clubId);
            try (ResultSet rs = stmt.executeQuery()) {
                clubCapacity = rs.next() ? rs.getInt("capacity") : 0;
            }
        }

        return currentEnrollments < clubCapacity;
    }

    public int getCurrentEnrollmentCount(Connection conn, UUID clubId,
                                         int termNumber, int academicYear) throws SQLException {
        String sql = "SELECT COUNT(*) FROM club_enrollments " +
                "WHERE club_id = ? AND term_number = ? AND academic_year = ? " +
                "AND is_active = true";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, clubId);
            stmt.setInt(2, termNumber);
            stmt.setInt(3, academicYear);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public List<Enrollment> getLearnerEnrollmentHistory(Connection conn, UUID learnerId) throws SQLException {
        List<Enrollment> history = new ArrayList<>();
        String sql = "SELECT ce.*, c.club_name " +
                "FROM club_enrollments ce " +
                "JOIN clubs c ON ce.club_id = c.club_id " +
                "WHERE ce.learner_id = ? " +
                "ORDER BY ce.academic_year DESC, ce.term_number DESC";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, learnerId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Enrollment enrollment = new Enrollment(
                            (UUID) rs.getObject("enrollment_id"),
                            learnerId,
                            (UUID) rs.getObject("club_id"),
                            rs.getInt("term_number"),
                            rs.getInt("academic_year"),
                            rs.getTimestamp("enrollment_date"),
                            rs.getBoolean("is_active")
                    );
                    enrollment.setClubName(rs.getString("club_name"));
                    history.add(enrollment);
                }
            }
        }
        return history;
    }

    public List<Enrollment> getEnrollmentsBySchoolAndTerm(Connection conn, UUID schoolId,
                                                          int termNumber, int academicYear) throws SQLException {
        List<Enrollment> enrollments = new ArrayList<>();
        String sql = "SELECT ce.*, l.first_name || ' ' || l.last_name as learner_name, " +
                "c.club_name, l.admission_number " +
                "FROM club_enrollments ce " +
                "JOIN learners l ON ce.learner_id = l.learner_id " +
                "JOIN clubs c ON ce.club_id = c.club_id " +
                "WHERE ce.school_id = ? AND ce.term_number = ? AND ce.academic_year = ? " +
                "AND ce.is_active = true " +
                "ORDER BY c.club_name, l.first_name, l.last_name";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, schoolId);
            stmt.setInt(2, termNumber);
            stmt.setInt(3, academicYear);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Enrollment enrollment = new Enrollment(
                            (UUID) rs.getObject("enrollment_id"),
                            (UUID) rs.getObject("learner_id"),
                            (UUID) rs.getObject("club_id"),
                            rs.getInt("term_number"),
                            rs.getInt("academic_year"),
                            rs.getTimestamp("enrollment_date"),
                            rs.getBoolean("is_active")
                    );
                    enrollment.setLearnerName(rs.getString("learner_name"));
                    enrollment.setClubName(rs.getString("club_name"));
                    enrollment.setAdmissionNumber(rs.getString("admission_number"));
                    enrollments.add(enrollment);
                }
            }
        }
        return enrollments;
    }
}