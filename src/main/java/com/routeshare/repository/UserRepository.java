package com.routeshare.repository;

import com.routeshare.model.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository
extends JpaRepository<User, Long> {
    public Optional<User> findByNameIgnoreCase(String var1);

    public boolean existsByNameIgnoreCase(String var1);
}
