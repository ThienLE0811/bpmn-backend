package com.example.bpmn;

import com.example.bpmn.config.AppConfig;
import com.example.bpmn.config.DatabaseConfig;
import com.example.bpmn.config.RouteConfig;
import com.example.bpmn.container.AppContainer;
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

        // 1. Initialize Database Schema (PostgreSQL)
        try {
            DatabaseConfig.initDatabase();
        } catch (Exception e) {
            logger.error("Failed to connect to Database. Please verify your application.properties settings.", e);
        }

        // 2. Initialize Dependency Container (DI)
        AppContainer container = new AppContainer();

        // 3. Register Shutdown Hook
        Runtime.getRuntime().addShutdownHook(new Thread(DatabaseConfig::close));

        try {
            // 4. Create JDK HttpServer
            HttpServer server = HttpServer.create(new InetSocketAddress(host, port), 0);

            // 5. Register All Routes via RouteConfig
            RouteConfig.registerRoutes(server, container);

            // 6. Set Executor (Java 21 Virtual Threads)
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

            // 7. Start Server
            server.start();

            logger.info("=================================================");
            logger.info("  BPMN Backend Server started successfully!");
            logger.info("  Base URL: http://localhost:{}", port);
            logger.info("=================================================");
        } catch (IOException e) {
            logger.error("Failed to start HTTP server", e);
        }
    }
}
