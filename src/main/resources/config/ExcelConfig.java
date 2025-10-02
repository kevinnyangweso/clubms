package com.cms.clubmanagementsystem.config;

import java.nio.file.Path;
import java.nio.file.Paths;

public class ExcelConfig {
    private final Path filePath;
    private final int pollIntervalSeconds;
    private final boolean enableFileWatcher;

    public ExcelConfig(String filePath, int pollIntervalSeconds, boolean enableFileWatcher) {
        this.filePath = Paths.get(filePath);
        this.pollIntervalSeconds = pollIntervalSeconds;
        this.enableFileWatcher = enableFileWatcher;
    }

    public Path getFilePath() { return filePath; }
    public int getPollIntervalSeconds() { return pollIntervalSeconds; }
    public boolean isEnableFileWatcher() { return enableFileWatcher; }

    public static ExcelConfig fromEnvironment() {
        String filePath = System.getenv().getOrDefault("EXCEL_FILE_PATH",
                "C:\\Users\\user\\Desktop\\learners.xlsx");
        int pollInterval = Integer.parseInt(System.getenv().getOrDefault("EXCEL_POLL_INTERVAL", "5"));
        boolean enableWatcher = Boolean.parseBoolean(System.getenv().getOrDefault("EXCEL_ENABLE_WATCHER", "true"));

        return new ExcelConfig(filePath, pollInterval, enableWatcher);
    }
}