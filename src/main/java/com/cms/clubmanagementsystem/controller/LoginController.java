package com.cms.clubmanagementsystem.controller;

import com.cms.clubmanagementsystem.utils.DatabaseConnector;
import com.cms.clubmanagementsystem.utils.SessionManager;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class LoginController {
    private static final Logger logger = LoggerFactory.getLogger(LoginController.class);

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField passwordVisibleField;
    @FXML private CheckBox showPasswordCheckBox;
    @FXML private Button loginButton;
    @FXML private Button registerButton;
    @FXML private Hyperlink forgotPasswordLink;
    @FXML private Label messageLabel;

    @FXML
    public void initialize() {
        passwordVisibleField.setVisible(false);
        passwordVisibleField.setManaged(false);
    }

    @FXML
    private void handleShowPassword(ActionEvent event) {
        boolean show = showPasswordCheckBox.isSelected();
        if (show) {
            passwordVisibleField.setText(passwordField.getText());
            passwordVisibleField.requestFocus();
        } else {
            passwordField.setText(passwordVisibleField.getText());
            passwordField.requestFocus();
        }
        togglePasswordVisibility(show);
    }

    private void togglePasswordVisibility(boolean show) {
        passwordField.setVisible(!show);
        passwordField.setManaged(!show);
        passwordVisibleField.setVisible(show);
        passwordVisibleField.setManaged(show);
    }

    @FXML
    private void handleLogin(ActionEvent event) {
        String usernameInput = usernameField.getText();
        String passwordInput = showPasswordCheckBox.isSelected()
                ? passwordVisibleField.getText()
                : passwordField.getText();

        if (usernameInput == null || passwordInput == null ||
                usernameInput.trim().isEmpty() || passwordInput.trim().isEmpty()) {
            messageLabel.setText("Please enter both username and password.");
            return;
        }

        final String username = usernameInput.trim().toLowerCase();
        final String password = passwordInput.trim();

        logger.debug("Login attempt for username='{}'", username);

        try (Connection conn = DatabaseConnector.getConnection()) {
            if (conn == null || conn.isClosed()) {
                logger.error("Database connection invalid during login");
                messageLabel.setText("Database connection error. Try again later.");
                return;
            }

            boolean prevAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try {
                // Allow RLS to reveal just this username for the auth step
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT set_config('app.login_username', ?, true)")) {
                    ps.setString(1, username);
                    ps.execute();
                }

                final String selectSql =
                        "SELECT user_id, password_hash, is_active, school_id, role " +
                                "FROM users WHERE username = ? LIMIT 1";

                UUID userId;
                String storedHash;
                boolean isActive;
                UUID schoolId;
                String role;

                try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                    ps.setString(1, username);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            logger.info("Login failed - username not found (or RLS blocked): {}", username);
                            messageLabel.setText("Invalid username or password.");
                            return;
                        }
                        userId = (UUID) rs.getObject("user_id");
                        storedHash = rs.getString("password_hash");
                        isActive = rs.getBoolean("is_active");
                        schoolId = (UUID) rs.getObject("school_id");
                        role = rs.getString("role");
                    }
                }

                if (!isActive) {
                    conn.rollback();
                    logger.info("Login attempt for inactive account: {}", username);
                    messageLabel.setText("Account is inactive. Contact administrator.");
                    return;
                }

                if (storedHash == null || storedHash.isBlank()) {
                    conn.rollback();
                    logger.error("Missing password hash for user: {}", username);
                    messageLabel.setText("System error during login. Please contact support.");
                    return;
                }

                boolean matches;
                try {
                    matches = BCrypt.checkpw(password, storedHash);
                } catch (IllegalArgumentException iae) {
                    conn.rollback();
                    logger.error("Stored password hash invalid for user {}: {}", username, iae.getMessage());
                    messageLabel.setText("System error during login. Please contact support.");
                    return;
                }

                if (!matches) {
                    conn.rollback();
                    logger.info("Invalid password attempt for username={}", username);
                    messageLabel.setText("Invalid username or password.");
                    return;
                }

                conn.commit();

                // Keep a session-scoped connection with tenant set
                Connection sessionConn = DatabaseConnector.getConnection();
                try {
                    if (schoolId == null) {
                        messageLabel.setText("No school assigned to this user.");
                        sessionConn.close();
                        return;
                    }
                    try (PreparedStatement setTenant = sessionConn.prepareStatement(
                            "SELECT set_config('app.current_school_id', ?, false)")) {
                        setTenant.setString(1, schoolId.toString());
                        setTenant.execute();
                    }
                } catch (SQLException e) {
                    try { sessionConn.close(); } catch (Exception ignore) {}
                    throw e;
                }

                SessionManager.createSession(userId, username, schoolId, sessionConn);

                logger.info("User '{}' logged in successfully (userId={}, schoolId={}, role={})",
                        username, userId, schoolId, role);

                // Go to dashboard
                switchScene(event, "/fxml/dashboard.fxml", "Club Management Dashboard");

            } catch (Exception ex) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                logger.error("Unexpected error during login for {}: {}", username, ex.getMessage(), ex);
                messageLabel.setText("Error during login. Try again later.");
            } finally {
                try { conn.setAutoCommit(prevAutoCommit); } catch (SQLException ignored) {}
            }

        } catch (SQLException sqlEx) {
            logger.error("Database error while attempting login for {}: {}", username, sqlEx.getMessage(), sqlEx);
            messageLabel.setText("Database error during login. Try again later.");
        }
    }

    @FXML
    private void handleRegister(ActionEvent event) {
        switchScene(event, "/fxml/register.fxml", "User Registration");
    }

    @FXML
    private void handleForgotPassword(ActionEvent event) {
        // Option 1: navigate to the in-app Forgot Password screen
        switchScene(event, "/fxml/forgot-password.fxml", "Forgot Password");
    }

    private void switchScene(ActionEvent event, String fxmlPath, String title) {
        try {
            URL url = getClass().getResource(fxmlPath);
            if (url == null) {
                throw new IOException("Cannot find FXML at " + fxmlPath);
            }
            Parent root = FXMLLoader.load(url);
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle(title);
            stage.show();
        } catch (IOException e) {
            logger.error("Error loading {}: {}", fxmlPath, e.getMessage(), e);
            messageLabel.setText("Error loading screen. Please try again.");
        }
    }
}
