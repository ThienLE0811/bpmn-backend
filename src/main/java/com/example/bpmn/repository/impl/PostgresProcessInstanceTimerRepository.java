package com.example.bpmn.repository.impl;

import com.example.bpmn.config.DatabaseConfig;
import com.example.bpmn.model.ProcessInstanceTimer;
import com.example.bpmn.repository.ProcessInstanceTimerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PostgresProcessInstanceTimerRepository implements ProcessInstanceTimerRepository {
    private static final Logger logger = LoggerFactory.getLogger(PostgresProcessInstanceTimerRepository.class);

    @Override
    public ProcessInstanceTimer save(ProcessInstanceTimer timer) {
        String sql = """
            INSERT INTO process_instance_timers (id, process_instance_id, node_id, due_date, created_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE
            SET due_date = EXCLUDED.due_date
        """;

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, timer.getId());
            stmt.setString(2, timer.getProcessInstanceId());
            stmt.setString(3, timer.getNodeId());
            stmt.setTimestamp(4, Timestamp.valueOf(timer.getDueDate()));
            stmt.setTimestamp(5, timer.getCreatedAt() != null
                    ? Timestamp.valueOf(timer.getCreatedAt()) : Timestamp.valueOf(LocalDateTime.now()));

            stmt.executeUpdate();
            return timer;
        } catch (SQLException e) {
            logger.error("Failed to save process_instance_timer id={}: {}", timer.getId(), e.getMessage(), e);
            throw new RuntimeException("Database error saving process_instance_timer", e);
        }
    }

    @Override
    public Optional<ProcessInstanceTimer> findById(String id) {
        String sql = "SELECT * FROM process_instance_timers WHERE id = ?";

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
            logger.error("Failed to find process_instance_timer id={}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Database error finding process_instance_timer", e);
        }
    }

    @Override
    public List<ProcessInstanceTimer> findByProcessInstanceId(String processInstanceId) {
        String sql = "SELECT * FROM process_instance_timers WHERE process_instance_id = ?";
        List<ProcessInstanceTimer> list = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, processInstanceId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            logger.error("Failed to fetch process_instance_timers for process_instance_id={}: {}",
                    processInstanceId, e.getMessage(), e);
            throw new RuntimeException("Database error fetching process_instance_timers", e);
        }
    }

    @Override
    public List<ProcessInstanceTimer> findDueTimers(LocalDateTime now) {
        String sql = "SELECT * FROM process_instance_timers WHERE due_date <= ?";
        List<ProcessInstanceTimer> list = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setTimestamp(1, Timestamp.valueOf(now));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            logger.error("Failed to fetch due process_instance_timers: {}", e.getMessage(), e);
            throw new RuntimeException("Database error fetching due process_instance_timers", e);
        }
    }

    @Override
    public boolean deleteById(String id) {
        String sql = "DELETE FROM process_instance_timers WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, id);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Failed to delete process_instance_timer id={}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Database error deleting process_instance_timer", e);
        }
    }

    private ProcessInstanceTimer mapRow(ResultSet rs) throws SQLException {
        ProcessInstanceTimer timer = new ProcessInstanceTimer();
        timer.setId(rs.getString("id"));
        timer.setProcessInstanceId(rs.getString("process_instance_id"));
        timer.setNodeId(rs.getString("node_id"));

        Timestamp dueDate = rs.getTimestamp("due_date");
        if (dueDate != null) {
            timer.setDueDate(dueDate.toLocalDateTime());
        }
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            timer.setCreatedAt(createdAt.toLocalDateTime());
        }
        return timer;
    }
}
