package com.example.bpmn.util;

import com.example.bpmn.config.AppConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Issues and validates the JWT access tokens returned by the login API.
 * Set JWT_SECRET (env var, overrides jwt.secret) in any non-development
 * environment - the built-in default is only safe for local development.
 */
public class JwtUtil {
    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);
    private static final String DEFAULT_SECRET =
            "dev-only-insecure-secret-please-override-via-JWT_SECRET-env-var";

    private static final SecretKey SECRET_KEY;
    private static final long EXPIRATION_MINUTES;

    static {
        String secret = AppConfig.getProperty("jwt.secret", DEFAULT_SECRET);
        String environment = AppConfig.getProperty("app.environment", "development");
        if (DEFAULT_SECRET.equals(secret) && !"development".equalsIgnoreCase(environment)) {
            logger.warn("Using the default JWT secret outside development environment ({}). " +
                    "Set the JWT_SECRET environment variable.", environment);
        }
        SECRET_KEY = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        EXPIRATION_MINUTES = Long.parseLong(AppConfig.getProperty("jwt.expiration-minutes", "60"));
    }

    private JwtUtil() {
    }

    public static String generateToken(String userId, String username, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + EXPIRATION_MINUTES * 60_000);

        return Jwts.builder()
                .subject(userId)
                .claim("username", username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(SECRET_KEY)
                .compact();
    }

    public static long getExpirationSeconds() {
        return EXPIRATION_MINUTES * 60;
    }

    public static Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(SECRET_KEY)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid or expired token", e);
        }
    }
}
