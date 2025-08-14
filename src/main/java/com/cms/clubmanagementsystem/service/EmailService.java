package com.cms.clubmanagementsystem.service;

import io.github.cdimascio.dotenv.Dotenv;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.util.Properties;

public class EmailService {

    private final String fromEmail;
    private final String password;
    private final String host;
    private final int port;

    public EmailService() {
        Dotenv dotenv = Dotenv.load(); // loads from .env
        this.fromEmail = dotenv.get("SMTP_USERNAME");
        this.password = dotenv.get("SMTP_PASSWORD");
        this.host = dotenv.get("SMTP_HOST", "smtp.gmail.com"); // default if missing
        this.port = Integer.parseInt(dotenv.get("SMTP_PORT", "587")); // default if missing
    }

    public void sendPasswordResetEmail(String toEmail, String resetLink) throws MessagingException {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));

        Session session = Session.getInstance(props, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(fromEmail, password);
            }
        });

        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(fromEmail));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
        message.setSubject("Password Reset Request");
        message.setText(
                "Hello,\n\nClick the link below to reset your password:\n" + resetLink +
                        "\n\nIf you did not request a password reset, please ignore this email."
        );

        Transport.send(message);
    }
}
