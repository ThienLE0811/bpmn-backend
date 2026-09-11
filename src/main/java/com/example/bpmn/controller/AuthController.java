package com.example.bpmn.controller;

import com.example.bpmn.dto.LoginRequest;
import com.example.bpmn.dto.LogoutRequest;
import com.example.bpmn.dto.RefreshRequest;
import com.example.bpmn.http.BaseController;
import com.example.bpmn.service.AuthService;

import java.util.Map;

/**
 * Controller handling authentication requests.
 */
public class AuthController extends BaseController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;

        postPublic("/api/auth/login", ctx -> authService.login(ctx.body(LoginRequest.class)));
        postPublic("/api/auth/refresh", ctx -> authService.refresh(ctx.body(RefreshRequest.class)));
        postPublic("/api/auth/logout", ctx -> {
            authService.logout(ctx.body(LogoutRequest.class));
            return Map.of("message", "Logged out successfully");
        });
    }
}
