package com.example.bpmn.repository.impl;

import com.example.bpmn.config.DatabaseConfig;
import com.example.bpmn.model.BpmnProcessStartTimer;
import com.example.bpmn.repository.BpmnProcessStartTimerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PostgresBpmnProcessStartTimerRepository implements BpmnProcessStartTimerRepository {
    private static final Logger logger = LoggerFactory.getLogger(PostgresBpmnProcessStartTimerRepository.class);

    @Override
    public BpmnProcessStartTimer save(BpmnProcessStartTimer timer) {
        String sql = """
            INSERT INTO bpmn_process_start_timers (process_id, next_fire_at, repeats_remaining, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (process_id) DO UPDATE
            SET next_fire_at = EXCLUDED.next_fire_at,
                repeats_remaining = EXCLUDED.repeats_remaining,
                updated_at = EXCLUDED.updated_at
        """;

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, timer.getProcessId());
            stmt.setTimestamp(2, Timestamp.valueOf(timer.getNextFireAt()));
            if (timer.getRepeatsRemaining() != null) {
                stmt.setInt(3, timer.getRepeatsRemaining());
            } else {
                stmt.setNull(3, Types.INTEGER);
            }
            LocalDateTime now = LocalDateTime.now();
            stmt.setTimestamp(4, timer.getCreatedAt() != null ? Timestamp.valueOf(timer.getCreatedAt()) : Timestamp.valueOf(now));
            stmt.setTimestamp(5, timer.getUpdatedAt() != null ? Timestamp.valueOf(timer.getUpdatedAt()) : Timestamp.valueOf(now));

            stmt.executeUpdate();
            return timer;
        } catch (SQLException e) {
            logger.error("Failed to save bpmn_process_start_timer process_id={}: {}", timer.getProcessId(), e.getMessage(), e);
            throw new RuntimeException("Database error saving bpmn_process_start_timer", e);
        }
    }

    @Override
    public Optional<BpmnProcessStartTimer> findByProcessId(String processId) {
        String sql = "SELECT * FROM bpmn_process_start_timers WHERE process_id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, processId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            logger.error("Failed to find bpmn_process_start_timer process_id={}: {}", processId, e.getMessage(), e);
            throw new RuntimeException("Database error finding bpmn_process_start_timer", e);
        }
    }

    @Override
    public List<BpmnProcessStartTimer> findDue(LocalDateTime now) {
        String sql = "SELECT * FROM bpmn_process_start_timers WHERE next_fire_at <= ?";
        List<BpmnProcessStartTimer> list = new ArrayList<>();

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
            logger.error("Failed to fetch due bpmn_process_start_timers: {}", e.getMessage(), e);
            throw new RuntimeException("Database error fetching due bpmn_process_start_timers", e);
        }
    }

    @Override
    public void deleteByProcessId(String processId) {
        String sql = "DELETE FROM bpmn_process_start_timers WHERE process_id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, processId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to delete bpmn_process_start_timer process_id={}: {}", processId, e.getMessage(), e);
            throw new RuntimeException("Database error deleting bpmn_process_start_timer", e);
        }
    }

    private BpmnProcessStartTimer mapRow(ResultSet rs) throws SQLException {
        BpmnProcessStartTimer timer = new BpmnProcessStartTimer();
        timer.setProcessId(rs.getString("process_id"));

        Timestamp nextFireAt = rs.getTimestamp("next_fire_at");
        if (nextFireAt != null) {
            timer.setNextFireAt(nextFireAt.toLocalDateTime());
        }
        int repeatsRemaining = rs.getInt("repeats_remaining");
        if (!rs.wasNull()) {
            timer.setRepeatsRemaining(repeatsRemaining);
        }
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            timer.setCreatedAt(createdAt.toLocalDateTime());
        }
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) {
            timer.setUpdatedAt(updatedAt.toLocalDateTime());
        }
        return timer;
    }
}
