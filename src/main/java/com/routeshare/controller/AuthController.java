package com.routeshare.controller;

import com.routeshare.model.User;
import com.routeshare.model.Vehicle;
import com.routeshare.model.enums.UserRole;
import com.routeshare.repository.UserRepository;
import com.routeshare.repository.VehicleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AuthController manages user authentication and registration workflows.
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

    /**
     * Authenticates a user by name and password.
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> payload) {
        String name = payload.get("name");
        String password = payload.get("password");

        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username is required."));
        }
        if (password == null || password.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Password is required."));
        }
        final String searchName = name.trim();

        // Check if user exists
        User user = userRepository.findAll().stream()
                .filter(u -> u.getName().equalsIgnoreCase(searchName))
                .findFirst()
                .orElse(null);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "User not found. Please register first."));
        }

        // Verify password using BCrypt
        if (!org.mindrot.jbcrypt.BCrypt.checkpw(password, user.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid username or password."));
        }

        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "name", user.getName(),
                "role", user.getRole().toString(),
                "reputationScore", user.getReputationScore(),
                "incentiveTier", user.getIncentiveTier().toString()
        ));
    }

    /**
     * Registers a passenger.
     */
    @PostMapping("/register-passenger")
    public ResponseEntity<Map<String, Object>> registerPassenger(@RequestBody Map<String, String> payload) {
        String name = payload.get("name");
        String password = payload.get("password");

        if (name == null || name.trim().isEmpty() || password == null || password.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username and password are required."));
        }
        String cleanName = name.trim();
        boolean exists = userRepository.findAll().stream().anyMatch(u -> u.getName().equalsIgnoreCase(cleanName));
        if (exists) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username is already taken."));
        }

        String hashedPassword = org.mindrot.jbcrypt.BCrypt.hashpw(password, org.mindrot.jbcrypt.BCrypt.gensalt());
        User user = new User(cleanName, UserRole.PASSENGER, hashedPassword);
        user = userRepository.save(user);

        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "name", user.getName(),
                "role", user.getRole().toString(),
                "reputationScore", user.getReputationScore(),
                "incentiveTier", user.getIncentiveTier().toString()
        ));
    }

    /**
     * Registers a driver and their vehicle information.
     */
    @PostMapping("/register-driver")
    public ResponseEntity<Map<String, Object>> registerDriver(@RequestBody Map<String, String> payload) {
        String name = payload.get("name");
        String password = payload.get("password");
        String make = payload.get("make");
        String model = payload.get("model");
        String capacityStr = payload.get("capacity");

        if (name == null || name.trim().isEmpty() || password == null || password.isEmpty() ||
            make == null || make.trim().isEmpty() || model == null || model.trim().isEmpty() || capacityStr == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username, password, and vehicle details are all required."));
        }

        String cleanName = name.trim();
        boolean exists = userRepository.findAll().stream().anyMatch(u -> u.getName().equalsIgnoreCase(cleanName));
        if (exists) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username is already taken."));
        }

        int capacity;
        try {
            capacity = Integer.parseInt(capacityStr);
            if (capacity < 1 || capacity > 8) {
                return ResponseEntity.badRequest().body(Map.of("error", "Seating capacity must be between 1 and 8."));
            }
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid capacity value."));
        }

        String hashedPassword = org.mindrot.jbcrypt.BCrypt.hashpw(password, org.mindrot.jbcrypt.BCrypt.gensalt());
        User user = new User(cleanName, UserRole.DRIVER, hashedPassword);
        user = userRepository.save(user);

        Vehicle vehicle = new Vehicle(user, capacity, make.trim(), model.trim());
        vehicleRepository.save(vehicle);

        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "name", user.getName(),
                "role", user.getRole().toString(),
                "reputationScore", user.getReputationScore(),
                "incentiveTier", user.getIncentiveTier().toString()
        ));
    }
}
