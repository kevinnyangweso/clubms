package com.cms.clubmanagementsystem.service;

import com.cms.clubmanagementsystem.utils.DatabaseConnector;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GradeService {
    private final UUID currentSchoolId;
    private final Map<String, UUID> gradeCache = new HashMap<>();

    public GradeService(UUID schoolId) {
        this.currentSchoolId = schoolId;
        loadGradeCache();
    }

    private void loadGradeCache() {
        String sql = "SELECT grade_id, grade_name FROM grades WHERE school_id = ?";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setObject(1, currentSchoolId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    UUID gradeId = (UUID) rs.getObject("grade_id");
                    String gradeName = rs.getString("grade_name");
                    gradeCache.put(gradeName.toLowerCase(), gradeId);
                }
            }
        } catch (SQLException e) {
            // Handle error
        }
    }

    public UUID getGradeIdByName(String gradeName) {
        return gradeCache.get(gradeName.toLowerCase());
    }

    public void refreshCache() {
        gradeCache.clear();
        loadGradeCache();
    }
}
