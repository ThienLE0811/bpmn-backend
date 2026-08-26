package com.example.bpmn.controller;

import com.example.bpmn.dto.BpmnProcessResponse;
import com.example.bpmn.exception.AppException;
import com.example.bpmn.service.BpmnProcessService;
import com.example.bpmn.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Controller handling REST API requests for BPMN Process resources via JDK HttpServer.
 */
public class BpmnProcessController implements HttpHandler {
    private static final Logger logger = LoggerFactory.getLogger(BpmnProcessController.class);
    private final BpmnProcessService bpmnProcessService;

    public BpmnProcessController(BpmnProcessService bpmnProcessService) {
        this.bpmnProcessService = bpmnProcessService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Set CORS headers
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");

        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath(); // e.g. /api/bpmn-processes or /api/bpmn-processes/{id}

        logger.info("Incoming HTTP Request: {} {}", method, path);

        if ("OPTIONS".equalsIgnoreCase(method)) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }

        try {
            String[] segments = path.split("/");

            if (segments.length == 3 && "bpmn-processes".equals(segments[2])) {
                if ("GET".equalsIgnoreCase(method)) {
                    handleGetAll(exchange);
                } else {
                    sendJsonResponse(exchange, 405, Map.of("error", "Method Not Allowed"));
                }
            } else if (segments.length == 4 && "bpmn-processes".equals(segments[2])) {
                String id = segments[3];
                if ("GET".equalsIgnoreCase(method)) {
                    handleGetById(exchange, id);
                } else {
                    sendJsonResponse(exchange, 405, Map.of("error", "Method Not Allowed"));
                }
            } else if (segments.length == 5 && "bpmn-processes".equals(segments[2]) && "key".equals(segments[3])) {
                String processKey = segments[4];
                if ("GET".equalsIgnoreCase(method)) {
                    handleGetByKey(exchange, processKey);
                } else {
                    sendJsonResponse(exchange, 405, Map.of("error", "Method Not Allowed"));
                }
            } else {
                sendJsonResponse(exchange, 404, Map.of("error", "Endpoint Not Found"));
            }
        } catch (AppException e) {
            logger.warn("AppException occurred: {}", e.getMessage());
            sendJsonResponse(exchange, e.getStatusCode(), Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Internal Server Error", e);
            sendJsonResponse(exchange, 500, Map.of("error", "Internal Server Error: " + e.getMessage()));
        }
    }

    private void handleGetAll(HttpExchange exchange) throws IOException {
        List<BpmnProcessResponse> list = bpmnProcessService.getAllProcesses();
        sendJsonResponse(exchange, 200, list);
    }

    private void handleGetById(HttpExchange exchange, String id) throws IOException {
        BpmnProcessResponse response = bpmnProcessService.getProcessById(id);
        sendJsonResponse(exchange, 200, response);
    }

    private void handleGetByKey(HttpExchange exchange, String processKey) throws IOException {
        BpmnProcessResponse response = bpmnProcessService.getProcessByKey(processKey);
        sendJsonResponse(exchange, 200, response);
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
