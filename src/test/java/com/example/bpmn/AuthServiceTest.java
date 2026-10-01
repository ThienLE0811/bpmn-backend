package com.example.bpmn;

import com.example.bpmn.dto.LoginRequest;
import com.example.bpmn.dto.LoginResponse;
import com.example.bpmn.dto.LogoutRequest;
import com.example.bpmn.dto.RefreshRequest;
import com.example.bpmn.dto.RefreshResponse;
import com.example.bpmn.exception.AppException;
import com.example.bpmn.model.RefreshToken;
import com.example.bpmn.model.User;
import com.example.bpmn.repository.RefreshTokenRepository;
import com.example.bpmn.repository.UserRepository;
import com.example.bpmn.service.AuthService;
import com.example.bpmn.service.impl.AuthServiceImpl;
import com.example.bpmn.util.JwtUtil;
import com.example.bpmn.util.PasswordUtil;
import com.example.bpmn.util.RefreshTokenUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest {

    private AuthService authService;
    private final Map<String, User> users = new ConcurrentHashMap<>();
    private final Map<String, RefreshToken> refreshTokens = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        users.clear();
        refreshTokens.clear();

        UserRepository mockUserRepo = new UserRepository() {
            @Override
            public User save(User user) {
                users.put(user.getId(), user);
                return user;
            }

            @Override
            public Optional<User> findById(String id) {
                return Optional.ofNullable(users.get(id));
            }

            @Override
            public Optional<User> findByUsername(String username) {
                return users.values().stream()
                        .filter(u -> username.equals(u.getUsername()))
                        .findFirst();
            }

            @Override
            public Optional<User> findByEmail(String email) {
                return users.values().stream()
                        .filter(u -> email.equals(u.getEmail()))
                        .findFirst();
            }

            @Override
            public List<User> findAll() {
                return new ArrayList<>(users.values());
            }

            @Override
            public List<User> findPage(int limit, int offset) {
                List<User> all = findAll();
                int from = Math.min(offset, all.size());
                int to = Math.min(offset + limit, all.size());
                return new ArrayList<>(all.subList(from, to));
            }

            @Override
            public long count() {
                return users.size();
            }

            @Override
            public boolean deleteById(String id) {
                return users.remove(id) != null;
            }
        };

        RefreshTokenRepository mockRefreshTokenRepo = new RefreshTokenRepository() {
            @Override
            public RefreshToken save(RefreshToken token) {
                refreshTokens.put(token.getId(), token);
                return token;
            }

            @Override
            public Optional<RefreshToken> findByTokenHash(String tokenHash) {
                return refreshTokens.values().stream()
                        .filter(t -> tokenHash.equals(t.getTokenHash()))
                        .findFirst();
            }

            @Override
            public void revoke(String id) {
                RefreshToken token = refreshTokens.get(id);
                if (token != null) {
                    token.setRevoked(true);
                }
            }
        };

        authService = new AuthServiceImpl(mockUserRepo, mockRefreshTokenRepo);
    }

    private User activeUser(String username, String password) {
        User user = new User(UUID.randomUUID().toString(), username, username + "@example.com", "Full Name", "USER", "ACTIVE");
        user.setPasswordHash(PasswordUtil.hash(password));
        users.put(user.getId(), user);
        return user;
    }

    @Test
    @DisplayName("Should log in with valid credentials and return a signed access token plus refresh token")
    void testLoginSuccess() {
        User user = activeUser("alice", "password123");

        LoginResponse response = authService.login(loginRequest("alice", "password123"));

        assertNotNull(response.getAccessToken());
        assertNotNull(response.getRefreshToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(JwtUtil.getExpirationSeconds(), response.getExpiresIn());
        assertEquals(user.getId(), response.getUser().getId());

        Claims claims = JwtUtil.parseToken(response.getAccessToken());
        assertEquals(user.getId(), claims.getSubject());
        assertEquals("alice", claims.get("username"));
        assertEquals("USER", claims.get("role"));

        RefreshToken stored = refreshTokens.values().iterator().next();
        assertEquals(user.getId(), stored.getUserId());
        assertEquals(RefreshTokenUtil.hash(response.getRefreshToken()), stored.getTokenHash());
        assertFalse(stored.isRevoked());
    }

    @Test
    @DisplayName("Should reject login with blank username or password")
    void testLoginBlankCredentials() {
        AppException ex = assertThrows(AppException.class, () -> authService.login(loginRequest(" ", "password123")));
        assertEquals(400, ex.getStatusCode());
    }

    @Test
    @DisplayName("Should reject login for unknown username")
    void testLoginUnknownUsername() {
        AppException ex = assertThrows(AppException.class, () -> authService.login(loginRequest("nobody", "password123")));
        assertEquals(401, ex.getStatusCode());
    }

    @Test
    @DisplayName("Should reject login with wrong password")
    void testLoginWrongPassword() {
        activeUser("alice", "password123");

        AppException ex = assertThrows(AppException.class, () -> authService.login(loginRequest("alice", "wrongpass")));
        assertEquals(401, ex.getStatusCode());
    }

    @Test
    @DisplayName("Should reject login for an inactive user")
    void testLoginInactiveUser() {
        User user = activeUser("alice", "password123");
        user.setStatus("INACTIVE");

        AppException ex = assertThrows(AppException.class, () -> authService.login(loginRequest("alice", "password123")));
        assertEquals(403, ex.getStatusCode());
    }

    @Test
    @DisplayName("Should refresh an access token given a valid refresh token")
    void testRefreshSuccess() {
        User user = activeUser("alice", "password123");
        LoginResponse login = authService.login(loginRequest("alice", "password123"));

        RefreshResponse response = authService.refresh(refreshRequest(login.getRefreshToken()));

        assertNotNull(response.getAccessToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(JwtUtil.getExpirationSeconds(), response.getExpiresIn());

        Claims claims = JwtUtil.parseToken(response.getAccessToken());
        assertEquals(user.getId(), claims.getSubject());
    }

    @Test
    @DisplayName("Should reject refresh with a blank refresh token")
    void testRefreshBlankToken() {
        AppException ex = assertThrows(AppException.class, () -> authService.refresh(refreshRequest(" ")));
        assertEquals(400, ex.getStatusCode());
    }

    @Test
    @DisplayName("Should reject refresh with an unknown refresh token")
    void testRefreshUnknownToken() {
        AppException ex = assertThrows(AppException.class, () -> authService.refresh(refreshRequest("not-a-real-token")));
        assertEquals(401, ex.getStatusCode());
    }

    @Test
    @DisplayName("Should reject refresh with an expired refresh token")
    void testRefreshExpiredToken() {
        activeUser("alice", "password123");
        LoginResponse login = authService.login(loginRequest("alice", "password123"));
        RefreshToken stored = refreshTokens.values().iterator().next();
        stored.setExpiresAt(LocalDateTime.now().minusDays(1));

        AppException ex = assertThrows(AppException.class, () -> authService.refresh(refreshRequest(login.getRefreshToken())));
        assertEquals(401, ex.getStatusCode());
    }

    @Test
    @DisplayName("Should reject refresh with a revoked refresh token")
    void testRefreshRevokedToken() {
        activeUser("alice", "password123");
        LoginResponse login = authService.login(loginRequest("alice", "password123"));
        RefreshToken stored = refreshTokens.values().iterator().next();
        stored.setRevoked(true);

        AppException ex = assertThrows(AppException.class, () -> authService.refresh(refreshRequest(login.getRefreshToken())));
        assertEquals(401, ex.getStatusCode());
    }

    @Test
    @DisplayName("Should reject refresh when the owning user is no longer active")
    void testRefreshInactiveUser() {
        User user = activeUser("alice", "password123");
        LoginResponse login = authService.login(loginRequest("alice", "password123"));
        user.setStatus("INACTIVE");

        AppException ex = assertThrows(AppException.class, () -> authService.refresh(refreshRequest(login.getRefreshToken())));
        assertEquals(403, ex.getStatusCode());
    }

    @Test
    @DisplayName("Should revoke the stored refresh token on logout")
    void testLogoutRevokesToken() {
        activeUser("alice", "password123");
        LoginResponse login = authService.login(loginRequest("alice", "password123"));

        authService.logout(logoutRequest(login.getRefreshToken()));

        RefreshToken stored = refreshTokens.values().iterator().next();
        assertTrue(stored.isRevoked());
    }

    @Test
    @DisplayName("Should not throw when logging out with a blank or unknown refresh token")
    void testLogoutIgnoresBlankOrUnknownToken() {
        assertDoesNotThrow(() -> authService.logout(logoutRequest(" ")));
        assertDoesNotThrow(() -> authService.logout(logoutRequest(null)));
        assertDoesNotThrow(() -> authService.logout(logoutRequest("not-a-real-token")));
    }

    private LoginRequest loginRequest(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }

    private RefreshRequest refreshRequest(String refreshToken) {
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken(refreshToken);
        return request;
    }

    private LogoutRequest logoutRequest(String refreshToken) {
        LogoutRequest request = new LogoutRequest();
        request.setRefreshToken(refreshToken);
        return request;
    }
}
