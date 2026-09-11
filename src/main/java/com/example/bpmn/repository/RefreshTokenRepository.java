package com.example.bpmn.repository;

import com.example.bpmn.model.RefreshToken;
import java.util.Optional;

public interface RefreshTokenRepository {
    RefreshToken save(RefreshToken token);
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    void revoke(String id);
}
