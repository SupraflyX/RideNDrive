package com.routeshare.controller;

import com.routeshare.model.User;
import com.routeshare.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

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

    // no POST here on purpose. registration goes through AuthController, which is the only
    // place that bcrypt hashes the password. a generic POST stored whatever string it was
    // given as the "hash", so the account could never log in

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
