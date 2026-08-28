package com.example.bpmn.repository.impl;

import com.example.bpmn.config.DatabaseConfig;
import com.example.bpmn.model.DmnDecision;
import com.example.bpmn.repository.DmnDecisionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PostgresDmnDecisionRepository implements DmnDecisionRepository {
    private static final Logger logger = LoggerFactory.getLogger(PostgresDmnDecisionRepository.class);

    @Override
    public DmnDecision save(DmnDecision decision) {
        String sql = """
            INSERT INTO dmn_decision (id, decision_key, name, description, hit_policy, category, version, dmn_xml, status, created_by, updated_by, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE
            SET decision_key = EXCLUDED.decision_key,
                name = EXCLUDED.name,
                description = EXCLUDED.description,
                hit_policy = EXCLUDED.hit_policy,
                category = EXCLUDED.category,
                version = EXCLUDED.version,
                dmn_xml = EXCLUDED.dmn_xml,
                status = EXCLUDED.status,
                updated_by = EXCLUDED.updated_by,
                updated_at = EXCLUDED.updated_at
        """;

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, decision.getId());
            stmt.setString(2, decision.getDecisionKey());
            stmt.setString(3, decision.getName());
            stmt.setString(4, decision.getDescription());
            stmt.setString(5, decision.getHitPolicy());
            stmt.setString(6, decision.getCategory());
            if (decision.getVersion() != null) {
                stmt.setInt(7, decision.getVersion());
            } else {
                stmt.setInt(7, 1);
            }
            stmt.setString(8, decision.getDmnXml());
            stmt.setString(9, decision.getStatus());
            stmt.setString(10, decision.getCreatedBy());
            stmt.setString(11, decision.getUpdatedBy());
            stmt.setTimestamp(12, decision.getCreatedAt() != null ? Timestamp.valueOf(decision.getCreatedAt()) : Timestamp.valueOf(LocalDateTime.now()));
            stmt.setTimestamp(13, decision.getUpdatedAt() != null ? Timestamp.valueOf(decision.getUpdatedAt()) : Timestamp.valueOf(LocalDateTime.now()));

            stmt.executeUpdate();
            return decision;
        } catch (SQLException e) {
            logger.error("Failed to save dmn_decision id={}: {}", decision.getId(), e.getMessage(), e);
            throw new RuntimeException("Database error saving dmn_decision", e);
        }
    }

    @Override
    public Optional<DmnDecision> findById(String id) {
        String sql = "SELECT * FROM dmn_decision WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToDecision(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            logger.error("Failed to find dmn_decision id={}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Database error finding dmn_decision", e);
        }
    }

    @Override
    public Optional<DmnDecision> findByDecisionKey(String decisionKey) {
        String sql = "SELECT * FROM dmn_decision WHERE decision_key = ? ORDER BY version DESC LIMIT 1";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, decisionKey);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToDecision(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            logger.error("Failed to find dmn_decision by decision_key={}: {}", decisionKey, e.getMessage(), e);
            throw new RuntimeException("Database error finding dmn_decision", e);
        }
    }

    @Override
    public List<DmnDecision> findAll() {
        String sql = "SELECT * FROM dmn_decision ORDER BY created_at DESC";
        List<DmnDecision> list = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                list.add(mapRowToDecision(rs));
            }
            return list;
        } catch (SQLException e) {
            logger.error("Failed to fetch all dmn_decisions: {}", e.getMessage(), e);
            throw new RuntimeException("Database error fetching dmn_decisions", e);
        }
    }

    @Override
    public boolean deleteById(String id) {
        String sql = "DELETE FROM dmn_decision WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, id);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Failed to delete dmn_decision id={}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Database error deleting dmn_decision", e);
        }
    }

    private DmnDecision mapRowToDecision(ResultSet rs) throws SQLException {
        DmnDecision decision = new DmnDecision();

        if (hasColumn(rs, "id")) {
            decision.setId(rs.getString("id"));
        }
        if (hasColumn(rs, "decision_key")) {
            decision.setDecisionKey(rs.getString("decision_key"));
        } else if (hasColumn(rs, "key")) {
            decision.setDecisionKey(rs.getString("key"));
        }

        if (hasColumn(rs, "decision_name")) {
            decision.setName(rs.getString("decision_name"));
        } else if (hasColumn(rs, "name")) {
            decision.setName(rs.getString("name"));
        }

        if (hasColumn(rs, "description")) {
            decision.setDescription(rs.getString("description"));
        }
        if (hasColumn(rs, "hit_policy")) {
            decision.setHitPolicy(rs.getString("hit_policy"));
        }
        if (hasColumn(rs, "category")) {
            decision.setCategory(rs.getString("category"));
        }
        if (hasColumn(rs, "version")) {
            decision.setVersion(rs.getInt("version"));
        }
        if (hasColumn(rs, "dmn_xml")) {
            decision.setDmnXml(rs.getString("dmn_xml"));
        } else if (hasColumn(rs, "xml")) {
            decision.setDmnXml(rs.getString("xml"));
        }

        if (hasColumn(rs, "status")) {
            decision.setStatus(rs.getString("status"));
        }
        if (hasColumn(rs, "created_by")) {
            decision.setCreatedBy(rs.getString("created_by"));
        }
        if (hasColumn(rs, "updated_by")) {
            decision.setUpdatedBy(rs.getString("updated_by"));
        }

        if (hasColumn(rs, "created_at")) {
            Timestamp createdAtTs = rs.getTimestamp("created_at");
            if (createdAtTs != null) {
                decision.setCreatedAt(createdAtTs.toLocalDateTime());
            }
        }

        if (hasColumn(rs, "updated_at")) {
            Timestamp updatedAtTs = rs.getTimestamp("updated_at");
            if (updatedAtTs != null) {
                decision.setUpdatedAt(updatedAtTs.toLocalDateTime());
            }
        }

        return decision;
    }

    private boolean hasColumn(ResultSet rs, String columnName) {
        try {
            ResultSetMetaData rsmd = rs.getMetaData();
            int columns = rsmd.getColumnCount();
            for (int x = 1; x <= columns; x++) {
                if (columnName.equalsIgnoreCase(rsmd.getColumnLabel(x)) || columnName.equalsIgnoreCase(rsmd.getColumnName(x))) {
                    return true;
                }
            }
            return false;
        } catch (SQLException e) {
            return false;
        }
    }
}
