package com.cms.clubmanagementsystem.controller;

import com.cms.clubmanagementsystem.utils.DatabaseConnector;
import com.cms.clubmanagementsystem.utils.PasswordUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;

public class UserRegistrationController {
    public static void registerUser(String username, String plainPassword) {
        //Validate password strength FIRST
        if (!PasswordUtils.isPasswordStrong(plainPassword)) {
            throw new IllegalArgumentException(
                    "Password must be 8+ chars with uppercase and special characters"
            );
        }
        //Hash the password
        String hashedPassword = PasswordUtils.hashPassword(plainPassword);

        //Store in database
        try (Connection conn = DatabaseConnector.getConnection()) {
            String sql = "INSERT INTO users (username, password_hash) VALUES (?, ?)";
            PreparedStatement stmt = conn.prepareStatement(sql);

            stmt.setString(1, username);
            stmt.setString(2, hashedPassword);  // Store the hash, not plain text

            stmt.executeUpdate();
            System.out.println("User registered successfully!");
        } catch (Exception e) {
            System.err.println("Registration failed: " + e.getMessage());
        }
    }
}
