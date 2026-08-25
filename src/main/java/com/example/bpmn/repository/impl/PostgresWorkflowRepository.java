package com.example.bpmn.repository.impl;

import com.example.bpmn.config.DatabaseConfig;
import com.example.bpmn.model.Workflow;
import com.example.bpmn.repository.WorkflowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PostgresWorkflowRepository implements WorkflowRepository {
    private static final Logger logger = LoggerFactory.getLogger(PostgresWorkflowRepository.class);

    @Override
    public Workflow save(Workflow workflow) {
        String sql = """
            INSERT INTO workflows (id, name, description, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE 
            SET name = EXCLUDED.name,
                description = EXCLUDED.description,
                status = EXCLUDED.status,
                updated_at = EXCLUDED.updated_at
        """;

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, workflow.getId());
            stmt.setString(2, workflow.getName());
            stmt.setString(3, workflow.getDescription());
            stmt.setString(4, workflow.getStatus());
            stmt.setTimestamp(5, workflow.getCreatedAt() != null ? Timestamp.valueOf(workflow.getCreatedAt()) : Timestamp.valueOf(LocalDateTime.now()));
            stmt.setTimestamp(6, workflow.getUpdatedAt() != null ? Timestamp.valueOf(workflow.getUpdatedAt()) : Timestamp.valueOf(LocalDateTime.now()));

            stmt.executeUpdate();
            return workflow;
        } catch (SQLException e) {
            logger.error("Failed to save workflow id={}: {}", workflow.getId(), e.getMessage(), e);
            throw new RuntimeException("Database error saving workflow", e);
        }
    }

    @Override
    public Optional<Workflow> findById(String id) {
        String sql = "SELECT id, name, description, status, created_at, updated_at FROM workflows WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToWorkflow(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            logger.error("Failed to find workflow id={}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Database error finding workflow", e);
        }
    }

    @Override
    public List<Workflow> findAll() {
        String sql = "SELECT id, name, description, status, created_at, updated_at FROM workflows ORDER BY created_at DESC";
        List<Workflow> list = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                list.add(mapRowToWorkflow(rs));
            }
            return list;
        } catch (SQLException e) {
            logger.error("Failed to fetch all workflows: {}", e.getMessage(), e);
            throw new RuntimeException("Database error fetching workflows", e);
        }
    }

    @Override
    public boolean deleteById(String id) {
        String sql = "DELETE FROM workflows WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, id);
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            logger.error("Failed to delete workflow id={}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Database error deleting workflow", e);
        }
    }

    private Workflow mapRowToWorkflow(ResultSet rs) throws SQLException {
        Workflow wf = new Workflow();
        wf.setId(rs.getString("id"));
        wf.setName(rs.getString("name"));
        wf.setDescription(rs.getString("description"));
        wf.setStatus(rs.getString("status"));

        Timestamp createdAtTs = rs.getTimestamp("created_at");
        if (createdAtTs != null) {
            wf.setCreatedAt(createdAtTs.toLocalDateTime());
        }

        Timestamp updatedAtTs = rs.getTimestamp("updated_at");
        if (updatedAtTs != null) {
            wf.setUpdatedAt(updatedAtTs.toLocalDateTime());
        }

        return wf;
    }
}
