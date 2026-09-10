package com.example.bpmn.service;

import com.example.bpmn.dto.UserRequest;
import com.example.bpmn.dto.UserResponse;
import com.example.bpmn.dto.UserUpdateRequest;

import java.util.List;

public interface UserService {
    List<UserResponse> getAllUsers();
    UserResponse getUserById(String id);
    UserResponse createUser(UserRequest request);
    UserResponse updateUser(String id, UserUpdateRequest request);
    void deleteUser(String id);
}
