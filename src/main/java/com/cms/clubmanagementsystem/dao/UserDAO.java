package com.cms.clubmanagementsystem.dao;

import com.cms.clubmanagementsystem.model.User;
import com.cms.clubmanagementsystem.utils.DatabaseConnector;

import java.sql.*;
import java.util.UUID;

public class UserDAO {

    public User findByEmailOrUsername(Connection conn, String emailOrUsername) throws SQLException {
        String sql = "SELECT user_id, username, email, password_hash, is_active, school_id, role " +
                "FROM users " +
                "WHERE email = ? OR username = ? " +
                "LIMIT 1";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, emailOrUsername);
            stmt.setString(2, emailOrUsername);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new User(
                            rs.getObject("user_id", java.util.UUID.class),
                            rs.getString("username"),
                            rs.getString("email"),
                            rs.getString("password_hash"),
                            rs.getBoolean("is_active"),
                            rs.getObject("school_id", java.util.UUID.class),
                            rs.getString("role")
                    );
                }
            }
        }
        return null; // no match
    }


    public void savePasswordResetToken(Connection conn, UUID userId, String token) throws SQLException {
        String sql = """
        INSERT INTO password_reset_tokens (user_id, token, expires_at)
        VALUES (?, ?, NOW() + INTERVAL '1 hour')
        ON CONFLICT (user_id) DO UPDATE
        SET token = EXCLUDED.token,
            expires_at = EXCLUDED.expires_at
    """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, userId);
            stmt.setString(2, token);
            stmt.executeUpdate();
        }
    }

}

