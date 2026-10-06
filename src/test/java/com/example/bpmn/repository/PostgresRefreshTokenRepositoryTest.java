package com.example.bpmn.repository;

import com.example.bpmn.model.RefreshToken;
import com.example.bpmn.repository.impl.PostgresRefreshTokenRepository;
import com.example.bpmn.repository.impl.PostgresUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresRefreshTokenRepositoryTest extends PostgresRepositoryTestBase {

    private final RefreshTokenRepository repository = new PostgresRefreshTokenRepository();

    @BeforeEach
    void insertParentUser() {
        insertUser("u1");
    }

    private RefreshToken newToken(String id, String tokenHash, LocalDateTime expiresAt) {
        RefreshToken token = new RefreshToken();
        token.setId(id);
        token.setUserId("u1");
        token.setTokenHash(tokenHash);
        token.setExpiresAt(expiresAt);
        token.setRevoked(false);
        token.setCreatedAt(NOW);
        return token;
    }

    @Test
    @DisplayName("save then findByTokenHash round-trips every column")
    void savePersistsAllColumns() {
        repository.save(newToken("rt1", "hash-1", NOW.plusDays(7)));

        RefreshToken found = repository.findByTokenHash("hash-1").orElseThrow();

        assertEquals("rt1", found.getId());
        assertEquals("u1", found.getUserId());
        assertEquals("hash-1", found.getTokenHash());
        assertEquals(NOW.plusDays(7), found.getExpiresAt());
        assertFalse(found.isRevoked());
        assertEquals(NOW, found.getCreatedAt());
    }

    @Test
    @DisplayName("findByTokenHash returns empty for an unknown hash")
    void findByTokenHashReturnsEmptyWhenMissing() {
        assertTrue(repository.findByTokenHash("nope").isEmpty());
    }

    @Test
    @DisplayName("revoke flips the revoked flag of that token only")
    void revokeFlipsTheFlag() {
        repository.save(newToken("rt1", "hash-1", NOW.plusDays(7)));
        repository.save(newToken("rt2", "hash-2", NOW.plusDays(7)));

        repository.revoke("rt1");

        assertTrue(repository.findByTokenHash("hash-1").orElseThrow().isRevoked());
        assertFalse(repository.findByTokenHash("hash-2").orElseThrow().isRevoked());
    }

    @Test
    @DisplayName("revoking an unknown id is a silent no-op")
    void revokeUnknownIdIsNoOp() {
        assertDoesNotThrow(() -> repository.revoke("never-existed"));
    }

    @Test
    @DisplayName("save is an insert, not an upsert: a duplicate id is rejected")
    void saveRejectsDuplicateId() {
        repository.save(newToken("rt1", "hash-1", NOW.plusDays(7)));

        assertThrows(RuntimeException.class, () -> repository.save(newToken("rt1", "hash-2", NOW.plusDays(7))));
    }

    @Test
    @DisplayName("the token hash is unique across rows")
    void saveRejectsDuplicateTokenHash() {
        repository.save(newToken("rt1", "hash-1", NOW.plusDays(7)));

        assertThrows(RuntimeException.class, () -> repository.save(newToken("rt2", "hash-1", NOW.plusDays(7))));
    }

    @Test
    @DisplayName("deleting the owning user cascades to their refresh tokens")
    void deletingUserCascadesToTokens() {
        repository.save(newToken("rt1", "hash-1", NOW.plusDays(7)));

        assertTrue(new PostgresUserRepository().deleteById("u1"));

        assertTrue(repository.findByTokenHash("hash-1").isEmpty());
    }
}
