package com.cms.clubmanagementsystem.utils;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Properties;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;


public class DBConfig {
    private static final Logger logger = LoggerFactory.getLogger(DBConfig.class);
    private static final String CONFIG_PATH = "config/config.properties";

    public static Properties load() throws IOException {
        logger.debug("Attempting to load configuration from: {}", CONFIG_PATH);

        // Method 1: Try classloader resource
        InputStream input = DBConfig.class.getClassLoader().getResourceAsStream(CONFIG_PATH);
        if (input != null) {
            logger.debug("Found config file via classloader");
            return loadProperties(input);
        }

        // Method 2: Try absolute path (for debugging)
        Path absPath = Paths.get("src/main/resources", CONFIG_PATH);
        if (Files.exists(absPath)) {
            logger.debug("Found config file at absolute path: {}", absPath);
            return loadProperties(Files.newInputStream(absPath));
        }

        // Method 3: Try alternative locations
        Path[] altPaths = {
                Paths.get("resources", CONFIG_PATH),
                Paths.get(CONFIG_PATH)
        };

        for (Path path : altPaths) {
            if (Files.exists(path)) {
                logger.debug("Found config file at alternative path: {}", path);
                return loadProperties(Files.newInputStream(path));
            }
        }

        throw new FileNotFoundException("Configuration file not found at any of: \n" +
                "- Classpath: " + CONFIG_PATH + "\n" +
                "- Absolute: " + absPath + "\n" +
                "- Alternative locations: " + Arrays.toString(altPaths));
    }

    private static Properties loadProperties(InputStream input) throws IOException {
        Properties props = new Properties();
        props.load(input);
        logger.debug("Successfully loaded {} properties", props.size());
        return props;
    }
}