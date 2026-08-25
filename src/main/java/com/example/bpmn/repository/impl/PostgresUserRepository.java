package com.example.bpmn.repository.impl;

import com.example.bpmn.config.DatabaseConfig;
import com.example.bpmn.model.User;
import com.example.bpmn.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PostgresUserRepository implements UserRepository {
    private static final Logger logger = LoggerFactory.getLogger(PostgresUserRepository.class);

    @Override
    public User save(User user) {
        String sql = """
            INSERT INTO users (id, username, email, full_name, role, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE
            SET username = EXCLUDED.username,
                email = EXCLUDED.email,
                full_name = EXCLUDED.full_name,
                role = EXCLUDED.role,
                status = EXCLUDED.status,
                updated_at = EXCLUDED.updated_at
        """;

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, user.getId());
            stmt.setString(2, user.getUsername());
            stmt.setString(3, user.getEmail());
            stmt.setString(4, user.getFullName());
            stmt.setString(5, user.getRole());
            stmt.setString(6, user.getStatus());
            stmt.setTimestamp(7, user.getCreatedAt() != null ? Timestamp.valueOf(user.getCreatedAt()) : Timestamp.valueOf(LocalDateTime.now()));
            stmt.setTimestamp(8, user.getUpdatedAt() != null ? Timestamp.valueOf(user.getUpdatedAt()) : Timestamp.valueOf(LocalDateTime.now()));

            stmt.executeUpdate();
            return user;
        } catch (SQLException e) {
            logger.error("Failed to save user id={}: {}", user.getId(), e.getMessage(), e);
            throw new RuntimeException("Database error saving user", e);
        }
    }

    @Override
    public Optional<User> findById(String id) {
        String sql = "SELECT id, username, email, full_name, role, status, created_at, updated_at FROM users WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToUser(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            logger.error("Failed to find user id={}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Database error finding user", e);
        }
    }

    @Override
    public Optional<User> findByUsername(String username) {
        String sql = "SELECT id, username, email, full_name, role, status, created_at, updated_at FROM users WHERE username = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToUser(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            logger.error("Failed to find user by username={}: {}", username, e.getMessage(), e);
            throw new RuntimeException("Database error finding user", e);
        }
    }

    @Override
    public Optional<User> findByEmail(String email) {
        String sql = "SELECT id, username, email, full_name, role, status, created_at, updated_at FROM users WHERE email = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, email);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToUser(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            logger.error("Failed to find user by email={}: {}", email, e.getMessage(), e);
            throw new RuntimeException("Database error finding user", e);
        }
    }

    @Override
    public List<User> findAll() {
        String sql = "SELECT id, username, email, full_name, role, status, created_at, updated_at FROM users ORDER BY created_at DESC";
        List<User> list = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                list.add(mapRowToUser(rs));
            }
            return list;
        } catch (SQLException e) {
            logger.error("Failed to fetch all users: {}", e.getMessage(), e);
            throw new RuntimeException("Database error fetching users", e);
        }
    }

    @Override
    public boolean deleteById(String id) {
        String sql = "DELETE FROM users WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, id);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Failed to delete user id={}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Database error deleting user", e);
        }
    }

    private User mapRowToUser(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getString("id"));
        user.setUsername(rs.getString("username"));
        user.setEmail(rs.getString("email"));
        user.setFullName(rs.getString("full_name"));
        user.setRole(rs.getString("role"));
        user.setStatus(rs.getString("status"));

        Timestamp createdAtTs = rs.getTimestamp("created_at");
        if (createdAtTs != null) {
            user.setCreatedAt(createdAtTs.toLocalDateTime());
        }

        Timestamp updatedAtTs = rs.getTimestamp("updated_at");
        if (updatedAtTs != null) {
            user.setUpdatedAt(updatedAtTs.toLocalDateTime());
        }

        return user;
    }
}
