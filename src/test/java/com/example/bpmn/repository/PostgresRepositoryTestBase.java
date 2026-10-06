package com.example.bpmn.repository;

import com.example.bpmn.config.AppConfig;
import com.example.bpmn.config.DatabaseConfig;
import com.example.bpmn.model.BpmnProcess;
import com.example.bpmn.model.DmnDecision;
import com.example.bpmn.model.ProcessInstance;
import com.example.bpmn.model.User;
import com.example.bpmn.repository.impl.PostgresBpmnProcessRepository;
import com.example.bpmn.repository.impl.PostgresDmnDecisionRepository;
import com.example.bpmn.repository.impl.PostgresProcessInstanceRepository;
import com.example.bpmn.repository.impl.PostgresUserRepository;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Base class for the repository integration tests: they exercise the hand-written JDBC/SQL in
 * {@code com.example.bpmn.repository.impl} against a real PostgreSQL, which the in-memory fake
 * repositories used by the service tests cannot cover.
 * <p>
 * The connection details are the application's own ({@code db.url}/{@code db.username}/
 * {@code db.password} in application.properties, overridable through the {@code DB_URL},
 * {@code DB_USERNAME} and {@code DB_PASSWORD} env vars - see {@link AppConfig}), except that the
 * database name is swapped for {@value #TEST_DATABASE}: the tests truncate every table before
 * each case, so they must never run against a real database. The throwaway database is created
 * on first use and its schema is built by the production Flyway migrations, so a migration that
 * no longer applies to an empty database fails the suite here.
 * <p>
 * When no PostgreSQL is reachable the whole class is skipped (JUnit assumption) instead of
 * failing, so {@code mvn test} still works on a machine without a local database.
 */
abstract class PostgresRepositoryTestBase {

    /** Dedicated throwaway database - never a database the application itself uses. */
    private static final String TEST_DATABASE = "bpmn_repo_test";

    /** Maintenance database used only to issue {@code CREATE DATABASE}. */
    private static final String MAINTENANCE_DATABASE = "postgres";

    /**
     * Every table the migrations create, except Flyway's own history table. Listed explicitly
     * (rather than discovered) so a future table is a deliberate addition here.
     */
    private static final String TRUNCATE_ALL = """
            TRUNCATE TABLE tasks, process_instance_timers, process_instances,
                           bpmn_process_start_timers, bpmn_process_versions, dmn_decision_versions,
                           refresh_tokens, bpmn_processes, dmn_decision, users CASCADE
            """;

    /**
     * Shared base timestamp, truncated to milliseconds: PostgreSQL {@code TIMESTAMP} keeps
     * microseconds, so a raw {@code LocalDateTime.now()} would not survive a round trip intact.
     */
    protected static final LocalDateTime NOW = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);

    private static boolean initialised;
    private static boolean available;
    private static String skipReason;

    @BeforeAll
    static void prepareTestDatabase() {
        initialiseOnce();
        assumeTrue(available, skipReason);
    }

    @BeforeEach
    void truncateAllTables() throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(TRUNCATE_ALL);
        }
    }

    private static synchronized void initialiseOnce() {
        if (initialised) {
            return;
        }
        initialised = true;

        HikariDataSource dataSource = null;
        try {
            createTestDatabaseIfMissing();
            dataSource = pool(databaseUrl(TEST_DATABASE));
            DatabaseConfig.useDataSource(dataSource);
            DatabaseConfig.initDatabase();
            available = true;
        } catch (Exception e) {
            if (dataSource != null) {
                dataSource.close();
            }
            DatabaseConfig.close();
            skipReason = "PostgreSQL not available for repository integration tests ("
                    + databaseUrl(TEST_DATABASE) + "): " + e.getMessage();
        }
    }

    private static void createTestDatabaseIfMissing() throws SQLException {
        try (Connection conn = DriverManager.getConnection(
                databaseUrl(MAINTENANCE_DATABASE), username(), password())) {

            try (PreparedStatement stmt = conn.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?")) {
                stmt.setString(1, TEST_DATABASE);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return;
                    }
                }
            }
            // CREATE DATABASE cannot be parameterised; TEST_DATABASE is a constant, not input.
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE DATABASE \"" + TEST_DATABASE + "\"");
            }
        }
    }

    private static HikariDataSource pool(String jdbcUrl) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username());
        config.setPassword(password());
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(4);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(5_000);
        config.setInitializationFailTimeout(5_000);
        return new HikariDataSource(config);
    }

    /** The configured JDBC url with its database name replaced, preserving any query string. */
    private static String databaseUrl(String database) {
        String configured = AppConfig.getProperty("db.url", "jdbc:postgresql://localhost:5432/postgres");
        int queryStart = configured.indexOf('?');
        String base = queryStart >= 0 ? configured.substring(0, queryStart) : configured;
        String query = queryStart >= 0 ? configured.substring(queryStart) : "";
        return base.substring(0, base.lastIndexOf('/') + 1) + database + query;
    }

    private static String username() {
        return AppConfig.getProperty("db.username", "postgres");
    }

    private static String password() {
        return AppConfig.getProperty("db.password", "postgres");
    }

    // --- Fixtures for rows other tables reference through a foreign key ---------------------

    protected static BpmnProcess insertProcess(String id) {
        BpmnProcess process = new BpmnProcess(id, "key-" + id, "Process " + id, 1, "<definitions/>", "ACTIVE");
        process.setCreatedAt(NOW);
        process.setUpdatedAt(NOW);
        return new PostgresBpmnProcessRepository().save(process);
    }

    protected static DmnDecision insertDecision(String id) {
        DmnDecision decision = new DmnDecision(id, "key-" + id, "Decision " + id, 1, "<definitions/>", "ACTIVE");
        decision.setCreatedAt(NOW);
        decision.setUpdatedAt(NOW);
        return new PostgresDmnDecisionRepository().save(decision);
    }

    protected static ProcessInstance insertInstance(String id, String processId) {
        ProcessInstance instance = new ProcessInstance();
        instance.setId(id);
        instance.setProcessId(processId);
        instance.setProcessVersion(1);
        instance.setStatus("RUNNING");
        instance.setCurrentNodeId("task1");
        instance.setVariables(Map.of());
        instance.setPendingJoinArrivals(Set.of());
        instance.setStartedBy("tester");
        instance.setStartedAt(NOW);
        instance.setCreatedAt(NOW);
        instance.setUpdatedAt(NOW);
        return new PostgresProcessInstanceRepository().save(instance);
    }

    protected static User insertUser(String id) {
        User user = new User(id, "user-" + id, id + "@example.com", "User " + id, "USER", "ACTIVE");
        user.setPasswordHash("hash-" + id);
        user.setCreatedAt(NOW);
        user.setUpdatedAt(NOW);
        return new PostgresUserRepository().save(user);
    }
}
