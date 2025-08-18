package com.cms.clubmanagementsystem.service;

import io.github.cdimascio.dotenv.Dotenv;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.util.Properties;

public class EmailService {

    private final String smtpHost;
    private final int smtpPort;
    private final String smtpUser;
    private final String smtpPassword;
    private final boolean smtpAuth;
    private final boolean smtpStartTls;
    private final String smtpFrom;

    public EmailService() {
        Dotenv dotenv = Dotenv.load();

        smtpHost = dotenv.get("SMTP_HOST");
        smtpPort = parseIntOrDefault(dotenv.get("SMTP_PORT"), 587);
        smtpUser = dotenv.get("SMTP_USERNAME");
        smtpPassword = dotenv.get("SMTP_PASSWORD");
        smtpAuth = Boolean.parseBoolean(dotenv.get("SMTP_AUTH", "true"));
        smtpStartTls = Boolean.parseBoolean(dotenv.get("SMTP_STARTTLS", "true"));
        smtpFrom = dotenv.get("SMTP_FROM", smtpUser);

        if (smtpUser == null || smtpUser.isEmpty() || smtpPassword == null || smtpPassword.isEmpty()) {
            throw new IllegalStateException("SMTP credentials are missing.");
        }

        System.out.println("SMTP Host loaded: " + smtpHost + ", Port: " + smtpPort);
    }

    public void sendEmail(String to, String subject, String body) throws MessagingException {
        Properties props = new Properties();
        props.put("mail.smtp.auth", String.valueOf(smtpAuth));
        props.put("mail.smtp.starttls.enable", String.valueOf(smtpStartTls));
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", String.valueOf(smtpPort));

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(smtpUser, smtpPassword);
            }
        });

        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(smtpFrom));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
        message.setSubject(subject);
        message.setText(body);

        Transport.send(message);
    }

    private int parseIntOrDefault(String value, int defaultVal) {
        try {
            return (value != null) ? Integer.parseInt(value) : defaultVal;
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }
}
