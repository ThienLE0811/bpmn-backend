package com.example.bpmn.repository.impl;

import com.example.bpmn.config.DatabaseConfig;
import com.example.bpmn.model.BpmnProcess;
import com.example.bpmn.repository.BpmnProcessRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PostgresBpmnProcessRepository implements BpmnProcessRepository {
    private static final Logger logger = LoggerFactory.getLogger(PostgresBpmnProcessRepository.class);

    @Override
    public BpmnProcess save(BpmnProcess process) {
        String sql = """
            INSERT INTO bpmn_processes (id, process_key, name, version, bpmn_xml, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE
            SET process_key = EXCLUDED.process_key,
                name = EXCLUDED.name,
                version = EXCLUDED.version,
                bpmn_xml = EXCLUDED.bpmn_xml,
                status = EXCLUDED.status,
                updated_at = EXCLUDED.updated_at
        """;

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, process.getId());
            stmt.setString(2, process.getProcessKey());
            stmt.setString(3, process.getName());
            if (process.getVersion() != null) {
                stmt.setInt(4, process.getVersion());
            } else {
                stmt.setInt(4, 1);
            }
            stmt.setString(5, process.getBpmnXml());
            stmt.setString(6, process.getStatus());
            stmt.setTimestamp(7, process.getCreatedAt() != null ? Timestamp.valueOf(process.getCreatedAt()) : Timestamp.valueOf(LocalDateTime.now()));
            stmt.setTimestamp(8, process.getUpdatedAt() != null ? Timestamp.valueOf(process.getUpdatedAt()) : Timestamp.valueOf(LocalDateTime.now()));

            stmt.executeUpdate();
            return process;
        } catch (SQLException e) {
            logger.error("Failed to save bpmn_process id={}: {}", process.getId(), e.getMessage(), e);
            throw new RuntimeException("Database error saving bpmn_process", e);
        }
    }

    @Override
    public Optional<BpmnProcess> findById(String id) {
        String sql = "SELECT id, process_key, name, version, bpmn_xml, status, created_at, updated_at FROM bpmn_processes WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToProcess(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            logger.error("Failed to find bpmn_process id={}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Database error finding bpmn_process", e);
        }
    }

    @Override
    public Optional<BpmnProcess> findByProcessKey(String processKey) {
        String sql = "SELECT id, process_key, name, version, bpmn_xml, status, created_at, updated_at FROM bpmn_processes WHERE process_key = ? ORDER BY version DESC LIMIT 1";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, processKey);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToProcess(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            logger.error("Failed to find bpmn_process by process_key={}: {}", processKey, e.getMessage(), e);
            throw new RuntimeException("Database error finding bpmn_process", e);
        }
    }

    @Override
    public List<BpmnProcess> findAll() {
        String sql = "SELECT id, process_key, name, version, bpmn_xml, status, created_at, updated_at FROM bpmn_processes ORDER BY created_at DESC";
        List<BpmnProcess> list = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                list.add(mapRowToProcess(rs));
            }
            return list;
        } catch (SQLException e) {
            logger.error("Failed to fetch all bpmn_processes: {}", e.getMessage(), e);
            throw new RuntimeException("Database error fetching bpmn_processes", e);
        }
    }

    @Override
    public boolean deleteById(String id) {
        String sql = "DELETE FROM bpmn_processes WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, id);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Failed to delete bpmn_process id={}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Database error deleting bpmn_process", e);
        }
    }

    private BpmnProcess mapRowToProcess(ResultSet rs) throws SQLException {
        BpmnProcess process = new BpmnProcess();
        process.setId(rs.getString("id"));
        process.setProcessKey(rs.getString("process_key"));
        process.setName(rs.getString("name"));
        process.setVersion(rs.getInt("version"));
        process.setBpmnXml(rs.getString("bpmn_xml"));
        process.setStatus(rs.getString("status"));

        Timestamp createdAtTs = rs.getTimestamp("created_at");
        if (createdAtTs != null) {
            process.setCreatedAt(createdAtTs.toLocalDateTime());
        }

        Timestamp updatedAtTs = rs.getTimestamp("updated_at");
        if (updatedAtTs != null) {
            process.setUpdatedAt(updatedAtTs.toLocalDateTime());
        }

        return process;
    }
}
