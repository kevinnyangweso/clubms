package com.cms.clubmanagementsystem.service;

import java.sql.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ClubService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public boolean createClub(Connection conn, String clubName, String description,
                              String meetingDay, String meetingTime, String venue,
                              UUID schoolId, int capacity) throws SQLException {

        String sql = "INSERT INTO clubs (club_name, description, meeting_day, " +
                "meeting_time, venue, school_id, capacity) " +
                "VALUES (?, ?, ?::meeting_day, ?, ?, ?, ?)";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, clubName);
            stmt.setString(2, description);
            stmt.setString(3, meetingDay);
            stmt.setTime(4, Time.valueOf(LocalTime.parse(meetingTime, TIME_FORMATTER)));
            stmt.setString(5, venue);
            stmt.setObject(6, schoolId);
            stmt.setInt(7, capacity);

            return stmt.executeUpdate() > 0;
        }
    }

    public List<Club> getClubsBySchool(Connection conn, UUID schoolId) throws SQLException {
        List<Club> clubs = new ArrayList<>();
        String sql = "SELECT * FROM clubs WHERE school_id = ? AND is_active = TRUE ORDER BY club_name";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, schoolId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Club club = new Club(
                            (UUID) rs.getObject("club_id"),
                            rs.getString("club_name"),
                            rs.getString("description"),
                            rs.getString("meeting_day"),
                            rs.getTime("meeting_time").toLocalTime().format(TIME_FORMATTER),
                            rs.getString("venue"),
                            (UUID) rs.getObject("school_id"),
                            rs.getInt("capacity"),
                            rs.getBoolean("is_active")
                    );
                    clubs.add(club);
                }
            }
        }
        return clubs;
    }

    public boolean updateClub(Connection conn, UUID clubId, String clubName, String description,
                              String meetingDay, String meetingTime, String venue,
                              int capacity) throws SQLException {

        String sql = "UPDATE clubs SET club_name = ?, description = ?, meeting_day = ?::meeting_day, " +
                "meeting_time = ?, venue = ?, capacity = ?, updated_at = CURRENT_TIMESTAMP " +
                "WHERE club_id = ? AND is_active = TRUE";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, clubName);
            stmt.setString(2, description);
            stmt.setString(3, meetingDay);
            stmt.setTime(4, Time.valueOf(LocalTime.parse(meetingTime, TIME_FORMATTER)));
            stmt.setString(5, venue);
            stmt.setInt(6, capacity);
            stmt.setObject(7, clubId);

            return stmt.executeUpdate() > 0;
        }
    }

    public boolean deactivateClub(Connection conn, UUID clubId) throws SQLException {
        String sql = "UPDATE clubs SET is_active = FALSE, updated_at = CURRENT_TIMESTAMP WHERE club_id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, clubId);
            return stmt.executeUpdate() > 0;
        }
    }

    public boolean clubExists(Connection conn, String clubName, UUID schoolId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM clubs WHERE club_name = ? AND school_id = ? AND is_active = TRUE";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, clubName);
            stmt.setObject(2, schoolId);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    // Club data transfer object
    public static class Club {
        private final UUID clubId;
        private final String clubName;
        private final String description;
        private final String meetingDay;
        private final String meetingTime;
        private final String venue;
        private final UUID schoolId;
        private final int capacity;
        private final boolean isActive;

        public Club(UUID clubId, String clubName, String description, String meetingDay,
                    String meetingTime, String venue, UUID schoolId, int capacity, boolean isActive) {
            this.clubId = clubId;
            this.clubName = clubName;
            this.description = description;
            this.meetingDay = meetingDay;
            this.meetingTime = meetingTime;
            this.venue = venue;
            this.schoolId = schoolId;
            this.capacity = capacity;
            this.isActive = isActive;
        }

        // Getters
        public UUID getClubId() { return clubId; }
        public String getClubName() { return clubName; }
        public String getDescription() { return description; }
        public String getMeetingDay() { return meetingDay; }
        public String getMeetingTime() { return meetingTime; }
        public String getVenue() { return venue; }
        public UUID getSchoolId() { return schoolId; }
        public int getCapacity() { return capacity; }
        public boolean isActive() { return isActive; }
    }
}