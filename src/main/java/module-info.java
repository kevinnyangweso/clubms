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
    requires java.desktop;
    requires com.google.gson;
    requires spark.core;
    requires java.net.http;
    requires java.prefs;
    requires org.apache.poi.poi;
    requires org.apache.poi.ooxml;
    requires layout;
    requires kernel;
    requires io;

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

    opens css to javafx.fxml;
    opens fxml to javafx.fxml;
    opens com.cms.clubmanagementsystem.utils to javafx.fxml;
}
