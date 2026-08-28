package com.example.bpmn.controller;

import com.example.bpmn.dto.ApiResponse;
import com.example.bpmn.dto.WorkflowRequest;
import com.example.bpmn.dto.WorkflowResponse;
import com.example.bpmn.exception.AppException;
import com.example.bpmn.service.WorkflowService;
import com.example.bpmn.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Controller handling REST API requests for Workflow resources via JDK HttpServer.
 */
public class WorkflowController implements HttpHandler {
    private static final Logger logger = LoggerFactory.getLogger(WorkflowController.class);
    private final WorkflowService workflowService;

    public WorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Set CORS headers
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");

        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath(); // e.g. /api/workflows or /api/workflows/{id}

        logger.info("Incoming HTTP Request: {} {}", method, path);

        if ("OPTIONS".equalsIgnoreCase(method)) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }

        try {
            // Route: /api/workflows or /api/workflows/{id}
            String[] segments = path.split("/");
            // segments: ["", "api", "workflows"] or ["", "api", "workflows", "id-value"]

            if (segments.length == 3 && "workflows".equals(segments[2])) {
                if ("GET".equalsIgnoreCase(method)) {
                    handleGetAll(exchange);
                } else if ("POST".equalsIgnoreCase(method)) {
                    handleCreate(exchange);
                } else {
                    sendJsonResponse(exchange, 405, ApiResponse.fail("405", "Method Not Allowed"));
                }
            } else if (segments.length == 4 && "workflows".equals(segments[2])) {
                String id = segments[3];
                if ("GET".equalsIgnoreCase(method)) {
                    handleGetById(exchange, id);
                } else if ("DELETE".equalsIgnoreCase(method)) {
                    handleDelete(exchange, id);
                } else {
                    sendJsonResponse(exchange, 405, ApiResponse.fail("405", "Method Not Allowed"));
                }
            } else {
                sendJsonResponse(exchange, 404, ApiResponse.fail("404", "Endpoint Not Found"));
            }
        } catch (AppException e) {
            logger.warn("AppException occurred: {}", e.getMessage());
            sendJsonResponse(exchange, e.getStatusCode(), ApiResponse.fail(e.getErrorCode(), e.getMessage()));
        } catch (Exception e) {
            logger.error("Internal Server Error", e);
            sendJsonResponse(exchange, 500, ApiResponse.fail("500", "Internal Server Error: " + e.getMessage()));
        }
    }

    private void handleGetAll(HttpExchange exchange) throws IOException {
        List<WorkflowResponse> list = workflowService.getAllWorkflows();
        sendJsonResponse(exchange, 200, ApiResponse.ok(list));
    }

    private void handleGetById(HttpExchange exchange, String id) throws IOException {
        WorkflowResponse response = workflowService.getWorkflowById(id);
        sendJsonResponse(exchange, 200, ApiResponse.ok(response));
    }

    private void handleCreate(HttpExchange exchange) throws IOException {
        String requestBody = readRequestBody(exchange.getRequestBody());
        WorkflowRequest request = JsonUtil.fromJson(requestBody, WorkflowRequest.class);
        WorkflowResponse response = workflowService.createWorkflow(request);
        sendJsonResponse(exchange, 201, ApiResponse.ok(response));
    }

    private void handleDelete(HttpExchange exchange, String id) throws IOException {
        workflowService.deleteWorkflow(id);
        sendJsonResponse(exchange, 200, ApiResponse.ok(Map.of("message", "Workflow deleted successfully", "id", id)));
    }

    private String readRequestBody(InputStream inputStream) throws IOException {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }

    private void sendJsonResponse(HttpExchange exchange, int statusCode, Object data) throws IOException {
        String jsonResponse = JsonUtil.toJson(data);
        byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
}
