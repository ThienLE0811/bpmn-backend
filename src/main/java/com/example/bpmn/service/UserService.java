package com.example.bpmn.service;

import com.example.bpmn.dto.UserResponse;

import java.util.List;

public interface UserService {
    List<UserResponse> getAllUsers();
}
