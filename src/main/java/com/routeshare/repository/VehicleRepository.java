package com.routeshare.repository;

import com.routeshare.model.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * VehicleRepository provides DB operations for the Vehicle entity.
 *
 * Demonstrates:
 * - Repository Pattern: Encapsulates query logic for vehicles.
 * - Modularity: High cohesion for Vehicle data operations.
 */
@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    List<Vehicle> findByDriverId(Long driverId);
}
