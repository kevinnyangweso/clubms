module com.cms.clubmanagementsystem {
    // JavaFX
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;

    // JDBC & HikariCP
    requires java.sql;
    requires com.zaxxer.hikari;
    requires org.postgresql.jdbc;

    // BCrypt
    requires jbcrypt;

    // Logging
    requires org.slf4j;
    requires ch.qos.logback.classic;
    requires ch.qos.logback.core;
    requires libphonenumber;
    requires jakarta.mail;
    requires jakarta.activation;
    requires io.github.cdimascio.dotenv.java;

    // Export your main package(s)
    exports com.cms.clubmanagementsystem;
    exports com.cms.clubmanagementsystem.controller;
    exports com.cms.clubmanagementsystem.utils;
    exports com.cms.clubmanagementsystem.dao;
    exports com.cms.clubmanagementsystem.model;

    // Open packages to JavaFX for reflection (FXML loading)
    opens com.cms.clubmanagementsystem to javafx.fxml;
    opens com.cms.clubmanagementsystem.controller to javafx.fxml;
    exports com.cms.clubmanagementsystem.service;
    opens com.cms.clubmanagementsystem.service to javafx.fxml;
}
