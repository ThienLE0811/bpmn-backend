package com.example.bpmn.repository.impl;

import com.example.bpmn.config.DatabaseConfig;
import com.example.bpmn.model.Task;
import com.example.bpmn.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PostgresTaskRepository implements TaskRepository {
    private static final Logger logger = LoggerFactory.getLogger(PostgresTaskRepository.class);

    @Override
    public Task save(Task task) {
        String sql = """
            INSERT INTO tasks
                (id, process_id, process_instance_id, node_id, name, description, assignee_id, status,
                 claimed_by, claimed_at, completed_by, completed_at, due_date, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE
            SET process_id = EXCLUDED.process_id,
                process_instance_id = EXCLUDED.process_instance_id,
                node_id = EXCLUDED.node_id,
                name = EXCLUDED.name,
                description = EXCLUDED.description,
                assignee_id = EXCLUDED.assignee_id,
                status = EXCLUDED.status,
                claimed_by = EXCLUDED.claimed_by,
                claimed_at = EXCLUDED.claimed_at,
                completed_by = EXCLUDED.completed_by,
                completed_at = EXCLUDED.completed_at,
                due_date = EXCLUDED.due_date,
                updated_at = EXCLUDED.updated_at
        """;

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, task.getId());
            stmt.setString(2, task.getProcessId());
            stmt.setString(3, task.getProcessInstanceId());
            stmt.setString(4, task.getNodeId());
            stmt.setString(5, task.getName());
            stmt.setString(6, task.getDescription());
            stmt.setString(7, task.getAssigneeId());
            stmt.setString(8, task.getStatus());
            stmt.setString(9, task.getClaimedBy());
            stmt.setTimestamp(10, task.getClaimedAt() != null ? Timestamp.valueOf(task.getClaimedAt()) : null);
            stmt.setString(11, task.getCompletedBy());
            stmt.setTimestamp(12, task.getCompletedAt() != null ? Timestamp.valueOf(task.getCompletedAt()) : null);
            stmt.setTimestamp(13, task.getDueDate() != null ? Timestamp.valueOf(task.getDueDate()) : null);
            stmt.setTimestamp(14, task.getCreatedAt() != null ? Timestamp.valueOf(task.getCreatedAt()) : Timestamp.valueOf(LocalDateTime.now()));
            stmt.setTimestamp(15, task.getUpdatedAt() != null ? Timestamp.valueOf(task.getUpdatedAt()) : Timestamp.valueOf(LocalDateTime.now()));

            stmt.executeUpdate();
            return task;
        } catch (SQLException e) {
            logger.error("Failed to save task id={}: {}", task.getId(), e.getMessage(), e);
            throw new RuntimeException("Database error saving task", e);
        }
    }

    @Override
    public Optional<Task> findById(String id) {
        String sql = "SELECT * FROM tasks WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToTask(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            logger.error("Failed to find task id={}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Database error finding task", e);
        }
    }

    @Override
    public List<Task> findByProcessId(String processId) {
        String sql = "SELECT * FROM tasks WHERE process_id = ? ORDER BY created_at ASC";
        List<Task> list = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, processId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToTask(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            logger.error("Failed to fetch tasks by processId={}: {}", processId, e.getMessage(), e);
            throw new RuntimeException("Database error fetching tasks by processId", e);
        }
    }

    @Override
    public List<Task> findByAssigneeId(String assigneeId) {
        String sql = "SELECT * FROM tasks WHERE assignee_id = ? ORDER BY created_at ASC";
        List<Task> list = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, assigneeId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToTask(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            logger.error("Failed to fetch tasks by assigneeId={}: {}", assigneeId, e.getMessage(), e);
            throw new RuntimeException("Database error fetching tasks by assigneeId", e);
        }
    }

    @Override
    public List<Task> findByProcessInstanceId(String processInstanceId) {
        String sql = "SELECT * FROM tasks WHERE process_instance_id = ? ORDER BY created_at ASC";
        List<Task> list = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, processInstanceId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToTask(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            logger.error("Failed to fetch tasks by processInstanceId={}: {}", processInstanceId, e.getMessage(), e);
            throw new RuntimeException("Database error fetching tasks by processInstanceId", e);
        }
    }

    @Override
    public List<Task> findAll() {
        String sql = "SELECT * FROM tasks ORDER BY created_at DESC";
        List<Task> list = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                list.add(mapRowToTask(rs));
            }
            return list;
        } catch (SQLException e) {
            logger.error("Failed to fetch all tasks: {}", e.getMessage(), e);
            throw new RuntimeException("Database error fetching tasks", e);
        }
    }

    @Override
    public boolean deleteById(String id) {
        String sql = "DELETE FROM tasks WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, id);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Failed to delete task id={}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Database error deleting task", e);
        }
    }

    private Task mapRowToTask(ResultSet rs) throws SQLException {
        Task task = new Task();
        task.setId(rs.getString("id"));
        task.setProcessId(rs.getString("process_id"));
        task.setName(rs.getString("name"));
        task.setDescription(rs.getString("description"));
        task.setAssigneeId(rs.getString("assignee_id"));
        task.setStatus(rs.getString("status"));
        task.setProcessInstanceId(rs.getString("process_instance_id"));
        task.setNodeId(rs.getString("node_id"));
        task.setClaimedBy(rs.getString("claimed_by"));
        task.setCompletedBy(rs.getString("completed_by"));

        Timestamp claimedAtTs = rs.getTimestamp("claimed_at");
        if (claimedAtTs != null) {
            task.setClaimedAt(claimedAtTs.toLocalDateTime());
        }

        Timestamp completedAtTs = rs.getTimestamp("completed_at");
        if (completedAtTs != null) {
            task.setCompletedAt(completedAtTs.toLocalDateTime());
        }

        Timestamp dueDateTs = rs.getTimestamp("due_date");
        if (dueDateTs != null) {
            task.setDueDate(dueDateTs.toLocalDateTime());
        }

        Timestamp createdAtTs = rs.getTimestamp("created_at");
        if (createdAtTs != null) {
            task.setCreatedAt(createdAtTs.toLocalDateTime());
        }

        Timestamp updatedAtTs = rs.getTimestamp("updated_at");
        if (updatedAtTs != null) {
            task.setUpdatedAt(updatedAtTs.toLocalDateTime());
        }

        return task;
    }
}
