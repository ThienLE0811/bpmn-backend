package com.example.bpmn.service.impl;

import com.example.bpmn.dto.LoginRequest;
import com.example.bpmn.dto.LoginResponse;
import com.example.bpmn.dto.LogoutRequest;
import com.example.bpmn.dto.RefreshRequest;
import com.example.bpmn.dto.RefreshResponse;
import com.example.bpmn.exception.AppException;
import com.example.bpmn.mapper.UserMapper;
import com.example.bpmn.model.RefreshToken;
import com.example.bpmn.model.User;
import com.example.bpmn.repository.RefreshTokenRepository;
import com.example.bpmn.repository.UserRepository;
import com.example.bpmn.service.AuthService;
import com.example.bpmn.util.JwtUtil;
import com.example.bpmn.util.PasswordUtil;
import com.example.bpmn.util.RefreshTokenUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.UUID;

public class AuthServiceImpl implements AuthService {
    private static final Logger logger = LoggerFactory.getLogger(AuthServiceImpl.class);
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public AuthServiceImpl(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        if (request.getUsername() == null || request.getUsername().isBlank()
                || request.getPassword() == null || request.getPassword().isBlank()) {
            throw new AppException("Username and password must not be empty", 400);
        }

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new AppException("Invalid username or password", 401));

        if (!PasswordUtil.matches(request.getPassword(), user.getPasswordHash())) {
            throw new AppException("Invalid username or password", 401);
        }
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new AppException("User account is not active", 403);
        }

        String accessToken = JwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = issueRefreshToken(user.getId());
        logger.info("User logged in: {}", user.getUsername());

        return new LoginResponse(accessToken, refreshToken, "Bearer", JwtUtil.getExpirationSeconds(),
                UserMapper.toResponse(user));
    }

    @Override
    public RefreshResponse refresh(RefreshRequest request) {
        if (request.getRefreshToken() == null || request.getRefreshToken().isBlank()) {
            throw new AppException("refreshToken must not be empty", 400);
        }

        RefreshToken stored = refreshTokenRepository.findByTokenHash(RefreshTokenUtil.hash(request.getRefreshToken()))
                .orElseThrow(() -> new AppException("Invalid refresh token", 401));

        if (stored.isRevoked() || stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new AppException("Refresh token expired or revoked", 401);
        }

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new AppException("Invalid refresh token", 401));
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new AppException("User account is not active", 403);
        }

        String accessToken = JwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        return new RefreshResponse(accessToken, "Bearer", JwtUtil.getExpirationSeconds());
    }

    @Override
    public void logout(LogoutRequest request) {
        if (request.getRefreshToken() == null || request.getRefreshToken().isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(RefreshTokenUtil.hash(request.getRefreshToken()))
                .ifPresent(stored -> refreshTokenRepository.revoke(stored.getId()));
    }

    private String issueRefreshToken(String userId) {
        String plainToken = RefreshTokenUtil.generate();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(UUID.randomUUID().toString());
        refreshToken.setUserId(userId);
        refreshToken.setTokenHash(RefreshTokenUtil.hash(plainToken));
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(JwtUtil.getRefreshExpirationDays()));
        refreshToken.setRevoked(false);
        refreshToken.setCreatedAt(LocalDateTime.now());
        refreshTokenRepository.save(refreshToken);

        return plainToken;
    }
}
