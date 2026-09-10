package com.example.bpmn.controller;

import com.example.bpmn.dto.UserRequest;
import com.example.bpmn.dto.UserUpdateRequest;
import com.example.bpmn.http.BaseController;
import com.example.bpmn.http.HttpResult;
import com.example.bpmn.service.UserService;

import java.util.Map;

/**
 * Controller handling REST API requests for User resources.
 */
public class UserController extends BaseController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;

        get("/api/users", ctx -> userService.getAllUsers());
        post("/api/users", ctx -> HttpResult.created(
                userService.createUser(ctx.body(UserRequest.class))));
        get("/api/users/:id", ctx -> userService.getUserById(ctx.param("id")));
        put("/api/users/:id", ctx -> userService.updateUser(
                ctx.param("id"), ctx.body(UserUpdateRequest.class)));
        delete("/api/users/:id", ctx -> {
            String id = ctx.param("id");
            userService.deleteUser(id);
            return Map.of("message", "User deleted successfully", "id", id);
        });
    }
}
