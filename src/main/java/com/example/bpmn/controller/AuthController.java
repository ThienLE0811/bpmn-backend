package com.example.bpmn.controller;

import com.example.bpmn.dto.LoginRequest;
import com.example.bpmn.http.BaseController;
import com.example.bpmn.service.AuthService;

/**
 * Controller handling authentication requests.
 */
public class AuthController extends BaseController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;

        postPublic("/api/auth/login", ctx -> authService.login(ctx.body(LoginRequest.class)));
    }
}
