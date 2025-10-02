package com.cms.clubmanagementsystem.service;

import com.cms.clubmanagementsystem.model.Learner;
import com.cms.clubmanagementsystem.utils.DatabaseConnector;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class StudentService {

    public List<Learner> fetchAllStudentsFromDatabase(UUID schoolId) {
        List<Learner> students = new ArrayList<>();
        String sql = "SELECT l.learner_id, l.admission_number, l.full_name, " +
                "l.grade_id, g.grade_name, l.school_id, " + // Removed is_active
                "l.date_joined_school, l.created_at, l.gender " +
                "FROM learners l " +
                "LEFT JOIN grades g ON l.grade_id = g.grade_id " +
                "WHERE l.school_id = ? " +
                "ORDER BY l.full_name";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setObject(1, schoolId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Learner learner = new Learner(
                            (UUID) rs.getObject("learner_id"),
                            rs.getString("admission_number"),
                            rs.getString("full_name"),
                            (UUID) rs.getObject("grade_id"),
                            rs.getString("grade_name"),
                            (UUID) rs.getObject("school_id"),
                            rs.getDate("date_joined_school").toLocalDate(),
                            rs.getTimestamp("created_at") != null ?
                                    rs.getTimestamp("created_at").toInstant().atZone(java.time.ZoneId.systemDefault()) : null,
                            rs.getString("gender")
                    );
                    students.add(learner);
                }
            }

        } catch (SQLException e) {
            System.err.println("Error fetching students from database: " + e.getMessage());
            e.printStackTrace();
        }

        return students;
    }

}