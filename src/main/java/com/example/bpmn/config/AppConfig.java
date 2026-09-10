package com.example.bpmn.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AppConfig {
    private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);
    private static final Properties properties = new Properties();

    static {
        try (InputStream input = AppConfig.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (input != null) {
                properties.load(input);
            } else {
                logger.warn("application.properties not found in classpath.");
            }
        } catch (IOException ex) {
            logger.error("Failed to load application.properties", ex);
        }
    }

    public static String getProperty(String key) {
        return getProperty(key, null);
    }

    /**
     * Resolves a property, checking the environment first so PaaS platforms
     * (Render, etc.) can override application.properties without a rebuild.
     * "db.pool.maximum-pool-size" is looked up as env var "DB_POOL_MAXIMUM_POOL_SIZE".
     */
    public static String getProperty(String key, String defaultValue) {
        String envValue = System.getenv(toEnvVarName(key));
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        return properties.getProperty(key, defaultValue);
    }

    private static String toEnvVarName(String key) {
        return key.toUpperCase().replace('.', '_').replace('-', '_');
    }
}
