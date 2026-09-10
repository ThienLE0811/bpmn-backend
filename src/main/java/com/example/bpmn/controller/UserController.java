package com.example.bpmn.controller;

import com.example.bpmn.http.BaseController;
import com.example.bpmn.service.UserService;

/**
 * Controller handling REST API requests for User resources.
 */
public class UserController extends BaseController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;

        get("/api/users", ctx -> userService.getAllUsers());
    }
}
