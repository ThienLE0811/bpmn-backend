package com.example.bpmn.repository;

import com.example.bpmn.model.User;
import java.util.List;
import java.util.Optional;

public interface UserRepository {
    User save(User user);
    Optional<User> findById(String id);
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    List<User> findAll();
    List<User> findPage(int limit, int offset);
    long count();
    boolean deleteById(String id);
}
