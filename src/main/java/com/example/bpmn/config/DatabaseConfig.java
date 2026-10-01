package com.example.bpmn.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.SQLException;

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

            String databaseUrl = System.getenv("DATABASE_URL");
            if (databaseUrl != null && !databaseUrl.isBlank()) {
                applyDatabaseUrl(config, databaseUrl);
            } else {
                config.setJdbcUrl(AppConfig.getProperty("db.url", "jdbc:postgresql://localhost:5432/los"));
                config.setUsername(AppConfig.getProperty("db.username", "postgres"));
                config.setPassword(AppConfig.getProperty("db.password", "postgres"));
            }
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
     * Neon/Render/Railway expose the connection info as a single URI-style
     * "DATABASE_URL" env var (postgresql://user:password@host:port/db?sslmode=require)
     * instead of separate jdbc.url/username/password properties. Convert it here so
     * the rest of the app keeps using the plain JDBC driver.
     */
    private static void applyDatabaseUrl(HikariConfig config, String databaseUrl) {
        try {
            URI uri = new URI(databaseUrl);
            String userInfo = uri.getUserInfo();
            String username = "";
            String password = "";
            if (userInfo != null) {
                String[] parts = userInfo.split(":", 2);
                username = parts[0];
                password = parts.length > 1 ? parts[1] : "";
            }

            String query = uri.getQuery();
            String jdbcUrl = "jdbc:postgresql://" + uri.getHost()
                    + (uri.getPort() > 0 ? ":" + uri.getPort() : "")
                    + uri.getPath()
                    + (query != null ? "?" + query : "?sslmode=require");

            config.setJdbcUrl(jdbcUrl);
            config.setUsername(username);
            config.setPassword(password);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid DATABASE_URL: " + databaseUrl, e);
        }
    }

    /**
     * Applies versioned migrations from classpath:db/migration via Flyway.
     * baselineOnMigrate/baselineVersion("0") lets this run against databases that already
     * have the tables from the old hand-rolled CREATE TABLE IF NOT EXISTS bootstrap (V1
     * baseline is recorded as already applied instead of being re-executed); a fresh
     * database runs V1 from scratch.
     */
    public static void initDatabase() {
        Flyway flyway = Flyway.configure()
                .dataSource(getDataSource())
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();
        logger.info("Database schema migrated via Flyway.");
    }

    public static synchronized void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Database connection pool closed.");
        }
    }
}
