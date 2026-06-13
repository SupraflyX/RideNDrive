package com.routeshare.repository;

import com.routeshare.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * UserRepository provides DB operations for the User entity.
 *
 * Demonstrates:
 * - Repository Pattern (MVC/Layered architecture): Mediating between domain and data mapping layers.
 * - Component Reuse (CBSE): Reusing standard Spring Data JPA interfaces.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
}
