package com.example.bpmn.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseConfig {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseConfig.class);
    private static HikariDataSource dataSource;

    private DatabaseConfig() {
    }

    public static synchronized DataSource getDataSource() {
        if (dataSource == null) {
            initDataSource();
        }
        return dataSource;
    }

    public static Connection getConnection() throws SQLException {
        return getDataSource().getConnection();
    }

    private static void initDataSource() {
        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(AppConfig.getProperty("db.url", "jdbc:postgresql://localhost:5432/los"));
            config.setUsername(AppConfig.getProperty("db.username", "postgres"));
            config.setPassword(AppConfig.getProperty("db.password", "123@123"));
            config.setDriverClassName(AppConfig.getProperty("db.driver-class-name", "org.postgresql.Driver"));

            int maxPoolSize = Integer.parseInt(AppConfig.getProperty("db.pool.maximum-pool-size", "10"));
            int minIdle = Integer.parseInt(AppConfig.getProperty("db.pool.minimum-idle", "2"));
            long idleTimeout = Long.parseLong(AppConfig.getProperty("db.pool.idle-timeout", "30000"));
            long connectionTimeout = Long.parseLong(AppConfig.getProperty("db.pool.connection-timeout", "10000"));

            config.setMaximumPoolSize(maxPoolSize);
            config.setMinimumIdle(minIdle);
            config.setIdleTimeout(idleTimeout);
            config.setConnectionTimeout(connectionTimeout);

            dataSource = new HikariDataSource(config);
            logger.info("Database connection pool initialized successfully: {}", config.getJdbcUrl());
        } catch (Exception e) {
            logger.error("Failed to initialize database connection pool", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    /**
     * Auto create necessary tables if they do not exist.
     */
    public static void initDatabase() {
        String sql = """
            CREATE TABLE IF NOT EXISTS users (
                id VARCHAR(100) PRIMARY KEY,
                username VARCHAR(100) UNIQUE NOT NULL,
                email VARCHAR(255) UNIQUE NOT NULL,
                full_name VARCHAR(255),
                role VARCHAR(50),
                status VARCHAR(50),
                created_at TIMESTAMP,
                updated_at TIMESTAMP
            );

            CREATE TABLE IF NOT EXISTS bpmn_processes (
                id VARCHAR(100) PRIMARY KEY,
                process_key VARCHAR(100) NOT NULL,
                name VARCHAR(255) NOT NULL,
                version INT DEFAULT 1,
                bpmn_xml TEXT,
                status VARCHAR(50),
                created_at TIMESTAMP,
                updated_at TIMESTAMP
            );

            CREATE TABLE IF NOT EXISTS workflows (
                id VARCHAR(100) PRIMARY KEY,
                name VARCHAR(255) NOT NULL,
                description TEXT,
                status VARCHAR(50),
                created_at TIMESTAMP,
                updated_at TIMESTAMP
            );

            CREATE TABLE IF NOT EXISTS tasks (
                id VARCHAR(100) PRIMARY KEY,
                process_id VARCHAR(100),
                name VARCHAR(255) NOT NULL,
                description TEXT,
                assignee_id VARCHAR(100),
                status VARCHAR(50),
                due_date TIMESTAMP,
                created_at TIMESTAMP,
                updated_at TIMESTAMP
            );
        """;

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            logger.info("Database schema initialized. Tables (users, bpmn_processes, workflows, tasks) are ready.");
        } catch (SQLException e) {
            logger.error("Failed to initialize database schema: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize database schema", e);
        }
    }

    public static synchronized void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Database connection pool closed.");
        }
    }
}
