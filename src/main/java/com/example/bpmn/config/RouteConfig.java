package com.example.bpmn.config;

import com.example.bpmn.container.AppContainer;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Centralized Route Registry for HTTP Server.
 * Routes are modularized by domain (BPMN, DMN, User, Workflow, Task, etc.).
 */
public class RouteConfig {
    private static final Logger logger = LoggerFactory.getLogger(RouteConfig.class);

    private RouteConfig() {
    }

    /**
     * Entry point to register all routes across modules.
     */
    public static void registerRoutes(HttpServer server, AppContainer container) {
        logger.info("Registering API routes across modules...");

        registerHealthRoute(server);
        registerAuthRoutes(server, container);
        registerWorkflowRoutes(server, container);
        registerBpmnRoutes(server, container);
        registerDmnRoutes(server, container);
        registerUserRoutes(server, container);
        registerTaskRoutes(server, container);

        logger.info("All API routes registered successfully.");
    }

    /**
     * Health check endpoint for the hosting platform (Render) to poll.
     */
    private static void registerHealthRoute(HttpServer server) {
        server.createContext("/health", exchange -> {
            byte[] body = "{\"status\":\"OK\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            } catch (IOException e) {
                logger.warn("Failed to write /health response", e);
            }
        });
        logger.info("  [Health] Registered: /health");
    }

    /**
     * Auth Module Routes
     */
    private static void registerAuthRoutes(HttpServer server, AppContainer container) {
        server.createContext("/api/auth", container.getAuthController());
        logger.info("  [Auth] Registered: /api/auth");
    }

    /**
     * BPMN Module Routes
     */
    private static void registerBpmnRoutes(HttpServer server, AppContainer container) {
        server.createContext("/api/bpmn-processes", container.getBpmnProcessController());
        logger.info("  [BPMN] Registered: /api/bpmn-processes");
    }

    /**
     * Workflow Module Routes
     */
    private static void registerWorkflowRoutes(HttpServer server, AppContainer container) {
        server.createContext("/api/workflows", container.getWorkflowController());
        logger.info("  [Workflow] Registered: /api/workflows");
    }

    /**
     * DMN (Decision Model and Notation) Module Routes
     */
    private static void registerDmnRoutes(HttpServer server, AppContainer container) {
        server.createContext("/api/dmn-decisions", container.getDmnDecisionController());
        logger.info("  [DMN] Registered: /api/dmn-decisions");
    }

    /**
     * User Module Routes
     */
    private static void registerUserRoutes(HttpServer server, AppContainer container) {
        server.createContext("/api/users", container.getUserController());
        logger.info("  [User] Registered: /api/users");
    }

    /**
     * Task Module Routes (placeholder for Task endpoints)
     */
    private static void registerTaskRoutes(HttpServer server, AppContainer container) {
        // Ví dụ sau này khi có TaskController:
        // server.createContext("/api/tasks", container.getTaskController());
        // logger.info("  [Task] Registered: /api/tasks");
    }
}
