package com.cms.clubmanagementsystem.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class StatsService {

    private static final Logger LOGGER = Logger.getLogger(StatsService.class.getName());

    // Simplified DashboardStats class - only what we need now
    public static class DashboardStats {
        private int totalClubs;
        private int totalMembers;
        private int totalTeachers;
        private int totalLearners;
        private int todayAttendance;
        private int attendanceThisWeek;
        private double weeklyAttendanceRate;
        private double programParticipationRate;

        public DashboardStats(int totalClubs, int totalMembers, int totalTeachers,
                              int totalLearners, int todayAttendance, int attendanceThisWeek,
                              double weeklyAttendanceRate, double programParticipationRate) {
            this.totalClubs = totalClubs;
            this.totalMembers = totalMembers;
            this.totalTeachers = totalTeachers;
            this.totalLearners = totalLearners;
            this.todayAttendance = todayAttendance;
            this.attendanceThisWeek = attendanceThisWeek;
            this.weeklyAttendanceRate = weeklyAttendanceRate;
            this.programParticipationRate = programParticipationRate;
        }

        // Getters
        public int getTotalClubs() { return totalClubs; }
        public int getTotalMembers() { return totalMembers; }
        public int getTotalTeachers() { return totalTeachers; }
        public int getTotalLearners() { return totalLearners; }
        public int getTodayAttendance() { return todayAttendance; }
        public int getAttendanceThisWeek() { return attendanceThisWeek; }
        public double getWeeklyAttendanceRate() { return weeklyAttendanceRate; } // New getter
        public double getProgramParticipationRate() { return programParticipationRate; }
    }

    public DashboardStats getDashboardStats(Connection conn, UUID schoolId) throws SQLException {
        int totalLearners = getTotalLearners(conn, schoolId);
        int totalMembers = getTotalMembers(conn, schoolId);
        int attendanceThisWeek = getAttendanceThisWeek(conn, schoolId);

        // Calculate Weekly Attendance Rate
        int expectedAttendancesThisWeek = getExpectedAttendancesThisWeek(conn, schoolId);

        double weeklyAttendanceRate = (expectedAttendancesThisWeek == 0) ? 0.0 :
                (attendanceThisWeek * 100.0) / expectedAttendancesThisWeek;

        // Calculate Program Participation Rate
        double programParticipationRate = (totalLearners == 0) ? 0.0 :
                (totalMembers * 100.0) / totalLearners;

        return new DashboardStats(
                getTotalClubs(conn, schoolId),
                totalMembers,
                getTotalTeachers(conn, schoolId),
                totalLearners,
                getTodayAttendance(conn, schoolId),
                attendanceThisWeek,
                weeklyAttendanceRate,
                programParticipationRate
        );
    }

    // Calculate expected attendances for the current week
    private int getExpectedAttendancesThisWeek(Connection conn, UUID schoolId) throws SQLException {
        // This counts all enrollments in active clubs for the current week
        String sql = "SELECT COUNT(*) " +
                "FROM club_enrollments ce " +
                "JOIN club_schedules cs ON ce.club_id = cs.club_id " +  // Changed to club_schedules
                "WHERE cs.school_id = ? " +
                "AND ce.is_active = TRUE " +
                "AND cs.is_active = TRUE";  // Check if schedule is active

        return executeCountQuery(conn, sql, schoolId);
    }

    private int getTotalClubs(Connection conn, UUID schoolId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM clubs WHERE school_id = ? AND is_active = TRUE";
        return executeCountQuery(conn, sql, schoolId);
    }

    private int getTotalMembers(Connection conn, UUID schoolId) throws SQLException {
        String sql = "SELECT COUNT(DISTINCT learner_id) FROM club_enrollments ce " +
                "JOIN clubs c ON ce.club_id = c.club_id " +
                "WHERE c.school_id = ? AND ce.is_active = TRUE AND c.is_active = TRUE";
        return executeCountQuery(conn, sql, schoolId);
    }

    private int getTotalTeachers(Connection conn, UUID schoolId) throws SQLException {
        String sql = "SELECT COUNT(DISTINCT ct.teacher_id) " +
                "FROM club_teachers ct " +
                "JOIN clubs c ON ct.club_id = c.club_id " +
                "JOIN users u ON ct.teacher_id = u.user_id " + // Join with users table
                "WHERE c.school_id = ? " +
                "AND c.is_active = TRUE " +
                "AND u.is_active = TRUE"; // Check if teacher is active

        return executeCountQuery(conn, sql, schoolId);
    }

    private int getTotalLearners(Connection conn, UUID schoolId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM learners WHERE school_id = ? AND is_active = TRUE";
        return executeCountQuery(conn, sql, schoolId);
    }

    private int getTodayAttendance(Connection conn, UUID schoolId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM attendance_records ar " +
                "JOIN attendance_sessions a ON ar.session_id = a.session_id " +
                "JOIN clubs c ON a.club_id = c.club_id " +
                "WHERE c.school_id = ? AND a.session_date = CURRENT_DATE " +
                "AND ar.status = 'present'";
        return executeCountQuery(conn, sql, schoolId);
    }

    private int getAttendanceThisWeek(Connection conn, UUID schoolId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM attendance_records ar " +
                "JOIN attendance_sessions a ON ar.session_id = a.session_id " +
                "JOIN clubs c ON a.club_id = c.club_id " +
                "WHERE c.school_id = ? AND a.session_date >= DATE_TRUNC('week', CURRENT_DATE) " +
                "AND a.session_date < DATE_TRUNC('week', CURRENT_DATE) + INTERVAL '7 days' " +
                "AND ar.status = 'present'";
        return executeCountQuery(conn, sql, schoolId);
    }

    // Additional method to get attendance percentage (very useful for your setup)
    public double getTodayAttendancePercentage(Connection conn, UUID schoolId) throws SQLException {
        // Get total learners expected to attend today (based on club meeting days)
        String expectedSql = "SELECT COUNT(DISTINCT ce.learner_id) " +
                "FROM club_enrollments ce " +
                "JOIN club_schedules cs ON ce.club_id = cs.club_id " +  // Changed to club_schedules
                "WHERE cs.school_id = ? " +
                "AND ce.is_active = TRUE " +
                "AND cs.is_active = TRUE " +  // Check if schedule is active
                "AND cs.meeting_day = ?::meeting_day";  // Use cs.meeting_day instead of c.meeting_day

        // Get actual attendance for today
        String actualSql = "SELECT COUNT(DISTINCT ar.learner_id) " +
                "FROM attendance_records ar " +
                "JOIN attendance_sessions a ON ar.session_id = a.session_id " +
                "JOIN clubs c ON a.club_id = c.club_id " +
                "WHERE c.school_id = ? " +
                "AND a.session_date = CURRENT_DATE " +
                "AND ar.status = 'present'";

        // Map current day to meeting_day enum (MON, TUE, WED, etc.)
        String todayMeetingDay = getTodayMeetingDay();

        int expected = executeCountQueryWithDay(conn, expectedSql, schoolId, todayMeetingDay);
        int actual = executeCountQuery(conn, actualSql, schoolId);

        if (expected == 0) return 0.0;
        return (actual * 100.0) / expected;
    }

    // Helper method to get today's meeting day abbreviation
    private String getTodayMeetingDay() {
        // Calendar constants: SUNDAY=1, MONDAY=2, ..., SATURDAY=7
        String[] days = {"SUN", "MON", "TUE", "WED", "THUR", "FRI", "SAT"};
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        int dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK);

        // Validate the range
        if (dayOfWeek < 1 || dayOfWeek > 7) {
            LOGGER.warning("Invalid day of week: " + dayOfWeek);
            return "MON"; // Default to Monday if unexpected value
        }

        return days[dayOfWeek - 1];
    }

    // Helper method for queries with day parameter
    private int executeCountQueryWithDay(Connection conn, String sql, UUID schoolId, String day) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, schoolId);
            stmt.setString(2, day);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }


    // Helper method
    private int executeCountQuery(Connection conn, String sql, UUID schoolId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, schoolId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}