package com.routeshare.controller;

import com.routeshare.model.User;
import com.routeshare.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * UserController exposes REST endpoints for managing Users.
 *
 * Demonstrates:
 * - MVC Architectural Pattern (Controller Layer): Translates HTTP payloads to Service calls.
 * - Client-Server Style: Standard REST API.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<User> getAllUsers() {
        return userService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable Long id) {
        return userService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Account creation lives in AuthController (/api/auth/register-*), which is the only
    // path that BCrypt-hashes the password. A generic POST here stored whatever string
    // the caller sent as the "hash", producing an account that could never log in.

    @PutMapping("/{id}")
    public ResponseEntity<User> updateUser(@PathVariable Long id,
                                           @RequestParam Long actorId,
                                           @RequestBody User userDetails) {
        Ownership.require(id, actorId, "account");
        try {
            return ResponseEntity.ok(userService.update(id, userDetails));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id, @RequestParam Long actorId) {
        Ownership.require(id, actorId, "account");
        userService.delete(id);
        return ResponseEntity.ok().build();
    }
}
