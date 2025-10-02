package com.cms.clubmanagementsystem.service;

import com.cms.clubmanagementsystem.model.ClubInfo;
import com.cms.clubmanagementsystem.model.ClubSchedule;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.sql.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClubService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final Logger LOGGER = Logger.getLogger(ClubService.class.getName());

    // NEW: Create club with multiple schedules
    public UUID createClubWithSchedules(Connection conn, String clubName, String description,
                                        UUID coordinatorId, UUID schoolId,
                                        List<ClubSchedule> schedules) throws SQLException {

        UUID clubId = null;

        try {
            conn.setAutoCommit(false); // Changed to false for transaction control

            // 1. Insert club basic info (NO schedule info in clubs table)
            String clubSql = """
            INSERT INTO clubs (club_name, description, school_id, is_active) 
            VALUES (?, ?, ?, ?) 
            RETURNING club_id
            """;

            try (PreparedStatement clubStmt = conn.prepareStatement(clubSql)) {
                String standardizedClubName = StringUtils.standardizeClubName(clubName);
                clubStmt.setString(1, standardizedClubName);
                clubStmt.setString(2, description);
                clubStmt.setObject(3, schoolId);
                clubStmt.setBoolean(4, true);

                try (ResultSet rs = clubStmt.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("Failed to create club");
                    }
                    clubId = (UUID) rs.getObject("club_id");
                }
            }

            // 2. Insert all schedules into club_schedules table
            String scheduleSql = """
            INSERT INTO club_schedules 
            (club_id, grade_id, class_group_id, meeting_day, start_time, end_time, venue, school_id) 
            VALUES (?, ?, ?, ?::meeting_day, ?, ?, ?, ?)
            """;

            try (PreparedStatement scheduleStmt = conn.prepareStatement(scheduleSql)) {
                for (ClubSchedule schedule : schedules) {
                    scheduleStmt.setObject(1, clubId);
                    scheduleStmt.setObject(2, schedule.getGradeId());
                    scheduleStmt.setObject(3, schedule.getClassGroupId());
                    scheduleStmt.setString(4, schedule.getMeetingDay());
                    scheduleStmt.setTime(5, Time.valueOf(schedule.getStartTime()));
                    scheduleStmt.setTime(6, Time.valueOf(schedule.getEndTime()));
                    scheduleStmt.setString(7, schedule.getVenue());
                    scheduleStmt.setObject(8, schoolId);
                    scheduleStmt.addBatch();
                }
                scheduleStmt.executeBatch();
            }

            conn.commit();
            LOGGER.log(Level.INFO, "Created club '{0}' with {1} schedules",
                    new Object[]{clubName, schedules.size()});

        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }

        return clubId;
    }

    // NEW: Get grades for a school
    public List<Map<String, Object>> getGrades(Connection conn, UUID schoolId) throws SQLException {
        List<Map<String, Object>> grades = new ArrayList<>();
        String sql = "SELECT grade_id, grade_name FROM grades WHERE school_id = ? ORDER BY grade_name";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, schoolId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> grade = new HashMap<>();
                    grade.put("gradeId", rs.getObject("grade_id"));
                    grade.put("gradeName", rs.getString("grade_name"));
                    grades.add(grade);
                }
            }
        }
        return grades;
    }

    // NEW: Get class groups for a school
    public List<Map<String, Object>> getClassGroups(Connection conn, UUID schoolId) throws SQLException {
        List<Map<String, Object>> classes = new ArrayList<>();
        String sql = "SELECT class_id, group_name FROM class_groups WHERE school_id = ? ORDER BY group_name";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, schoolId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> classGroup = new HashMap<>();
                    classGroup.put("classId", rs.getObject("class_id"));
                    classGroup.put("className", rs.getString("group_name"));
                    classes.add(classGroup);
                }
            }
        }
        return classes;
    }

    // NEW: Get schedules for a specific club
    public List<ClubSchedule> getClubSchedules(Connection conn, UUID clubId) throws SQLException {
        List<ClubSchedule> schedules = new ArrayList<>();
        String sql = """
            SELECT cs.schedule_id, cs.grade_id, cs.class_group_id, cs.meeting_day, 
                   cs.start_time, cs.end_time, cs.venue, cs.is_active,
                   g.grade_name, cg.group_name
            FROM club_schedules cs
            LEFT JOIN grades g ON cs.grade_id = g.grade_id
            LEFT JOIN class_groups cg ON cs.class_group_id = cg.class_id
            WHERE cs.club_id = ? AND cs.is_active = TRUE
            ORDER BY 
                CASE\s
                            WHEN cs.grade_id IS NULL THEN 2  -- "All Grades" comes after specific grades
                            ELSE 1
                        END,
                        g.grade_name ASC NULLS LAST,        -- Order specific grades alphabetically
                        cs.meeting_day ASC,
                        cs.start_time ASC
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, clubId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ClubSchedule schedule = new ClubSchedule();
                    schedule.setScheduleId((UUID) rs.getObject("schedule_id"));
                    schedule.setClubId(clubId);
                    schedule.setGradeId((UUID) rs.getObject("grade_id"));
                    schedule.setClassGroupId((UUID) rs.getObject("class_group_id"));
                    schedule.setMeetingDay(rs.getString("meeting_day"));
                    schedule.setStartTime(rs.getTime("start_time").toLocalTime());
                    schedule.setEndTime(rs.getTime("end_time").toLocalTime());
                    schedule.setVenue(rs.getString("venue"));
                    schedule.setActive(rs.getBoolean("is_active"));

                    // Additional info for display
                    schedule.setGradeName(rs.getString("grade_name"));
                    schedule.setClassName(rs.getString("group_name"));

                    schedules.add(schedule);
                }
            }
        }
        return schedules;
    }

    // NEW: Update club schedules (replace all existing schedules)
    public boolean updateClubSchedules(Connection conn, UUID clubId, List<ClubSchedule> schedules) throws SQLException {
        try {
            conn.setAutoCommit(false);

            // 1. Deactivate all existing schedules
            String deactivateSql = "UPDATE club_schedules SET is_active = FALSE WHERE club_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(deactivateSql)) {
                stmt.setObject(1, clubId);
                stmt.executeUpdate();
            }

            // 2. Insert new schedules
            String insertSql = """
                INSERT INTO club_schedules 
                (club_id, grade_id, class_group_id, meeting_day, start_time, end_time, venue, school_id) 
                VALUES (?, ?, ?, ?::meeting_day, ?, ?, ?, ?)
                """;

            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                for (ClubSchedule schedule : schedules) {
                    stmt.setObject(1, clubId);
                    stmt.setObject(2, schedule.getGradeId());
                    stmt.setObject(3, schedule.getClassGroupId());
                    stmt.setString(4, schedule.getMeetingDay());
                    stmt.setTime(5, Time.valueOf(schedule.getStartTime()));
                    stmt.setTime(6, Time.valueOf(schedule.getEndTime()));
                    stmt.setString(7, schedule.getVenue());
                    stmt.setObject(8, getSchoolIdFromClub(conn, clubId));
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }

            conn.commit();
            LOGGER.log(Level.INFO, "Updated {0} schedules for club ID: {1}",
                    new Object[]{schedules.size(), clubId});
            return true;

        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    // Helper method to get school ID from club
    private UUID getSchoolIdFromClub(Connection conn, UUID clubId) throws SQLException {
        String sql = "SELECT school_id FROM clubs WHERE club_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, clubId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return (UUID) rs.getObject("school_id");
                }
            }
        }
        throw new SQLException("Club not found: " + clubId);
    }

    // NEW: Check for schedule conflicts
    /**
     * Unified method to check for schedule conflicts
     *
     * @param excludeClubId - Use when checking for new schedules (exclude a specific club)
     * @param excludeScheduleId - Use when editing existing schedules (exclude a specific schedule)
     * @return true if there's a conflict, false otherwise
     */
    /**
     * Unified method to check for schedule conflicts
     */
    public boolean hasScheduleConflict(Connection conn, UUID gradeId, UUID classId,
                                       String meetingDay, LocalTime startTime, LocalTime endTime,
                                       UUID schoolId, UUID excludeClubId, UUID excludeScheduleId) throws SQLException {

        // Build the SQL query dynamically based on which parameters are null
        StringBuilder sqlBuilder = new StringBuilder("""
    SELECT COUNT(*) FROM club_schedules cs
    JOIN clubs c ON cs.club_id = c.club_id
    WHERE cs.grade_id = ? 
    AND cs.class_group_id = ? 
    AND cs.meeting_day = ?::meeting_day
    AND cs.is_active = TRUE 
    AND c.is_active = TRUE
    AND c.school_id = ?
    AND (
        (cs.start_time <= ? AND cs.end_time >= ?) OR
        (cs.start_time >= ? AND cs.start_time < ?) OR
        (cs.end_time > ? AND cs.end_time <= ?)
    )
    """);

        List<Object> parameters = new ArrayList<>();
        parameters.add(gradeId);
        parameters.add(classId);
        parameters.add(meetingDay);
        parameters.add(schoolId);

        // Time parameters
        Time startTimeSql = Time.valueOf(startTime);
        Time endTimeSql = Time.valueOf(endTime);
        parameters.add(startTimeSql);  // cs.start_time <= new_start
        parameters.add(endTimeSql);    // cs.end_time >= new_end
        parameters.add(startTimeSql);  // cs.start_time >= new_start
        parameters.add(endTimeSql);    // cs.start_time < new_end
        parameters.add(startTimeSql);  // cs.end_time > new_start
        parameters.add(endTimeSql);    // cs.end_time <= new_end

        // Handle excludeClubId
        if (excludeClubId != null) {
            sqlBuilder.append(" AND cs.club_id != ?");
            parameters.add(excludeClubId);
        }

        // Handle excludeScheduleId
        if (excludeScheduleId != null) {
            sqlBuilder.append(" AND cs.schedule_id != ?");
            parameters.add(excludeScheduleId);
        }

        String sql = sqlBuilder.toString();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            // Set all parameters
            for (int i = 0; i < parameters.size(); i++) {
                Object param = parameters.get(i);
                if (param instanceof UUID) {
                    stmt.setObject(i + 1, (UUID) param);
                } else if (param instanceof String) {
                    stmt.setString(i + 1, (String) param);
                } else if (param instanceof Time) {
                    stmt.setTime(i + 1, (Time) param);
                } else {
                    stmt.setObject(i + 1, param);
                }
            }

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    // UPDATED: Removed capacity parameter
    public List<ClubInfo> getActiveClubs(Connection conn, UUID schoolId) throws SQLException {
        List<ClubInfo> clubs = new ArrayList<>();
        String sql = "SELECT club_id, club_name FROM clubs " +
                "WHERE school_id = ? AND is_active = TRUE ORDER BY club_name";

        LOGGER.log(Level.INFO, "Loading active clubs for school: {0}", schoolId);

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, schoolId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    try {
                        UUID clubId = (UUID) rs.getObject("club_id");
                        String clubName = rs.getString("club_name");

                        clubs.add(new ClubInfo(clubId, clubName));
                        LOGGER.log(Level.FINE, "Found club: {0}", clubName);
                    } catch (Exception e) {
                        LOGGER.log(Level.SEVERE, "Error parsing club data", e);
                    }
                }
            }
        }
        LOGGER.log(Level.INFO, "Loaded {0} active clubs for school {1}",
                new Object[]{clubs.size(), schoolId});
        return clubs;
    }

    // REMOVED: getClubCapacity method

    // UPDATED: Now checks for both active and inactive clubs
    public boolean clubExists(Connection conn, String clubName, UUID schoolId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM clubs WHERE LOWER(club_name) = LOWER(?) AND school_id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, clubName);
            stmt.setObject(2, schoolId);

            try (ResultSet rs = stmt.executeQuery()) {
                boolean exists = rs.next() && rs.getInt(1) > 0;
                LOGGER.log(Level.INFO, "Club existence check: {0} in school {1} - exists: {2}",
                        new Object[]{clubName, schoolId, exists});
                return exists;
            }
        }
    }

    // NEW METHOD: Check if an active club exists
    public boolean activeClubExists(Connection conn, String clubName, UUID schoolId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM clubs WHERE LOWER(club_name) = LOWER(?) AND school_id = ? " +
                "AND is_active = TRUE";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, clubName);
            stmt.setObject(2, schoolId);

            try (ResultSet rs = stmt.executeQuery()) {
                boolean exists = rs.next() && rs.getInt(1) > 0;
                LOGGER.log(Level.INFO, "Active club existence check: {0} in school {1} - exists: {2}",
                        new Object[]{clubName, schoolId, exists});
                return exists;
            }
        }
    }

    // NEW METHOD: Reactivate a club by name
    public boolean reactivateClub(Connection conn, String clubName, UUID schoolId) throws SQLException {
        String sql = "UPDATE clubs SET is_active = TRUE, updated_at = CURRENT_TIMESTAMP " +
                "WHERE LOWER(club_name) = LOWER(?) AND school_id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, clubName);
            stmt.setObject(2, schoolId);
            int affectedRows = stmt.executeUpdate();

            LOGGER.log(Level.INFO, "Reactivated club: {0} in school {1}, affected rows: {2}",
                    new Object[]{clubName, schoolId, affectedRows});
            return affectedRows > 0;
        }
    }

    // NEW METHOD: Get club by name and school
    public Club getClubByName(Connection conn, String clubName, UUID schoolId) throws SQLException {
        String sql = "SELECT club_id, club_name, description, school_id, is_active " +
                "FROM clubs WHERE LOWER(club_name) = LOWER(?) AND school_id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, clubName);
            stmt.setObject(2, schoolId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new Club(
                            (UUID) rs.getObject("club_id"),
                            rs.getString("club_name"),
                            rs.getString("description"),
                            schoolId,
                            rs.getBoolean("is_active")
                    );
                }
            }
        }
        return null;
    }

    // NEW METHOD: Check if a specific club is active
    public boolean isClubActive(Connection conn, String clubName, UUID schoolId) throws SQLException {
        String sql = "SELECT is_active FROM clubs WHERE club_name = ? AND school_id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, clubName);
            stmt.setObject(2, schoolId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    boolean isActive = rs.getBoolean("is_active");
                    LOGGER.log(Level.INFO, "Club activity check: {0} in school {1} - active: {2}",
                            new Object[]{clubName, schoolId, isActive});
                    return isActive;
                }
            }
        }
        LOGGER.log(Level.INFO, "Club does not exist: {0} in school {1}",
                new Object[]{clubName, schoolId});
        return false; // Club doesn't exist at all
    }

    // create club
    public boolean createClub(Connection conn, String clubName, String description,
                              String meetingDay, String meetingTime, String venue,
                              UUID schoolId) throws SQLException {

        // Standardize the club name
        String standardizedClubName = StringUtils.standardizeClubName(clubName);

        String sql = "INSERT INTO clubs (club_name, description, meeting_day, " +
                "meeting_time, venue, school_id) " +
                "VALUES (?, ?, ?::meeting_day, ?, ?, ?)";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, standardizedClubName); // Use standardized name
            stmt.setString(2, description);
            stmt.setString(3, meetingDay);
            stmt.setTime(4, Time.valueOf(LocalTime.parse(meetingTime, TIME_FORMATTER)));
            stmt.setString(5, venue);
            stmt.setObject(6, schoolId);

            int affectedRows = stmt.executeUpdate();
            LOGGER.log(Level.INFO, "Created club: {0}, affected rows: {1}",
                    new Object[]{standardizedClubName, affectedRows});
            return affectedRows > 0;
        }
    }

    public List<Club> getClubsBySchool(Connection conn, UUID schoolId) throws SQLException {
        List<Club> clubs = new ArrayList<>();
        String sql = "SELECT club_id, club_name, description, school_id, is_active " +
                "FROM clubs WHERE school_id = ? AND is_active = TRUE ORDER BY club_name";

        LOGGER.log(Level.INFO, "Executing query: {0}", sql);
        LOGGER.log(Level.INFO, "School ID: {0}", schoolId);

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, schoolId);

            try (ResultSet rs = stmt.executeQuery()) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    try {
                        UUID clubId = (UUID) rs.getObject("club_id");
                        String clubName = rs.getString("club_name");
                        String description = rs.getString("description");
                        UUID resultSchoolId = (UUID) rs.getObject("school_id");
                        boolean isActive = rs.getBoolean("is_active");

                        LOGGER.log(Level.INFO, "Club {0}: ID={1}, Name={2}, Active={3}",
                                new Object[]{count, clubId, clubName, isActive});

                        Club club = new Club(clubId, clubName, description, resultSchoolId, isActive);
                        clubs.add(club);

                    } catch (Exception e) {
                        LOGGER.log(Level.SEVERE, "Error parsing club data at row " + count, e);
                    }
                }
                LOGGER.log(Level.INFO, "Total rows processed: {0}", count);
            }
        }
        LOGGER.log(Level.INFO, "Loaded {0} clubs for school {1}",
                new Object[]{clubs.size(), schoolId});
        return clubs;
    }
    // Update club
    public boolean updateClub(Connection conn, UUID clubId, String clubName, String description,
                              String meetingDay, String meetingTime, String venue) throws SQLException {

        // Standardize the club name
        String standardizedClubName = StringUtils.standardizeClubName(clubName);

        String sql = "UPDATE clubs SET club_name = ?, description = ?, updated_at = CURRENT_TIMESTAMP " +
                "WHERE club_id = ? AND is_active = TRUE";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, standardizedClubName); // Use standardized name
            stmt.setString(2, description);
            stmt.setObject(3, clubId);

            int affectedRows = stmt.executeUpdate();
            LOGGER.log(Level.INFO, "Updated club: {0}, affected rows: {1}",
                    new Object[]{standardizedClubName, affectedRows});
            return affectedRows > 0;
        }
    }

    public boolean deactivateClub(Connection conn, UUID clubId) throws SQLException {
        String sql = "UPDATE clubs SET is_active = FALSE, updated_at = CURRENT_TIMESTAMP WHERE club_id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, clubId);
            int affectedRows = stmt.executeUpdate();
            LOGGER.log(Level.INFO, "Deactivated club ID: {0}, affected rows: {1}",
                    new Object[]{clubId, affectedRows});
            return affectedRows > 0;
        }
    }

    public static class Club {
        private final StringProperty clubName = new SimpleStringProperty();
        private final StringProperty description = new SimpleStringProperty();
        private UUID clubId;
        private UUID schoolId;
        private boolean isActive;

        @Override
        public String toString() {
            return getClubName();
        }

        public Club() {
            // Default constructor needed for JavaFX
        }

        // UPDATED: Removed schedule parameters
        public Club(UUID clubId, String clubName, String description, UUID schoolId, boolean isActive) {
            this.clubId = clubId;
            setClubName(clubName);
            setDescription(description);
            this.schoolId = schoolId;
            this.isActive = isActive;
        }

        // Standard getters and setters
        public String getClubName() { return clubName.get(); }
        public void setClubName(String clubName) { this.clubName.set(clubName); }
        public StringProperty clubNameProperty() { return clubName; }

        public String getDescription() { return description.get(); }
        public void setDescription(String description) { this.description.set(description); }
        public StringProperty descriptionProperty() { return description; }

        public UUID getClubId() { return clubId; }
        public void setClubId(UUID clubId) { this.clubId = clubId; }
        public UUID getSchoolId() { return schoolId; }
        public void setSchoolId(UUID schoolId) { this.schoolId = schoolId; }
        public boolean isActive() { return isActive; }
        public void setActive(boolean active) { isActive = active; }
    }

    public class StringUtils {

        public static String standardizeClubName(String clubName) {
            if (clubName == null || clubName.trim().isEmpty()) {
                return clubName;
            }

            String trimmed = clubName.trim();

            // Convert to title case (first letter of each word capitalized, rest lowercase)
            String[] words = trimmed.split("\\s+");
            StringBuilder standardized = new StringBuilder();

            for (int i = 0; i < words.length; i++) {
                if (!words[i].isEmpty()) {
                    if (i > 0) {
                        standardized.append(" ");
                    }
                    // Capitalize first letter, lowercase the rest
                    standardized.append(Character.toUpperCase(words[i].charAt(0)))
                            .append(words[i].substring(1).toLowerCase());
                }
            }

            return standardized.toString();
        }

        // Special handling for acronyms or special cases
        private static final Set<String> SPECIAL_CASES = Set.of(
                "STEM", "ICT", "IT", "AI", "VR", "AR", "NASA", "UNICEF", "UNESCO"
        );

        public static String standardizeWithSpecialCases(String clubName) {
            if (clubName == null || clubName.trim().isEmpty()) {
                return clubName;
            }

            String trimmed = clubName.trim();

            // Check if the entire name is a special case (acronym)
            if (SPECIAL_CASES.contains(trimmed.toUpperCase())) {
                return trimmed.toUpperCase();
            }

            // Handle mixed cases where acronyms might be part of the name
            String[] words = trimmed.split("\\s+");
            StringBuilder standardized = new StringBuilder();

            for (int i = 0; i < words.length; i++) {
                if (!words[i].isEmpty()) {
                    if (i > 0) {
                        standardized.append(" ");
                    }

                    String upperWord = words[i].toUpperCase();
                    if (SPECIAL_CASES.contains(upperWord)) {
                        standardized.append(upperWord);
                    } else {
                        // Capitalize first letter, lowercase the rest
                        standardized.append(Character.toUpperCase(words[i].charAt(0)))
                                .append(words[i].substring(1).toLowerCase());
                    }
                }
            }

            return standardized.toString();
        }
    }

    // Add these methods to your existing ClubService class

    /**
     * Create club without schedules (basic info only)
     */
    public UUID createClub(Connection conn, String clubName, String description,
                           UUID coordinatorId, UUID schoolId) throws SQLException {

        // Store the original auto-commit state
        boolean originalAutoCommit = conn.getAutoCommit();

        try {
            // Start transaction
            conn.setAutoCommit(false);

            String sql = """
            INSERT INTO clubs (club_name, description, school_id, is_active) 
            VALUES (?, ?, ?, ?) 
            RETURNING club_id
            """;

            UUID clubId = null;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                String standardizedClubName = StringUtils.standardizeClubName(clubName);
                stmt.setString(1, standardizedClubName);
                stmt.setString(2, description);
                stmt.setObject(3, schoolId);
                stmt.setBoolean(4, true);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        clubId = (UUID) rs.getObject("club_id");
                        LOGGER.log(Level.INFO, "Created club: {0} with ID: {1}",
                                new Object[]{standardizedClubName, clubId});
                    } else {
                        throw new SQLException("Failed to create club - no ID returned");
                    }
                }
            }

            // If we reach here, commit the transaction
            conn.commit();
            return clubId;

        } catch (SQLException e) {
            // Rollback on any error
            try {
                conn.rollback();
                LOGGER.log(Level.WARNING, "Transaction rolled back due to error: {0}", e.getMessage());
            } catch (SQLException rollbackEx) {
                LOGGER.log(Level.SEVERE, "Failed to rollback transaction", rollbackEx);
            }
            throw e; // Re-throw the original exception
        } finally {
            // Restore original auto-commit state
            try {
                conn.setAutoCommit(originalAutoCommit);
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Failed to restore auto-commit state", e);
            }
        }
    }

    /**
     * Add schedules to an existing club
     */
    public boolean addSchedulesToClub(Connection conn, UUID clubId,
                                      List<ClubSchedule> schedules) throws SQLException {
        if (schedules.isEmpty()) {
            return true; // Nothing to add
        }

        String sql = """
        INSERT INTO club_schedules 
        (club_id, grade_id, class_group_id, meeting_day, start_time, end_time, venue, school_id) 
        VALUES (?, ?, ?, ?::meeting_day, ?, ?, ?, ?)
        """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            UUID schoolId = getSchoolIdFromClub(conn, clubId);

            for (ClubSchedule schedule : schedules) {
                stmt.setObject(1, clubId);
                stmt.setObject(2, schedule.getGradeId());
                stmt.setObject(3, schedule.getClassGroupId());
                stmt.setString(4, schedule.getMeetingDay());
                stmt.setTime(5, Time.valueOf(schedule.getStartTime()));
                stmt.setTime(6, Time.valueOf(schedule.getEndTime()));
                stmt.setString(7, schedule.getVenue());
                stmt.setObject(8, schoolId);
                stmt.addBatch();
            }

            int[] results = stmt.executeBatch();
            LOGGER.log(Level.INFO, "Added {0} schedules to club ID: {1}",
                    new Object[]{schedules.size(), clubId});
            return true;
        }
    }

    /**
     * Get club by ID
     */
    public Club getClubById(Connection conn, UUID clubId) throws SQLException {
        String sql = "SELECT club_id, club_name, description, school_id, is_active " +
                "FROM clubs WHERE club_id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, clubId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new Club(
                            (UUID) rs.getObject("club_id"),
                            rs.getString("club_name"),
                            rs.getString("description"),
                            (UUID) rs.getObject("school_id"),
                            rs.getBoolean("is_active")
                    );
                }
            }
        }
        return null;
    }

    public boolean deleteSchedule(Connection conn, UUID scheduleId) throws SQLException {
        String sql = "DELETE FROM club_schedules WHERE schedule_id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, scheduleId);
            return stmt.executeUpdate() > 0;
        }
    }

    public boolean updateSchedule(Connection conn, UUID scheduleId, UUID gradeId, UUID classId,
                                  String day, LocalTime startTime, LocalTime endTime, String venue) throws SQLException {
        String sql = "UPDATE club_schedules SET grade_id = ?, class_group_id = ?, meeting_day = ?::meeting_day , " +
                "start_time = ?, end_time = ?, venue = ? WHERE schedule_id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, gradeId);
            stmt.setObject(2, classId);
            stmt.setString(3, day);
            stmt.setTime(4, Time.valueOf(startTime));
            stmt.setTime(5, Time.valueOf(endTime));
            stmt.setString(6, venue);
            stmt.setObject(7, scheduleId);

            return stmt.executeUpdate() > 0;
        }
    }

}