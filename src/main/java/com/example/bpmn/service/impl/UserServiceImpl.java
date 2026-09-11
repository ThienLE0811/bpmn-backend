package com.example.bpmn.service.impl;

import com.example.bpmn.dto.UserRequest;
import com.example.bpmn.dto.UserResponse;
import com.example.bpmn.dto.UserUpdateRequest;
import com.example.bpmn.exception.AppException;
import com.example.bpmn.mapper.UserMapper;
import com.example.bpmn.model.User;
import com.example.bpmn.repository.UserRepository;
import com.example.bpmn.service.UserService;
import com.example.bpmn.util.PasswordUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class UserServiceImpl implements UserService {
    private static final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);
    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public List<UserResponse> getAllUsers() {
        logger.info("Fetching all users from database");
        return userRepository.findAll().stream()
                .map(UserMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public UserResponse getUserById(String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException("User not found with id: " + id, 404));
        return UserMapper.toResponse(user);
    }

    @Override
    public UserResponse createUser(UserRequest request, String requesterRole) {
        if (!"ADMIN".equalsIgnoreCase(requesterRole)) {
            throw new AppException("Only administrators can create users", 403);
        }
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new AppException("Username must not be empty", 400);
        }
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new AppException("Email must not be empty", 400);
        }
        if (request.getPassword() == null || request.getPassword().length() < 8) {
            throw new AppException("Password must be at least 8 characters", 400);
        }
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new AppException("Username already exists: " + request.getUsername(), 409);
        }
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new AppException("Email already exists: " + request.getEmail(), 409);
        }

        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setFullName(request.getFullName());
        user.setRole(request.getRole());
        user.setStatus("ACTIVE");
        user.setPasswordHash(PasswordUtil.hash(request.getPassword()));
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        User saved = userRepository.save(user);
        logger.info("Saved new user with ID: {}", saved.getId());
        return UserMapper.toResponse(saved);
    }

    @Override
    public UserResponse updateUser(String id, UserUpdateRequest request, String requesterId, String requesterRole) {
        User existing = userRepository.findById(id)
                .orElseThrow(() -> new AppException("User not found with id: " + id, 404));

        boolean isSelf = id.equals(requesterId);
        boolean isAdmin = "ADMIN".equalsIgnoreCase(requesterRole);
        if (!isSelf && !isAdmin) {
            throw new AppException("Only the account owner or an administrator can update this user", 403);
        }

        if (request.getEmail() != null) {
            if (request.getEmail().isBlank()) {
                throw new AppException("Email must not be empty", 400);
            }
            userRepository.findByEmail(request.getEmail())
                    .filter(other -> !other.getId().equals(id))
                    .ifPresent(other -> {
                        throw new AppException("Email already exists: " + request.getEmail(), 409);
                    });
            existing.setEmail(request.getEmail());
        }
        if (request.getFullName() != null) {
            existing.setFullName(request.getFullName());
        }
        if (request.getRole() != null) {
            if (!isAdmin) {
                throw new AppException("Only administrators can change role", 403);
            }
            existing.setRole(request.getRole());
        }
        if (request.getStatus() != null) {
            if (!isAdmin) {
                throw new AppException("Only administrators can change status", 403);
            }
            existing.setStatus(request.getStatus());
        }
        if (request.getPassword() != null) {
            if (request.getPassword().length() < 8) {
                throw new AppException("Password must be at least 8 characters", 400);
            }
            existing.setPasswordHash(PasswordUtil.hash(request.getPassword()));
        }
        existing.setUpdatedAt(LocalDateTime.now());

        User saved = userRepository.save(existing);
        logger.info("Updated user with ID: {}", saved.getId());
        return UserMapper.toResponse(saved);
    }

    @Override
    public void deleteUser(String id, String requesterRole) {
        if (!"ADMIN".equalsIgnoreCase(requesterRole)) {
            throw new AppException("Only administrators can delete users", 403);
        }
        boolean deleted = userRepository.deleteById(id);
        if (!deleted) {
            throw new AppException("User not found with id: " + id, 404);
        }
        logger.info("Deleted user with ID: {}", id);
    }
}
