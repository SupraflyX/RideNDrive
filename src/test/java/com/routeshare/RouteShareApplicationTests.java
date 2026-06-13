package com.routeshare;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * RouteShareApplicationTests runs a high-level integration test that bootstrap-loads the full Spring context.
 *
 * Demonstrates:
 * - Integration Testing (Ch. 10): Validates bean injection and component wiring.
 * - Quality Attributes - Verifiability (Ch. 2).
 */
@SpringBootTest
@ActiveProfiles("test")
class RouteShareApplicationTests {

    @Test
    void contextLoads() {
        // Assert that the Spring container boots successfully without throwing exceptions.
    }
}
