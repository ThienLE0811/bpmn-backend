package com.example.bpmn.repository.impl;

import com.example.bpmn.config.DatabaseConfig;
import com.example.bpmn.model.DmnDecisionVersion;
import com.example.bpmn.repository.DmnDecisionVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PostgresDmnDecisionVersionRepository implements DmnDecisionVersionRepository {
    private static final Logger logger = LoggerFactory.getLogger(PostgresDmnDecisionVersionRepository.class);

    @Override
    public DmnDecisionVersion save(DmnDecisionVersion version) {
        String sql = """
            INSERT INTO dmn_decision_versions (id, decision_id, version, dmn_xml, created_by, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (decision_id, version) DO UPDATE
            SET dmn_xml = EXCLUDED.dmn_xml,
                created_by = EXCLUDED.created_by,
                created_at = EXCLUDED.created_at
        """;

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, version.getId());
            stmt.setString(2, version.getDecisionId());
            stmt.setInt(3, version.getVersion());
            stmt.setString(4, version.getDmnXml());
            stmt.setString(5, version.getCreatedBy());
            stmt.setTimestamp(6, version.getCreatedAt() != null
                    ? Timestamp.valueOf(version.getCreatedAt()) : Timestamp.valueOf(LocalDateTime.now()));

            stmt.executeUpdate();
            return version;
        } catch (SQLException e) {
            logger.error("Failed to save dmn_decision_version decision_id={} version={}: {}",
                    version.getDecisionId(), version.getVersion(), e.getMessage(), e);
            throw new RuntimeException("Database error saving dmn_decision_version", e);
        }
    }

    @Override
    public List<DmnDecisionVersion> findByDecisionId(String decisionId) {
        String sql = "SELECT * FROM dmn_decision_versions WHERE decision_id = ? ORDER BY version ASC";
        List<DmnDecisionVersion> list = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, decisionId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            logger.error("Failed to fetch dmn_decision_versions for decision_id={}: {}", decisionId, e.getMessage(), e);
            throw new RuntimeException("Database error fetching dmn_decision_versions", e);
        }
    }

    @Override
    public Optional<DmnDecisionVersion> findByDecisionIdAndVersion(String decisionId, int version) {
        String sql = "SELECT * FROM dmn_decision_versions WHERE decision_id = ? AND version = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, decisionId);
            stmt.setInt(2, version);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            logger.error("Failed to fetch dmn_decision_version decision_id={} version={}: {}",
                    decisionId, version, e.getMessage(), e);
            throw new RuntimeException("Database error fetching dmn_decision_version", e);
        }
    }

    private DmnDecisionVersion mapRow(ResultSet rs) throws SQLException {
        DmnDecisionVersion version = new DmnDecisionVersion();
        version.setId(rs.getString("id"));
        version.setDecisionId(rs.getString("decision_id"));
        version.setVersion(rs.getInt("version"));
        version.setDmnXml(rs.getString("dmn_xml"));
        version.setCreatedBy(rs.getString("created_by"));

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            version.setCreatedAt(createdAt.toLocalDateTime());
        }
        return version;
    }
}
