package com.cms.clubmanagementsystem.utils;

import com.cms.clubmanagementsystem.service.ExcelSchoolServer;
import com.cms.clubmanagementsystem.service.LearnerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class ExcelServerTester {
    private static final Logger logger = LoggerFactory.getLogger(ExcelServerTester.class);

    public static void testExcelIntegration(UUID schoolId) {
        try {
            LearnerService learnerService = new LearnerService(schoolId);
            String excelPath = EnvLoader.get("EXCEL_FILE_PATH");
            String webhookUrl = EnvLoader.get("WEBHOOK_CALLBACK_URL", "http://localhost:8080/webhook");
            String apiKey = EnvLoader.get("WEBHOOK_API_KEY");

            ExcelSchoolServer server = new ExcelSchoolServer(excelPath, webhookUrl, apiKey, learnerService);

            // Test file existence
            logger.info("Excel file exists: {}", java.nio.file.Files.exists(java.nio.file.Paths.get(excelPath)));

            // Test database connection through learner service
            try {
                var learners = learnerService.getAllLearnersForSchool();
                logger.info("Current learners in DB: {}", learners.size());
            } catch (Exception e) {
                logger.error("Database connection test failed: {}", e.getMessage());
            }

            // Test manual processing
            server.forceReload();

        } catch (Exception e) {
            logger.error("Excel integration test failed: {}", e.getMessage(), e);
        }
    }
}