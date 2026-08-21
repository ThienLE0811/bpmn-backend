package com.example.bpmn;

import com.example.bpmn.config.AppConfig;
import com.example.bpmn.controller.WorkflowController;
import com.example.bpmn.repository.impl.InMemoryWorkflowRepository;
import com.example.bpmn.service.impl.WorkflowServiceImpl;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        String host = AppConfig.getProperty("server.host", "0.0.0.0");
        int port = Integer.parseInt(AppConfig.getProperty("server.port", "8080"));

        logger.info("Initializing BPMN Backend Application...");

        // 1. Dependency Injection setup
        var repository = new InMemoryWorkflowRepository();
        var service = new WorkflowServiceImpl(repository);
        var workflowController = new WorkflowController(service);

        try {
            // 2. Create JDK HttpServer
            HttpServer server = HttpServer.create(new InetSocketAddress(host, port), 0);

            // 3. Register Routes / Contexts
            server.createContext("/api/workflows", workflowController);

            // 4. Set Executor (Java 21 Virtual Threads)
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

            // 5. Start Server
            server.start();

            logger.info("=================================================");
            logger.info("  BPMN Backend Server started successfully!");
            logger.info("  Listening at: http://localhost:{}/api/workflows", port);
            logger.info("=================================================");
        } catch (IOException e) {
            logger.error("Failed to start HTTP server", e);
        }
    }
}
