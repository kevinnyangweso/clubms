package com.cms.clubmanagementsystem.utils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.security.SecureRandom;

import javafx.scene.control.Alert;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CoordinatorManager {
    private static final Logger logger = LoggerFactory.getLogger(CoordinatorManager.class);

    public static boolean updateCoordinatorStatus(UUID userId, boolean makeActive) {
        Connection conn = null;
        try {
            conn = DatabaseConnector.getConnection();
            conn.setAutoCommit(false); // Start transaction

            UUID schoolId = SessionManager.getCurrentSchoolId();

            if (makeActive) {
                // First, deactivate all current coordinators
                String deactivateSql = """
                    UPDATE users 
                    SET is_active_coordinator = FALSE 
                    WHERE school_id = ? 
                    AND role = 'club_coordinator'
                    AND is_active_coordinator = TRUE
                    """;

                try (PreparedStatement deactivateStmt = conn.prepareStatement(deactivateSql)) {
                    deactivateStmt.setObject(1, schoolId);
                    deactivateStmt.executeUpdate();
                }

                // Then activate the specified coordinator
                String activateSql = """
                    UPDATE users 
                    SET is_active_coordinator = TRUE 
                    WHERE user_id = ? 
                    AND school_id = ? 
                    AND role = 'club_coordinator'
                    """;

                try (PreparedStatement activateStmt = conn.prepareStatement(activateSql)) {
                    activateStmt.setObject(1, userId);
                    activateStmt.setObject(2, schoolId);
                    int rowsUpdated = activateStmt.executeUpdate();

                    if (rowsUpdated > 0) {
                        conn.commit();
                        return true;
                    } else {
                        conn.rollback();
                        return false;
                    }
                }
            } else {
                String sql = "UPDATE users SET is_active_coordinator = FALSE WHERE user_id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setObject(1, userId);
                    int rowsUpdated = stmt.executeUpdate();
                    conn.commit();
                    return rowsUpdated > 0;
                }
            }
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    logger.error("Error rolling back transaction", ex);
                }
            }
            logger.error("Error updating coordinator status", e);
            return false;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    logger.error("Error closing connection", e);
                }
            }
        }
    }

    public static boolean canCreateCoordinator(UUID schoolId) {
        String sql = """
            SELECT COUNT(*) 
            FROM users 
            WHERE school_id = ? 
            AND role = 'club_coordinator' 
            AND is_active_coordinator = TRUE
            """;

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, schoolId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) == 0;
            }
        } catch (SQLException e) {
            logger.error("Error checking coordinator status", e);
        }
        return false;
    }

    public static UUID getActiveCoordinatorId(UUID schoolId) {
        String sql = """
            SELECT user_id 
            FROM users 
            WHERE school_id = ? 
            AND role = 'club_coordinator' 
            AND is_active_coordinator = TRUE
            """;

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, schoolId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return (UUID) rs.getObject("user_id");
            }
        } catch (SQLException e) {
            logger.error("Error getting active coordinator", e);
        }
        return null;
    }

    public static void showAlert(String title, String message, javafx.scene.control.Alert.AlertType type) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static boolean createUserWithCoordinatorValidation(String username, String password, String fullName,
                                                              String email, String role, String phone,
                                                              UUID schoolId, boolean makeCoordinatorActive) {

        // Check if current user is authorized to create users
        if (!isCurrentUserActiveCoordinator()) {
            logger.error("Unauthorized: Only active coordinators can register users");
            showAlert("Access Denied", "Only active coordinators can register users", Alert.AlertType.ERROR);
            return false;
        }

        // Validate coordinator creation
        if (role.equals("club_coordinator") && makeCoordinatorActive) {
            if (!canCreateCoordinator(schoolId)) {
                logger.error("Cannot create active coordinator: One already exists");
                showAlert("Error", "Cannot create active coordinator: One already exists", Alert.AlertType.ERROR);
                return false;
            }
        }

        try {
            String sql = """
            INSERT INTO users (username, password_hash, full_name, email, role, phone, school_id, is_active_coordinator, first_login)
            VALUES (?, ?, ?, ?, ?::user_role, ?, ?, ?, ?)
            """;

            try (Connection conn = DatabaseConnector.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                // Hash the password using BCrypt
                String hashedPassword = hashPassword(password);

                stmt.setString(1, username.toLowerCase());
                stmt.setString(2, hashedPassword);
                stmt.setString(3, fullName);
                stmt.setString(4, email.toLowerCase());
                stmt.setString(5, role);
                stmt.setString(6, phone);
                stmt.setObject(7, schoolId);
                stmt.setBoolean(8, role.equals("club_coordinator") && makeCoordinatorActive);
                stmt.setBoolean(9, true); // First login required

                int rowsAffected = stmt.executeUpdate();
                return rowsAffected > 0;
            }
        } catch (SQLException e) {
            logger.error("Error creating user", e);
            return false;
        }
    }

    /**
     * Hashes a password using BCrypt with a work factor of 12
     * @param password the plain text password to hash
     * @return the hashed password
     */
    public static String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt(12));
    }

    /**
     * Verifies a plain text password against a hashed password
     * @param plainPassword the plain text password to verify
     * @param hashedPassword the hashed password to compare against
     * @return true if the passwords match, false otherwise
     */
    public static boolean verifyPassword(String plainPassword, String hashedPassword) {
        try {
            return BCrypt.checkpw(plainPassword, hashedPassword);
        } catch (Exception e) {
            logger.error("Error verifying password", e);
            return false;
        }
    }

    // Add this overloaded method for creating teachers with automatic temporary password
    public static boolean createTeacherWithClubAssignment(String username, String fullName,
                                                          String email, String phone, UUID schoolId, UUID clubId) {

        // Check if current user is authorized to create users
        if (!isCurrentUserActiveCoordinator()) {
            System.err.println("Unauthorized: Only active coordinators can register teachers");
            showAlert("Access Denied", "Only active coordinators can register teachers", Alert.AlertType.ERROR);
            return false;
        }

        // Generate temporary password
        String tempPassword = generateTemporaryPassword();
        String hashedPassword = hashPassword(tempPassword);

        Connection conn = null;
        UUID userId = null;

        try {
            conn = DatabaseConnector.getConnection();
            conn.setAutoCommit(false); // Start transaction

            // First, create the user with first_login = TRUE
            String userSql = """
    INSERT INTO users (user_id, username, password_hash, full_name, email, role, phone, school_id, first_login)
    VALUES (?, ?, ?, ?, ?, 'teacher', ?, ?, TRUE)
    RETURNING user_id
    """;

            try (PreparedStatement userStmt = conn.prepareStatement(userSql)) {
                userId = UUID.randomUUID(); // Generate UUID here
                userStmt.setObject(1, userId);
                userStmt.setString(2, username.toLowerCase());
                userStmt.setString(3, hashedPassword);
                userStmt.setString(4, fullName);
                userStmt.setString(5, email.toLowerCase());
                userStmt.setString(6, phone);
                userStmt.setObject(7, schoolId);

                ResultSet rs = userStmt.executeQuery();
                if (!rs.next()) {
                    conn.rollback();
                    return false;
                }
            }

            // Then, assign the teacher to the club
            String assignmentSql = """
    INSERT INTO club_teachers (club_id, teacher_id, school_id)
    VALUES (?, ?, ?)
    """;

            try (PreparedStatement assignmentStmt = conn.prepareStatement(assignmentSql)) {
                assignmentStmt.setObject(1, clubId);
                assignmentStmt.setObject(2, userId);
                assignmentStmt.setObject(3, schoolId);

                int rowsAffected = assignmentStmt.executeUpdate();
                if (rowsAffected == 0) {
                    conn.rollback();
                    return false;
                }
            }

            // Store the temporary password in database
            if (!PasswordService.storeTempPassword(conn, userId, tempPassword)) {
                conn.rollback();
                return false;
            }

            conn.commit();
            logger.info("Created teacher {} with temporary password", username);
            return true;

        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    logger.error("Error rolling back transaction: {}", ex.getMessage());
                }
            }
            logger.error("Error creating teacher with club assignment: {}", e.getMessage());
            return false;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    logger.error("Error closing connection: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Gets the temporary password for a recently created user by username
     * @return the temporary password or null if not found/expired
     */
    public static String getTempPasswordByUsername(String username, UUID schoolId) {
        // First, get the user ID for the username
        String sql = "SELECT user_id FROM users WHERE username = ? AND school_id = ?";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username.toLowerCase());
            stmt.setObject(2, schoolId);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                UUID userId = (UUID) rs.getObject("user_id");
                return PasswordService.getTempPassword(userId);
            }
        } catch (SQLException e) {
            logger.error("Error retrieving user ID for username {}: {}", username, e.getMessage());
        }

        return null;
    }

    /**
     * Clears the temporary password after it's been displayed
     */
    public static void clearTempPassword(String username, UUID schoolId) {
        String sql = "SELECT user_id FROM users WHERE username = ? AND school_id = ?";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username.toLowerCase());
            stmt.setObject(2, schoolId);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                UUID userId = (UUID) rs.getObject("user_id");
                PasswordService.clearTempPassword(userId);
            }
        } catch (SQLException e) {
            logger.error("Error clearing temp password for username {}: {}", username, e.getMessage());
        }
    }

    /**
     * Generates a secure temporary password
     * @return the generated temporary password
     */
    public static String generateTemporaryPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        SecureRandom random = new SecureRandom();
        StringBuilder password = new StringBuilder();

        for (int i = 0; i < 8; i++) {
            password.append(chars.charAt(random.nextInt(chars.length())));
        }

        return password.toString();
    }

    //prevents a teacher from being assigned multiple clubs
    public static boolean isTeacherAlreadyAssigned(UUID teacherId, UUID schoolId) {
        String sql = "SELECT COUNT(*) FROM club_teachers WHERE teacher_id = ? AND school_id = ?";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, teacherId);
            stmt.setObject(2, schoolId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            logger.error("Error checking teacher assignment", e);
        }
        return false;
    }

    public static boolean isCurrentUserActiveCoordinator() {
        UUID currentUserId = SessionManager.getCurrentUserId();
        UUID schoolId = SessionManager.getCurrentSchoolId();

        if (currentUserId == null || schoolId == null) {
            return false;
        }

        String sql = """
        SELECT is_active_coordinator 
        FROM users 
        WHERE user_id = ? 
        AND school_id = ? 
        AND role = 'club_coordinator'
        """;

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, currentUserId);
            stmt.setObject(2, schoolId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getBoolean("is_active_coordinator");
            }
        } catch (SQLException e) {
            logger.error("Error checking current user coordinator status", e);
        }
        return false;
    }

    public static boolean canPerformCoordinatorActions() {
        // Allow system administrators to bypass coordinator restrictions
        if ("system_administrator".equals(SessionManager.getCurrentUserRole())) {
            return true;
        }

        return isCurrentUserActiveCoordinator();
    }
}