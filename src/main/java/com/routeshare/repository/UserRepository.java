package com.routeshare.repository;

import com.routeshare.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);
}
