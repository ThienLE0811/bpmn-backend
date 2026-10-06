package com.example.bpmn.repository;

import com.example.bpmn.model.User;
import com.example.bpmn.repository.impl.PostgresUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresUserRepositoryTest extends PostgresRepositoryTestBase {

    private final UserRepository repository = new PostgresUserRepository();

    private User newUser(String id, LocalDateTime createdAt) {
        User user = new User(id, "user-" + id, id + "@example.com", "User " + id, "USER", "ACTIVE");
        user.setPasswordHash("hash-" + id);
        user.setCreatedAt(createdAt);
        user.setUpdatedAt(createdAt);
        return user;
    }

    @Test
    @DisplayName("save then findById round-trips every column")
    void savePersistsAllColumns() {
        repository.save(newUser("u1", NOW));

        User found = repository.findById("u1").orElseThrow();

        assertEquals("u1", found.getId());
        assertEquals("user-u1", found.getUsername());
        assertEquals("u1@example.com", found.getEmail());
        assertEquals("User u1", found.getFullName());
        assertEquals("USER", found.getRole());
        assertEquals("ACTIVE", found.getStatus());
        assertEquals("hash-u1", found.getPasswordHash());
        assertEquals(NOW, found.getCreatedAt());
        assertEquals(NOW, found.getUpdatedAt());
    }

    @Test
    @DisplayName("save on an existing id updates in place and keeps the original created_at")
    void saveUpsertsExistingRow() {
        repository.save(newUser("u1", NOW));

        User changed = newUser("u1", NOW.plusHours(1));
        changed.setUsername("renamed");
        changed.setEmail("renamed@example.com");
        changed.setFullName("Renamed");
        changed.setRole("ADMIN");
        changed.setStatus("INACTIVE");
        changed.setPasswordHash("hash-2");
        repository.save(changed);

        assertEquals(1, repository.count());
        User found = repository.findById("u1").orElseThrow();
        assertEquals("renamed", found.getUsername());
        assertEquals("renamed@example.com", found.getEmail());
        assertEquals("Renamed", found.getFullName());
        assertEquals("ADMIN", found.getRole());
        assertEquals("INACTIVE", found.getStatus());
        assertEquals("hash-2", found.getPasswordHash());
        // ON CONFLICT deliberately leaves created_at untouched.
        assertEquals(NOW, found.getCreatedAt());
        assertEquals(NOW.plusHours(1), found.getUpdatedAt());
    }

    @Test
    @DisplayName("save defaults created_at/updated_at to now when the model leaves them null")
    void saveDefaultsTimestamps() {
        User user = newUser("u1", null);
        LocalDateTime beforeSave = LocalDateTime.now().minusSeconds(1);

        repository.save(user);

        User found = repository.findById("u1").orElseThrow();
        assertTrue(found.getCreatedAt().isAfter(beforeSave), "created_at should be defaulted to now");
        assertTrue(found.getUpdatedAt().isAfter(beforeSave), "updated_at should be defaulted to now");
    }

    @Test
    @DisplayName("save keeps a null full_name as null rather than an empty string")
    void savePersistsNullableColumnsAsNull() {
        User user = newUser("u1", NOW);
        user.setFullName(null);
        user.setRole(null);
        user.setStatus(null);
        user.setPasswordHash(null);

        repository.save(user);

        User found = repository.findById("u1").orElseThrow();
        assertNull(found.getFullName());
        assertNull(found.getRole());
        assertNull(found.getStatus());
        assertNull(found.getPasswordHash());
    }

    @Test
    @DisplayName("findById returns empty for an unknown id")
    void findByIdReturnsEmptyWhenMissing() {
        assertEquals(Optional.empty(), repository.findById("nope"));
    }

    @Test
    @DisplayName("findByUsername / findByEmail match the stored row")
    void findsByUsernameAndEmail() {
        repository.save(newUser("u1", NOW));

        assertEquals("u1", repository.findByUsername("user-u1").orElseThrow().getId());
        assertEquals("u1", repository.findByEmail("u1@example.com").orElseThrow().getId());
        assertTrue(repository.findByUsername("unknown").isEmpty());
        assertTrue(repository.findByEmail("unknown@example.com").isEmpty());
    }

    @Test
    @DisplayName("findByUsername is case sensitive")
    void findByUsernameIsCaseSensitive() {
        repository.save(newUser("u1", NOW));

        assertTrue(repository.findByUsername("USER-U1").isEmpty());
    }

    @Test
    @DisplayName("findAll returns newest first")
    void findAllOrdersByCreatedAtDesc() {
        repository.save(newUser("old", NOW.minusDays(2)));
        repository.save(newUser("new", NOW));
        repository.save(newUser("middle", NOW.minusDays(1)));

        List<String> ids = repository.findAll().stream().map(User::getId).toList();

        assertEquals(List.of("new", "middle", "old"), ids);
    }

    @Test
    @DisplayName("findPage applies limit and offset to the same newest-first order")
    void findPageAppliesLimitAndOffset() {
        repository.save(newUser("a", NOW.minusDays(3)));
        repository.save(newUser("b", NOW.minusDays(2)));
        repository.save(newUser("c", NOW.minusDays(1)));

        assertEquals(List.of("c", "b"), repository.findPage(2, 0).stream().map(User::getId).toList());
        assertEquals(List.of("a"), repository.findPage(2, 2).stream().map(User::getId).toList());
        assertTrue(repository.findPage(2, 10).isEmpty());
    }

    @Test
    @DisplayName("count reflects the number of rows")
    void countReflectsRows() {
        assertEquals(0, repository.count());

        repository.save(newUser("u1", NOW));
        repository.save(newUser("u2", NOW));

        assertEquals(2, repository.count());
    }

    @Test
    @DisplayName("deleteById reports whether a row was removed")
    void deleteByIdReportsWhetherRowWasRemoved() {
        repository.save(newUser("u1", NOW));

        assertTrue(repository.deleteById("u1"));
        assertFalse(repository.deleteById("u1"));
        assertTrue(repository.findById("u1").isEmpty());
    }
}
