package com.cms.clubmanagementsystem.service;

import com.cms.clubmanagementsystem.utils.DatabaseConnector;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;

public class AuthService {

    public void storePasswordResetToken(String email, String token, int expiryMinutes) throws SQLException {
        String sql = "UPDATE users SET reset_token = ?, reset_token_expiry = ? WHERE email = ?";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, token);
            stmt.setObject(2, LocalDateTime.now().plusMinutes(expiryMinutes));
            stmt.setString(3, email);

            stmt.executeUpdate();
        }
    }
}
