package com.example.bpmn;

import com.example.bpmn.dto.PageResponse;
import com.example.bpmn.dto.UserRequest;
import com.example.bpmn.dto.UserResponse;
import com.example.bpmn.dto.UserUpdateRequest;
import com.example.bpmn.exception.AppException;
import com.example.bpmn.model.User;
import com.example.bpmn.repository.UserRepository;
import com.example.bpmn.service.UserService;
import com.example.bpmn.service.impl.UserServiceImpl;
import com.example.bpmn.util.PasswordUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class UserServiceTest {

    private UserService userService;
    private final Map<String, User> storage = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        storage.clear();
        UserRepository mockRepo = new UserRepository() {
            @Override
            public User save(User user) {
                storage.put(user.getId(), user);
                return user;
            }

            @Override
            public Optional<User> findById(String id) {
                return Optional.ofNullable(storage.get(id));
            }

            @Override
            public Optional<User> findByUsername(String username) {
                return storage.values().stream()
                        .filter(u -> username.equals(u.getUsername()))
                        .findFirst();
            }

            @Override
            public Optional<User> findByEmail(String email) {
                return storage.values().stream()
                        .filter(u -> email.equals(u.getEmail()))
                        .findFirst();
            }

            @Override
            public List<User> findAll() {
                return new ArrayList<>(storage.values());
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
                return storage.size();
            }

            @Override
            public boolean deleteById(String id) {
                return storage.remove(id) != null;
            }
        };

        userService = new UserServiceImpl(mockRepo);
    }

    private User seedUser(String id, String username, String email, String role, String status) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setFullName("Full " + username);
        user.setRole(role);
        user.setStatus(status);
        user.setPasswordHash(PasswordUtil.hash("password123"));
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        storage.put(id, user);
        return user;
    }

    @Test
    @DisplayName("Should return paginated users")
    void testGetAllUsers() {
        seedUser("1", "alice", "alice@example.com", "ADMIN", "ACTIVE");
        seedUser("2", "bob", "bob@example.com", "USER", "ACTIVE");

        PageResponse<UserResponse> result = userService.getAllUsers(1, 20);

        assertEquals(2, result.getContent().size());
        assertEquals(2, result.getTotalElements());
    }

    @Test
    @DisplayName("Should get user by id")
    void testGetUserById() {
        seedUser("1", "alice", "alice@example.com", "ADMIN", "ACTIVE");

        UserResponse response = userService.getUserById("1");

        assertEquals("alice", response.getUsername());
        assertEquals("alice@example.com", response.getEmail());
    }

    @Test
    @DisplayName("Should throw 404 when user not found by id")
    void testGetUserByIdNotFound() {
        AppException ex = assertThrows(AppException.class, () -> userService.getUserById("unknown"));
        assertEquals(404, ex.getStatusCode());
    }

    @Test
    @DisplayName("Should create user when requester is admin")
    void testCreateUserSuccess() {
        UserRequest request = new UserRequest("carol", "carol@example.com", "Carol", "USER", "password123");

        UserResponse response = userService.createUser(request, "ADMIN");

        assertNotNull(response.getId());
        assertEquals("carol", response.getUsername());
        assertEquals("ACTIVE", response.getStatus());
        assertTrue(PasswordUtil.matches("password123", storage.get(response.getId()).getPasswordHash()));
    }

    @Test
    @DisplayName("Should reject create user when requester is not admin")
    void testCreateUserForbiddenForNonAdmin() {
        UserRequest request = new UserRequest("carol", "carol@example.com", "Carol", "USER", "password123");

        AppException ex = assertThrows(AppException.class, () -> userService.createUser(request, "USER"));
        assertEquals(403, ex.getStatusCode());
    }

    @Test
    @DisplayName("Should reject create user with blank username")
    void testCreateUserBlankUsername() {
        UserRequest request = new UserRequest(" ", "carol@example.com", "Carol", "USER", "password123");

        AppException ex = assertThrows(AppException.class, () -> userService.createUser(request, "ADMIN"));
        assertEquals(400, ex.getStatusCode());
    }

    @Test
    @DisplayName("Should reject create user with blank email")
    void testCreateUserBlankEmail() {
        UserRequest request = new UserRequest("carol", " ", "Carol", "USER", "password123");

        AppException ex = assertThrows(AppException.class, () -> userService.createUser(request, "ADMIN"));
        assertEquals(400, ex.getStatusCode());
    }

    @Test
    @DisplayName("Should reject create user with short password")
    void testCreateUserShortPassword() {
        UserRequest request = new UserRequest("carol", "carol@example.com", "Carol", "USER", "short");

        AppException ex = assertThrows(AppException.class, () -> userService.createUser(request, "ADMIN"));
        assertEquals(400, ex.getStatusCode());
    }

    @Test
    @DisplayName("Should reject create user with duplicate username")
    void testCreateUserDuplicateUsername() {
        seedUser("1", "carol", "existing@example.com", "USER", "ACTIVE");
        UserRequest request = new UserRequest("carol", "carol@example.com", "Carol", "USER", "password123");

        AppException ex = assertThrows(AppException.class, () -> userService.createUser(request, "ADMIN"));
        assertEquals(409, ex.getStatusCode());
    }

    @Test
    @DisplayName("Should reject create user with duplicate email")
    void testCreateUserDuplicateEmail() {
        seedUser("1", "existing", "carol@example.com", "USER", "ACTIVE");
        UserRequest request = new UserRequest("carol", "carol@example.com", "Carol", "USER", "password123");

        AppException ex = assertThrows(AppException.class, () -> userService.createUser(request, "ADMIN"));
        assertEquals(409, ex.getStatusCode());
    }

    @Test
    @DisplayName("Should allow self update of email and full name")
    void testUpdateUserSelf() {
        seedUser("1", "alice", "alice@example.com", "USER", "ACTIVE");
        UserUpdateRequest request = new UserUpdateRequest();
        request.setEmail("new@example.com");
        request.setFullName("New Name");

        UserResponse response = userService.updateUser("1", request, "1", "USER");

        assertEquals("new@example.com", response.getEmail());
        assertEquals("New Name", response.getFullName());
    }

    @Test
    @DisplayName("Should reject update from a non-owner, non-admin requester")
    void testUpdateUserForbidden() {
        seedUser("1", "alice", "alice@example.com", "USER", "ACTIVE");
        UserUpdateRequest request = new UserUpdateRequest();
        request.setFullName("Hacked");

        AppException ex = assertThrows(AppException.class, () -> userService.updateUser("1", request, "2", "USER"));
        assertEquals(403, ex.getStatusCode());
    }

    @Test
    @DisplayName("Should throw 404 when updating unknown user")
    void testUpdateUserNotFound() {
        UserUpdateRequest request = new UserUpdateRequest();
        request.setFullName("New Name");

        AppException ex = assertThrows(AppException.class, () -> userService.updateUser("unknown", request, "unknown", "ADMIN"));
        assertEquals(404, ex.getStatusCode());
    }

    @Test
    @DisplayName("Should reject email update to one already used by another user")
    void testUpdateUserDuplicateEmail() {
        seedUser("1", "alice", "alice@example.com", "USER", "ACTIVE");
        seedUser("2", "bob", "bob@example.com", "USER", "ACTIVE");
        UserUpdateRequest request = new UserUpdateRequest();
        request.setEmail("bob@example.com");

        AppException ex = assertThrows(AppException.class, () -> userService.updateUser("1", request, "1", "USER"));
        assertEquals(409, ex.getStatusCode());
    }

    @Test
    @DisplayName("Should reject blank email on update")
    void testUpdateUserBlankEmail() {
        seedUser("1", "alice", "alice@example.com", "USER", "ACTIVE");
        UserUpdateRequest request = new UserUpdateRequest();
        request.setEmail(" ");

        AppException ex = assertThrows(AppException.class, () -> userService.updateUser("1", request, "1", "USER"));
        assertEquals(400, ex.getStatusCode());
    }

    @Test
    @DisplayName("Should reject role change by a non-admin, even the owner")
    void testUpdateUserRoleForbiddenForNonAdmin() {
        seedUser("1", "alice", "alice@example.com", "USER", "ACTIVE");
        UserUpdateRequest request = new UserUpdateRequest();
        request.setRole("ADMIN");

        AppException ex = assertThrows(AppException.class, () -> userService.updateUser("1", request, "1", "USER"));
        assertEquals(403, ex.getStatusCode());
    }

    @Test
    @DisplayName("Should allow admin to change another user's role")
    void testUpdateUserRoleByAdmin() {
        seedUser("1", "alice", "alice@example.com", "USER", "ACTIVE");
        UserUpdateRequest request = new UserUpdateRequest();
        request.setRole("ADMIN");

        UserResponse response = userService.updateUser("1", request, "2", "ADMIN");

        assertEquals("ADMIN", response.getRole());
    }

    @Test
    @DisplayName("Should reject status change by a non-admin, even the owner")
    void testUpdateUserStatusForbiddenForNonAdmin() {
        seedUser("1", "alice", "alice@example.com", "USER", "ACTIVE");
        UserUpdateRequest request = new UserUpdateRequest();
        request.setStatus("INACTIVE");

        AppException ex = assertThrows(AppException.class, () -> userService.updateUser("1", request, "1", "USER"));
        assertEquals(403, ex.getStatusCode());
    }

    @Test
    @DisplayName("Should reject short password on update")
    void testUpdateUserShortPassword() {
        seedUser("1", "alice", "alice@example.com", "USER", "ACTIVE");
        UserUpdateRequest request = new UserUpdateRequest();
        request.setPassword("short");

        AppException ex = assertThrows(AppException.class, () -> userService.updateUser("1", request, "1", "USER"));
        assertEquals(400, ex.getStatusCode());
    }

    @Test
    @DisplayName("Should update password hash on update")
    void testUpdateUserPassword() {
        seedUser("1", "alice", "alice@example.com", "USER", "ACTIVE");
        UserUpdateRequest request = new UserUpdateRequest();
        request.setPassword("newpassword123");

        userService.updateUser("1", request, "1", "USER");

        assertTrue(PasswordUtil.matches("newpassword123", storage.get("1").getPasswordHash()));
    }

    @Test
    @DisplayName("Should delete user as admin")
    void testDeleteUserSuccess() {
        seedUser("1", "alice", "alice@example.com", "USER", "ACTIVE");

        userService.deleteUser("1", "ADMIN");

        assertTrue(storage.isEmpty());
    }

    @Test
    @DisplayName("Should reject delete from a non-admin")
    void testDeleteUserForbidden() {
        seedUser("1", "alice", "alice@example.com", "USER", "ACTIVE");

        AppException ex = assertThrows(AppException.class, () -> userService.deleteUser("1", "USER"));
        assertEquals(403, ex.getStatusCode());
    }

    @Test
    @DisplayName("Should throw 404 when deleting unknown user")
    void testDeleteUserNotFound() {
        AppException ex = assertThrows(AppException.class, () -> userService.deleteUser("unknown", "ADMIN"));
        assertEquals(404, ex.getStatusCode());
    }
}
