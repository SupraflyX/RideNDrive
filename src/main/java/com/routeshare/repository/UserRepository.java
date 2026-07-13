package com.routeshare.repository;

import com.routeshare.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * UserRepository provides DB operations for the User entity.
 *
 * Demonstrates:
 * - Repository Pattern (MVC/Layered architecture): Mediating between domain and data mapping layers.
 * - Component Reuse (CBSE): Reusing standard Spring Data JPA interfaces.
 * - Derived queries: username uniqueness answered by the database instead of
 *   loading every user into memory.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);
}
