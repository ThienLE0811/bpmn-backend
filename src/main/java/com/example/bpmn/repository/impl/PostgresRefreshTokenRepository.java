package com.example.bpmn.repository.impl;

import com.example.bpmn.config.DatabaseConfig;
import com.example.bpmn.model.RefreshToken;
import com.example.bpmn.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;

public class PostgresRefreshTokenRepository implements RefreshTokenRepository {
    private static final Logger logger = LoggerFactory.getLogger(PostgresRefreshTokenRepository.class);

    @Override
    public RefreshToken save(RefreshToken token) {
        String sql = """
            INSERT INTO refresh_tokens (id, user_id, token_hash, expires_at, revoked, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
        """;

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, token.getId());
            stmt.setString(2, token.getUserId());
            stmt.setString(3, token.getTokenHash());
            stmt.setTimestamp(4, Timestamp.valueOf(token.getExpiresAt()));
            stmt.setBoolean(5, token.isRevoked());
            stmt.setTimestamp(6, Timestamp.valueOf(token.getCreatedAt()));

            stmt.executeUpdate();
            return token;
        } catch (SQLException e) {
            logger.error("Failed to save refresh_token id={}: {}", token.getId(), e.getMessage(), e);
            throw new RuntimeException("Database error saving refresh_token", e);
        }
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        String sql = "SELECT * FROM refresh_tokens WHERE token_hash = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, tokenHash);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            logger.error("Failed to find refresh_token: {}", e.getMessage(), e);
            throw new RuntimeException("Database error finding refresh_token", e);
        }
    }

    @Override
    public void revoke(String id) {
        String sql = "UPDATE refresh_tokens SET revoked = TRUE WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to revoke refresh_token id={}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Database error revoking refresh_token", e);
        }
    }

    private RefreshToken mapRow(ResultSet rs) throws SQLException {
        RefreshToken token = new RefreshToken();
        token.setId(rs.getString("id"));
        token.setUserId(rs.getString("user_id"));
        token.setTokenHash(rs.getString("token_hash"));
        token.setExpiresAt(rs.getTimestamp("expires_at").toLocalDateTime());
        token.setRevoked(rs.getBoolean("revoked"));
        token.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return token;
    }
}
