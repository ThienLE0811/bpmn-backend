package com.example.bpmn.repository.impl;

import com.example.bpmn.config.DatabaseConfig;
import com.example.bpmn.model.ProcessInstance;
import com.example.bpmn.repository.ProcessInstanceRepository;
import com.example.bpmn.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class PostgresProcessInstanceRepository implements ProcessInstanceRepository {
    private static final Logger logger = LoggerFactory.getLogger(PostgresProcessInstanceRepository.class);

    @Override
    public ProcessInstance save(ProcessInstance instance) {
        String sql = """
            INSERT INTO process_instances
                (id, process_id, process_version, status, current_node_id, variables, pending_join_arrivals, started_by, started_at, completed_at, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE
            SET status = EXCLUDED.status,
                current_node_id = EXCLUDED.current_node_id,
                variables = EXCLUDED.variables,
                pending_join_arrivals = EXCLUDED.pending_join_arrivals,
                completed_at = EXCLUDED.completed_at,
                updated_at = EXCLUDED.updated_at
        """;

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, instance.getId());
            stmt.setString(2, instance.getProcessId());
            stmt.setInt(3, instance.getProcessVersion());
            stmt.setString(4, instance.getStatus());
            stmt.setString(5, instance.getCurrentNodeId());
            stmt.setString(6, instance.getVariables() != null ? JsonUtil.toJson(instance.getVariables()) : null);
            stmt.setString(7, instance.getPendingJoinArrivals() != null ? JsonUtil.toJson(instance.getPendingJoinArrivals()) : null);
            stmt.setString(8, instance.getStartedBy());
            stmt.setTimestamp(9, instance.getStartedAt() != null ? Timestamp.valueOf(instance.getStartedAt()) : null);
            stmt.setTimestamp(10, instance.getCompletedAt() != null ? Timestamp.valueOf(instance.getCompletedAt()) : null);
            stmt.setTimestamp(11, instance.getCreatedAt() != null ? Timestamp.valueOf(instance.getCreatedAt()) : Timestamp.valueOf(LocalDateTime.now()));
            stmt.setTimestamp(12, instance.getUpdatedAt() != null ? Timestamp.valueOf(instance.getUpdatedAt()) : Timestamp.valueOf(LocalDateTime.now()));

            stmt.executeUpdate();
            return instance;
        } catch (SQLException e) {
            logger.error("Failed to save process_instance id={}: {}", instance.getId(), e.getMessage(), e);
            throw new RuntimeException("Database error saving process_instance", e);
        }
    }

    @Override
    public Optional<ProcessInstance> findById(String id) {
        String sql = "SELECT * FROM process_instances WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            logger.error("Failed to find process_instance id={}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Database error finding process_instance", e);
        }
    }

    @Override
    public List<ProcessInstance> findAll() {
        String sql = "SELECT * FROM process_instances ORDER BY created_at DESC";
        List<ProcessInstance> list = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                list.add(mapRow(rs));
            }
            return list;
        } catch (SQLException e) {
            logger.error("Failed to fetch all process_instances: {}", e.getMessage(), e);
            throw new RuntimeException("Database error fetching process_instances", e);
        }
    }

    @Override
    public List<ProcessInstance> findPage(int limit, int offset) {
        String sql = "SELECT * FROM process_instances ORDER BY created_at DESC LIMIT ? OFFSET ?";
        List<ProcessInstance> list = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, limit);
            stmt.setInt(2, offset);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            logger.error("Failed to fetch process_instances page: {}", e.getMessage(), e);
            throw new RuntimeException("Database error fetching process_instances page", e);
        }
    }

    @Override
    public long count() {
        String sql = "SELECT COUNT(*) FROM process_instances";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            logger.error("Failed to count process_instances: {}", e.getMessage(), e);
            throw new RuntimeException("Database error counting process_instances", e);
        }
    }

    @Override
    public boolean deleteById(String id) {
        String sql = "DELETE FROM process_instances WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, id);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Failed to delete process_instance id={}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Database error deleting process_instance", e);
        }
    }

    @SuppressWarnings("unchecked")
    private ProcessInstance mapRow(ResultSet rs) throws SQLException {
        ProcessInstance instance = new ProcessInstance();
        instance.setId(rs.getString("id"));
        instance.setProcessId(rs.getString("process_id"));
        instance.setProcessVersion(rs.getInt("process_version"));
        instance.setStatus(rs.getString("status"));
        instance.setCurrentNodeId(rs.getString("current_node_id"));

        String variablesJson = rs.getString("variables");
        instance.setVariables(variablesJson != null ? JsonUtil.fromJson(variablesJson, Map.class) : Map.of());

        String pendingJoinArrivalsJson = rs.getString("pending_join_arrivals");
        instance.setPendingJoinArrivals(pendingJoinArrivalsJson != null
                ? JsonUtil.fromJson(pendingJoinArrivalsJson, Set.class) : Set.of());

        instance.setStartedBy(rs.getString("started_by"));

        Timestamp startedAtTs = rs.getTimestamp("started_at");
        if (startedAtTs != null) {
            instance.setStartedAt(startedAtTs.toLocalDateTime());
        }
        Timestamp completedAtTs = rs.getTimestamp("completed_at");
        if (completedAtTs != null) {
            instance.setCompletedAt(completedAtTs.toLocalDateTime());
        }
        Timestamp createdAtTs = rs.getTimestamp("created_at");
        if (createdAtTs != null) {
            instance.setCreatedAt(createdAtTs.toLocalDateTime());
        }
        Timestamp updatedAtTs = rs.getTimestamp("updated_at");
        if (updatedAtTs != null) {
            instance.setUpdatedAt(updatedAtTs.toLocalDateTime());
        }

        return instance;
    }
}
