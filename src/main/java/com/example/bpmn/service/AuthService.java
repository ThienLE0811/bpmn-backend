package com.example.bpmn.service;

import com.example.bpmn.dto.LoginRequest;
import com.example.bpmn.dto.LoginResponse;
import com.example.bpmn.dto.LogoutRequest;
import com.example.bpmn.dto.RefreshRequest;
import com.example.bpmn.dto.RefreshResponse;

public interface AuthService {
    LoginResponse login(LoginRequest request);
    RefreshResponse refresh(RefreshRequest request);
    void logout(LogoutRequest request);
}
