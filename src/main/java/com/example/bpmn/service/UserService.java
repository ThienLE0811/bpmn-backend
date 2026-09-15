package com.example.bpmn.service;

import com.example.bpmn.dto.PageResponse;
import com.example.bpmn.dto.UserRequest;
import com.example.bpmn.dto.UserResponse;
import com.example.bpmn.dto.UserUpdateRequest;

public interface UserService {
    PageResponse<UserResponse> getAllUsers(int page, int size);
    UserResponse getUserById(String id);
    UserResponse createUser(UserRequest request, String requesterRole);
    UserResponse updateUser(String id, UserUpdateRequest request, String requesterId, String requesterRole);
    void deleteUser(String id, String requesterRole);
}
