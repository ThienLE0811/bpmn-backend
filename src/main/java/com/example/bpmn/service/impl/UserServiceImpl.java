package com.example.bpmn.service.impl;

import com.example.bpmn.dto.UserResponse;
import com.example.bpmn.mapper.UserMapper;
import com.example.bpmn.repository.UserRepository;
import com.example.bpmn.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
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
}
