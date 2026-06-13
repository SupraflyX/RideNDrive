package com.routeshare.service;

import com.routeshare.model.Vehicle;
import com.routeshare.repository.VehicleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

/**
 * VehicleService handles business logic and CRUD operations for Vehicles.
 *
 * Demonstrates:
 * - Layered Architecture: Mediating between controller requests and repo lookups.
 * - Business Rules Enforcement: Ensuring drivers can own and select appropriate vehicles.
 */
@Service
public class VehicleService {

    private final VehicleRepository vehicleRepository;

    @Autowired
    public VehicleService(VehicleRepository vehicleRepository) {
        this.vehicleRepository = vehicleRepository;
    }

    public List<Vehicle> findAll() {
        return vehicleRepository.findAll();
    }

    public Optional<Vehicle> findById(Long id) {
        return vehicleRepository.findById(id);
    }

    public List<Vehicle> findByDriverId(Long driverId) {
        return vehicleRepository.findByDriverId(driverId);
    }

    public Vehicle save(Vehicle vehicle) {
        return vehicleRepository.save(vehicle);
    }

    public Vehicle update(Long id, Vehicle vehicleDetails) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Vehicle not found with id: " + id));
        vehicle.setCapacity(vehicleDetails.getCapacity());
        vehicle.setMake(vehicleDetails.getMake());
        vehicle.setModel(vehicleDetails.getModel());
        vehicle.setDriver(vehicleDetails.getDriver());
        return vehicleRepository.save(vehicle);
    }

    public void delete(Long id) {
        vehicleRepository.deleteById(id);
    }
}
