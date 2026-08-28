package com.example.bpmn.controller;

import com.example.bpmn.dto.ApiResponse;
import com.example.bpmn.dto.DmnDecisionResponse;
import com.example.bpmn.exception.AppException;
import com.example.bpmn.service.DmnDecisionService;
import com.example.bpmn.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Controller handling REST API requests for DMN Decision resources via JDK HttpServer.
 */
public class DmnDecisionController implements HttpHandler {
    private static final Logger logger = LoggerFactory.getLogger(DmnDecisionController.class);
    private final DmnDecisionService dmnDecisionService;

    public DmnDecisionController(DmnDecisionService dmnDecisionService) {
        this.dmnDecisionService = dmnDecisionService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Set CORS headers
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");

        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath(); // e.g. /api/dmn-decisions or /api/dmn-decisions/{id}

        logger.info("Incoming HTTP Request: {} {}", method, path);

        if ("OPTIONS".equalsIgnoreCase(method)) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }

        try {
            String[] segments = path.split("/");

            if (segments.length == 3 && "dmn-decisions".equals(segments[2])) {
                if ("GET".equalsIgnoreCase(method)) {
                    handleGetAll(exchange);
                } else {
                    sendJsonResponse(exchange, 405, ApiResponse.fail("405", "Method Not Allowed"));
                }
            } else if (segments.length == 4 && "dmn-decisions".equals(segments[2])) {
                String id = segments[3];
                if ("GET".equalsIgnoreCase(method)) {
                    handleGetById(exchange, id);
                } else {
                    sendJsonResponse(exchange, 405, ApiResponse.fail("405", "Method Not Allowed"));
                }
            } else if (segments.length == 5 && "dmn-decisions".equals(segments[2]) && "key".equals(segments[3])) {
                String decisionKey = segments[4];
                if ("GET".equalsIgnoreCase(method)) {
                    handleGetByKey(exchange, decisionKey);
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
        List<DmnDecisionResponse> list = dmnDecisionService.getAllDecisions();
        sendJsonResponse(exchange, 200, ApiResponse.ok(list));
    }

    private void handleGetById(HttpExchange exchange, String id) throws IOException {
        DmnDecisionResponse response = dmnDecisionService.getDecisionById(id);
        sendJsonResponse(exchange, 200, ApiResponse.ok(response));
    }

    private void handleGetByKey(HttpExchange exchange, String decisionKey) throws IOException {
        DmnDecisionResponse response = dmnDecisionService.getDecisionByKey(decisionKey);
        sendJsonResponse(exchange, 200, ApiResponse.ok(response));
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
