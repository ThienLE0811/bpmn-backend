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
            INSERT INTO bpmn_processes (id, process_key, process_name, description, category, version, bpmn_xml, status, created_by, updated_by, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE
            SET process_key = EXCLUDED.process_key,
                process_name = EXCLUDED.process_name,
                description = EXCLUDED.description,
                category = EXCLUDED.category,
                version = EXCLUDED.version,
                bpmn_xml = EXCLUDED.bpmn_xml,
                status = EXCLUDED.status,
                updated_by = EXCLUDED.updated_by,
                updated_at = EXCLUDED.updated_at
        """;

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, process.getId());
            stmt.setString(2, process.getProcessKey());
            stmt.setString(3, process.getName());
            stmt.setString(4, process.getDescription());
            stmt.setString(5, process.getCategory());
            if (process.getVersion() != null) {
                stmt.setInt(6, process.getVersion());
            } else {
                stmt.setInt(6, 1);
            }
            stmt.setString(7, process.getBpmnXml());
            stmt.setString(8, process.getStatus());
            stmt.setString(9, process.getCreatedBy());
            stmt.setString(10, process.getUpdatedBy());
            stmt.setTimestamp(11, process.getCreatedAt() != null ? Timestamp.valueOf(process.getCreatedAt()) : Timestamp.valueOf(LocalDateTime.now()));
            stmt.setTimestamp(12, process.getUpdatedAt() != null ? Timestamp.valueOf(process.getUpdatedAt()) : Timestamp.valueOf(LocalDateTime.now()));

            stmt.executeUpdate();
            return process;
        } catch (SQLException e) {
            logger.error("Failed to save bpmn_process id={}: {}", process.getId(), e.getMessage(), e);
            throw new RuntimeException("Database error saving bpmn_process", e);
        }
    }

    @Override
    public Optional<BpmnProcess> findById(String id) {
        String sql = "SELECT * FROM bpmn_processes WHERE id = ?";

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
        String sql = "SELECT * FROM bpmn_processes WHERE process_key = ? ORDER BY version DESC LIMIT 1";

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
        String sql = "SELECT * FROM bpmn_processes ORDER BY created_at DESC";
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

        if (hasColumn(rs, "id")) {
            process.setId(rs.getString("id"));
        }
        if (hasColumn(rs, "process_key")) {
            process.setProcessKey(rs.getString("process_key"));
        }

        // Handle both process_name and name
        if (hasColumn(rs, "process_name")) {
            process.setName(rs.getString("process_name"));
        } else if (hasColumn(rs, "name")) {
            process.setName(rs.getString("name"));
        }

        if (hasColumn(rs, "description")) {
            process.setDescription(rs.getString("description"));
        }
        if (hasColumn(rs, "category")) {
            process.setCategory(rs.getString("category"));
        }
        if (hasColumn(rs, "version")) {
            process.setVersion(rs.getInt("version"));
        }
        if (hasColumn(rs, "bpmn_xml")) {
            process.setBpmnXml(rs.getString("bpmn_xml"));
        }
        if (hasColumn(rs, "status")) {
            process.setStatus(rs.getString("status"));
        }
        if (hasColumn(rs, "created_by")) {
            process.setCreatedBy(rs.getString("created_by"));
        }
        if (hasColumn(rs, "updated_by")) {
            process.setUpdatedBy(rs.getString("updated_by"));
        }

        if (hasColumn(rs, "created_at")) {
            Timestamp createdAtTs = rs.getTimestamp("created_at");
            if (createdAtTs != null) {
                process.setCreatedAt(createdAtTs.toLocalDateTime());
            }
        }

        if (hasColumn(rs, "updated_at")) {
            Timestamp updatedAtTs = rs.getTimestamp("updated_at");
            if (updatedAtTs != null) {
                process.setUpdatedAt(updatedAtTs.toLocalDateTime());
            }
        }

        return process;
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
