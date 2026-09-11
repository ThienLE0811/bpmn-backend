package com.example.bpmn.service;

import com.example.bpmn.dto.LoginRequest;
import com.example.bpmn.dto.LoginResponse;

public interface AuthService {
    LoginResponse login(LoginRequest request);
}
