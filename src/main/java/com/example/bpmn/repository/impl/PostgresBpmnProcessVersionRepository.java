package com.example.bpmn.repository.impl;

import com.example.bpmn.config.DatabaseConfig;
import com.example.bpmn.model.BpmnProcessVersion;
import com.example.bpmn.repository.BpmnProcessVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PostgresBpmnProcessVersionRepository implements BpmnProcessVersionRepository {
    private static final Logger logger = LoggerFactory.getLogger(PostgresBpmnProcessVersionRepository.class);

    @Override
    public BpmnProcessVersion save(BpmnProcessVersion version) {
        String sql = """
            INSERT INTO bpmn_process_versions (id, process_id, version, bpmn_xml, created_by, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (process_id, version) DO UPDATE
            SET bpmn_xml = EXCLUDED.bpmn_xml,
                created_by = EXCLUDED.created_by,
                created_at = EXCLUDED.created_at
        """;

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, version.getId());
            stmt.setString(2, version.getProcessId());
            stmt.setInt(3, version.getVersion());
            stmt.setString(4, version.getBpmnXml());
            stmt.setString(5, version.getCreatedBy());
            stmt.setTimestamp(6, version.getCreatedAt() != null
                    ? Timestamp.valueOf(version.getCreatedAt()) : Timestamp.valueOf(LocalDateTime.now()));

            stmt.executeUpdate();
            return version;
        } catch (SQLException e) {
            logger.error("Failed to save bpmn_process_version process_id={} version={}: {}",
                    version.getProcessId(), version.getVersion(), e.getMessage(), e);
            throw new RuntimeException("Database error saving bpmn_process_version", e);
        }
    }

    @Override
    public List<BpmnProcessVersion> findByProcessId(String processId) {
        String sql = "SELECT * FROM bpmn_process_versions WHERE process_id = ? ORDER BY version ASC";
        List<BpmnProcessVersion> list = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, processId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            logger.error("Failed to fetch bpmn_process_versions for process_id={}: {}", processId, e.getMessage(), e);
            throw new RuntimeException("Database error fetching bpmn_process_versions", e);
        }
    }

    @Override
    public Optional<BpmnProcessVersion> findByProcessIdAndVersion(String processId, int version) {
        String sql = "SELECT * FROM bpmn_process_versions WHERE process_id = ? AND version = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, processId);
            stmt.setInt(2, version);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            logger.error("Failed to fetch bpmn_process_version process_id={} version={}: {}",
                    processId, version, e.getMessage(), e);
            throw new RuntimeException("Database error fetching bpmn_process_version", e);
        }
    }

    private BpmnProcessVersion mapRow(ResultSet rs) throws SQLException {
        BpmnProcessVersion version = new BpmnProcessVersion();
        version.setId(rs.getString("id"));
        version.setProcessId(rs.getString("process_id"));
        version.setVersion(rs.getInt("version"));
        version.setBpmnXml(rs.getString("bpmn_xml"));
        version.setCreatedBy(rs.getString("created_by"));

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            version.setCreatedAt(createdAt.toLocalDateTime());
        }
        return version;
    }
}
