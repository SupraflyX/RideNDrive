package com.routeshare;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * RouteShareApplication is the main entry point of the commuter carpooling platform.
 *
 * Demonstrates:
 * - Spring Boot Web Application Framework (CBSE / Ch. 10 concept of reusable architecture)
 * - Inversion of Control (IoC) and Dependency Injection (DI) as Creational Factory structures
 * - Separation of Concerns (SE Principle 2) by bootstrap-initializing the MVC layered system
 *
 * @author Mohammad Haroon (560824)
 */
@SpringBootApplication
public class RouteShareApplication {
    public static void main(String[] args) {
        SpringApplication.run(RouteShareApplication.class, args);
    }
}
