package com.example.bpmn.service.impl;

import com.example.bpmn.dto.LoginRequest;
import com.example.bpmn.dto.LoginResponse;
import com.example.bpmn.exception.AppException;
import com.example.bpmn.mapper.UserMapper;
import com.example.bpmn.model.User;
import com.example.bpmn.repository.UserRepository;
import com.example.bpmn.service.AuthService;
import com.example.bpmn.util.JwtUtil;
import com.example.bpmn.util.PasswordUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthServiceImpl implements AuthService {
    private static final Logger logger = LoggerFactory.getLogger(AuthServiceImpl.class);
    private final UserRepository userRepository;

    public AuthServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
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
        logger.info("User logged in: {}", user.getUsername());

        return new LoginResponse(accessToken, "Bearer", JwtUtil.getExpirationSeconds(), UserMapper.toResponse(user));
    }
}
