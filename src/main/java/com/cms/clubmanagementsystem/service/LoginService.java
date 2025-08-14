package com.cms.clubmanagementsystem.service;

import com.cms.clubmanagementsystem.utils.DatabaseConnector;
import com.cms.clubmanagementsystem.utils.PasswordUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class LoginService {
    public static boolean validateLogin(String username, String plainPassword) {
        try (Connection conn = DatabaseConnector.getConnection()) {
            // 1. Retrieve stored hash from DB
            String sql = "SELECT password_hash FROM users WHERE username = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, username);

            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String storedHash = rs.getString("password_hash");
                // 2. Verify password
                return PasswordUtils.verifyPassword(plainPassword, storedHash);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}
