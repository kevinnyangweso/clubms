package com.cms.clubmanagementsystem.controller;
import com.cms.clubmanagementsystem.utils.DatabaseConnector;
import com.cms.clubmanagementsystem.utils.SessionManager;
import jakarta.mail.MessagingException;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node; import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.net.URL;
import java.sql.*;
import java.util.Optional;
import java.util.UUID;

public class LoginController {
    private static final Logger logger =
            LoggerFactory.getLogger(LoginController.class);

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
        // Initially hide the visible password field
        passwordVisibleField.setVisible(false);
        passwordVisibleField.setManaged(false);
    }

    @FXML private void handleShowPassword(ActionEvent event) {
        boolean show = showPasswordCheckBox.isSelected();

        if (show) { // Copy password to visible field and show it
            passwordVisibleField.setText(passwordField.getText());
            passwordVisibleField.requestFocus(); }
        else { // Copy visible password back to password field
            passwordField.setText(passwordVisibleField.getText());
            passwordField.requestFocus(); }

        // Toggle visibility
        togglePasswordVisibility(show);

    }

    private void togglePasswordVisibility(boolean show) {
        passwordField.setVisible(!show);
        passwordField.setManaged(!show);
        passwordVisibleField.setVisible(show);
        passwordVisibleField.setManaged(show);
    }

    @FXML private void handleLogin(ActionEvent event) {
        String usernameInput = usernameField.getText();
        String passwordInput = showPasswordCheckBox.isSelected() ?
                passwordVisibleField.getText() : passwordField.getText();

        if (usernameInput == null || passwordInput == null ||
                usernameInput.trim().isEmpty() || passwordInput.trim().isEmpty()) {
            messageLabel.setText("Please enter both username and password.");
            return;

        }

        final String username = usernameInput.trim().toLowerCase();
        final String password = passwordInput.trim(); logger.debug("Login attempt for " +
                "username='{}'", username);

        // We'll use a dedicated connection for the authentication transaction.
        try (Connection conn = DatabaseConnector.getConnection()) {
            if (conn == null || conn.isClosed()) {
                logger.error("Database connection invalid during login");
                messageLabel.setText("Database connection error. Try again later.");
                return;

            }

            boolean prevAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            // start transaction
            try {
                // 1) Set transaction-local variable to allow RLS to
                // reveal this username row
                try (PreparedStatement ps = conn.prepareStatement("SELECT " +
                        "set_config('app.login_username', ?, true)"))
                {
                    ps.setString(1, username); ps.execute();
                }
                // 2) Fetch the user row (RLS will allow it because of the login
                // variable)
                final String selectSql = "SELECT user_id, password_hash, is_active, " +
                        "school_id, role " + "FROM users WHERE username = ? LIMIT 1";
                UUID userId;
                String storedHash;
                boolean isActive;
                UUID schoolId;
                String role;
                try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                    ps.setString(1, username);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback(); logger.info("Login failed - username " +
                                    "not found (or RLS blocked): {}", username);
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

                if (!isActive) { conn.rollback(); logger.info("Login attempt for " +
                        "inactive account: {}", username);
                    messageLabel.setText("Account is inactive. Contact " +
                            "administrator.");
                    return;

                } if (storedHash == null || storedHash.isBlank()) {
                    conn.rollback();
                    logger.error("Missing password hash for user: {}", username);
                    messageLabel.setText("System error during login. Please contact support.");
                        return;
                }
                // 3) Verify password securely (BCrypt)
                boolean matches;
                try {
                    matches = BCrypt.checkpw(password, storedHash);
                }
                catch (IllegalArgumentException iae) { conn.rollback(); logger.error("Stored " +
                                "password hash invalid for user {}: {}",
                        username, iae.getMessage());
                    messageLabel.setText("System error during login. Please contact " +
                            "support."); return;
                } if (!matches) { conn.rollback();
                    logger.info("Invalid password attempt for username={}",
                            username); messageLabel.setText("Invalid username or " +
                            "password.");
                            return;
                }
                // Authentication successful: commit the transaction (login variable removed)
                conn.commit();

                // 4) Now get a dedicated connection to keep session-level tenant for
                // this GUI session. // We obtain a new connection from the pool and
                // set its session tenant variable.
                Connection sessionConn = DatabaseConnector.getConnection();
                try {
                    if (schoolId == null) { messageLabel.setText("No school" +
                            " assigned to this user."); sessionConn.close();
                            return;
                    } try (PreparedStatement setTenant = sessionConn.prepareStatement(
                            "SELECT set_config('app.current_school_id', ?, " +
                                    "false)"))
                    {
                        setTenant.setString(1, schoolId.toString());
                        setTenant.execute(); }
                }
                catch (SQLException e)
                { // ensure sessionConn is closed on failure
                    try {
                        sessionConn.close();
                    } catch (Exception ex)
                    { logger.warn("Failed to close sessionConn after set tenant " +
                            "error");
                    }
                    throw e;
                }

                // 5) Register session (keeps the connection open for the logged-in
                // UI)
                SessionManager.createSession(userId, username, schoolId, sessionConn);
                logger.info("User '{}' logged in successfully (userId={}, schoolId={}, " +
                        "role={})", username, userId, schoolId, role);

                // 6) Load dashboard UI on JavaFX thread (same as before)
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/dashboard.fxml"));
                Parent root = loader.load();
                Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
                stage.setScene(new Scene(root));
                stage.setTitle("Club Management Dashboard"); stage.show();
            }
            catch (Exception ex) { try { conn.rollback();
            }
            catch (SQLException ignored) {} logger.error("Unexpected error during login for" +
                    " {}: {}", username, ex.getMessage(), ex);
                messageLabel.setText("Error during login. Try again later.");
            }
            finally
            {
                try
            { conn.setAutoCommit(prevAutoCommit);

            } catch (SQLException ignored) {}

            } } catch (SQLException sqlEx)

        {
            logger.error("Database error while attempting login for {}: {}",
                username, sqlEx.getMessage(), sqlEx);
            messageLabel.setText("Database error during login. Try again later.");
        }
    }

    @FXML private void handleRegister(ActionEvent event) {
        try {
            URL url = getClass().getResource("/fxml/register.fxml");
            if (url == null) throw new IOException("Cannot find register.fxml in /fxml/");
            Parent root = FXMLLoader.load(url);
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("User Registration");
            stage.show();
        } catch (IOException e)
        { logger.error("Error loading registration form: {}", e.getMessage(), e);
            messageLabel.setText("Error loading registration form.");
        }
    }

    @FXML private void handleForgotPassword(ActionEvent event) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Forgot Password");
        dialog.setHeaderText("Enter your registered email");
        dialog.setContentText("Email:");
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(email -> {
            if (!isValidEmail(email))
            { showPasswordResetAlert("Invalid Email", "Please enter a " +
                    "valid email address."); return;
            }
            try
            { if (sendPasswordResetEmail(email))
            { showPasswordResetAlert("Success", "If this email is registered, " +
                    "you'll receive a reset link shortly.");
            } else { // Don't reveal if email exists (security best practice)
                showPasswordResetAlert("Success", "If this email is registered, you'll " +
                        "receive a reset link shortly.");
            }
            } catch (SQLException e)
            { logger.error("Error sending reset email: {}", e.getMessage(), e);
                showPasswordResetAlert("Error", "A system error occurred. Please try " +
                        "again later.");
            } catch (RuntimeException e)
            { logger.error("Email sending failed: {}", e.getMessage());
                showPasswordResetAlert("Error", "Failed to send reset email. Please contact " +
                        "support."); } });

    } // Method to show password reset related alerts

    private void showPasswordResetAlert(String alertType, String message) {
        Alert.AlertType alertLevel = switch (alertType.toUpperCase()) {
            case "INVALID EMAIL", "WARNING" -> Alert.AlertType.WARNING;
            case "ERROR" -> Alert.AlertType.ERROR; default -> Alert.AlertType.INFORMATION; };
        // Map your string types to AlertType
        Alert alert = new Alert(alertLevel);
        alert.setTitle(alertType + " - Password Reset");
        alert.setHeaderText(null);
        // Simpler header
        alert.setContentText(message);
        alert.showAndWait(); }
    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$"); }
    private boolean sendPasswordResetEmail(String email) throws SQLException {
        System.out.println("📩 sendPasswordResetEmail() called with email: " + email);
        try (Connection conn = DatabaseConnector.getConnection()) {
            if (conn == null || conn.isClosed()) {
                System.out.println("❌ Database connection unavailable"); return false; }
            boolean prevAutoCommit = conn.getAutoCommit(); conn.setAutoCommit(false);
            // optional: if you want transactional safety
            try {
                // 1) Enable reset password mode for this session to bypass normal RLS
                try (PreparedStatement ps = conn.prepareStatement( "SELECT " +
                        "set_config('app.reset_password_mode', 'true', true)")) {
                    ps.execute(); }
                // 2) Lookup user by email (case-insensitive)
                String sql = "SELECT user_id, username FROM users WHERE LOWER(email) = " +
                        "LOWER(?)";
                UUID userId;
                String username;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, email.trim());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            System.out.println("⚠ No user found with email: " + email);
                            conn.commit();
                            // commit to clear session variable too
                            return false; }
                        userId = (UUID) rs.getObject("user_id");
                        username = rs.getString("username"); } }
                // 3) Clear the reset_password_mode variable to prevent misuse
                try (PreparedStatement ps = conn.prepareStatement( "SELECT " +
                        "set_config('app.reset_password_mode', '', true)")) {
                    ps.execute(); }

                // 4) Generate token and update user record as usual
                String resetToken = UUID.randomUUID().toString();
                Timestamp expiryTime = new Timestamp(System.currentTimeMillis() + 3600000); // 1 hr
                String updateSql = "UPDATE users SET reset_token = ?, reset_token_expiry = ? " +
                        "WHERE user_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setString(1, resetToken);
                    ps.setTimestamp(2, expiryTime);
                    ps.setObject(3, userId); ps.executeUpdate(); }

                // 5) Send email with reset link (implement this)
                String resetLink = "https://clubms.school.com/reset-password?token=" +
                        resetToken + "&username=" + username;

                new com.cms.clubmanagementsystem.service.EmailService().sendPasswordResetEmail(email, resetLink);
                conn.commit(); System.out.println("✅ Password reset email sent successfully.");
                return true; }
            catch (SQLException | RuntimeException ex) { conn.rollback();
                System.err.println("❌ Error during password reset email process: " + ex.getMessage());
                throw ex; // rethrow or handle
                 } catch (MessagingException e) {
                throw new RuntimeException(e); }
            finally {
                try {
                    // Ensure reset_password_mode is cleared even on exceptions
                    try (PreparedStatement ps = conn.prepareStatement( "SELECT " +
                            "set_config('app.reset_password_mode', '', true)")) {
                        ps.execute(); }
                    conn.setAutoCommit(prevAutoCommit); }
                catch (SQLException ignore) {} } } }

    private void sendEmail(String to, String subject, String body) {
        // TODO: Implement email sending (SMTP, SendGrid, etc.)
        logger.info("Simulating email to {}: {} - {}", to, subject, body); } }