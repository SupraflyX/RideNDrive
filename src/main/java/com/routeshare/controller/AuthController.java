package com.routeshare.controller;

import com.routeshare.model.User;
import com.routeshare.model.Vehicle;
import com.routeshare.model.enums.UserRole;
import com.routeshare.repository.UserRepository;
import com.routeshare.repository.VehicleRepository;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AuthController manages user authentication and registration workflows.
 *
 * Demonstrates:
 * - Security (NFR-2): salted BCrypt hashing; credential material never leaves the server.
 * - Repository derived queries: username uniqueness is answered by the database
 *   (existsByNameIgnoreCase) instead of scanning all users in memory.
 * - DRY: shared helpers for hashing and profile responses across the three flows.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;

    @Autowired
    public AuthController(UserRepository userRepository, VehicleRepository vehicleRepository) {
        this.userRepository = userRepository;
        this.vehicleRepository = vehicleRepository;
    }

    /** Authenticates a user by name and password. */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> payload) {
        String name = payload.get("name");
        String password = payload.get("password");

        if (isBlank(name)) {
            return badRequest("Username is required.");
        }
        if (password == null || password.isEmpty()) {
            return badRequest("Password is required.");
        }

        User user = userRepository.findByNameIgnoreCase(name.trim()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "User not found. Please register first."));
        }

        if (!BCrypt.checkpw(password, user.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid username or password."));
        }

        return ResponseEntity.ok(profileResponse(user));
    }

    /** Registers a passenger. */
    @PostMapping("/register-passenger")
    public ResponseEntity<Map<String, Object>> registerPassenger(@RequestBody Map<String, String> payload) {
        String name = payload.get("name");
        String password = payload.get("password");

        if (isBlank(name) || password == null || password.isEmpty()) {
            return badRequest("Username and password are required.");
        }
        String cleanName = name.trim();
        if (userRepository.existsByNameIgnoreCase(cleanName)) {
            return badRequest("Username is already taken.");
        }

        User user = userRepository.save(new User(cleanName, UserRole.PASSENGER, hash(password)));
        return ResponseEntity.ok(profileResponse(user));
    }

    /** Registers a driver and their vehicle information. */
    @PostMapping("/register-driver")
    public ResponseEntity<Map<String, Object>> registerDriver(@RequestBody Map<String, String> payload) {
        String name = payload.get("name");
        String password = payload.get("password");
        String make = payload.get("make");
        String model = payload.get("model");
        String capacityStr = payload.get("capacity");

        if (isBlank(name) || password == null || password.isEmpty()
                || isBlank(make) || isBlank(model) || capacityStr == null) {
            return badRequest("Username, password, and vehicle details are all required.");
        }

        String cleanName = name.trim();
        if (userRepository.existsByNameIgnoreCase(cleanName)) {
            return badRequest("Username is already taken.");
        }

        int capacity;
        try {
            capacity = Integer.parseInt(capacityStr);
        } catch (NumberFormatException e) {
            return badRequest("Invalid capacity value.");
        }
        if (capacity < 1 || capacity > 8) {
            return badRequest("Seating capacity must be between 1 and 8.");
        }

        User user = userRepository.save(new User(cleanName, UserRole.DRIVER, hash(password)));
        vehicleRepository.save(new Vehicle(user, capacity, make.trim(), model.trim()));

        return ResponseEntity.ok(profileResponse(user));
    }

    // ── shared helpers ───────────────────────────────────────────────

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String hash(String rawPassword) {
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt());
    }

    private static ResponseEntity<Map<String, Object>> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    /** The public profile shape returned by every auth flow — no credential material. */
    private static Map<String, Object> profileResponse(User user) {
        return Map.of(
                "id", user.getId(),
                "name", user.getName(),
                "role", user.getRole().toString(),
                "reputationScore", user.getReputationScore(),
                "incentiveTier", user.getIncentiveTier().toString()
        );
    }
}
