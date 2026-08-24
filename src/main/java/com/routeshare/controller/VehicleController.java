package com.routeshare.controller;

import com.routeshare.model.Vehicle;
import com.routeshare.service.VehicleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {

    private final VehicleService vehicleService;

    @Autowired
    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @GetMapping
    public List<Vehicle> getAllVehicles() {
        return vehicleService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Vehicle> getVehicleById(@PathVariable Long id) {
        return vehicleService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/driver/{driverId}")
    public List<Vehicle> getVehiclesByDriverId(@PathVariable Long driverId) {
        return vehicleService.findByDriverId(driverId);
    }

    // vehicles are created with the driver account, so there is no standalone create here

    @PutMapping("/{id}")
    public ResponseEntity<Vehicle> updateVehicle(@PathVariable Long id,
                                                 @RequestParam Long actorId,
                                                 @RequestBody Vehicle vehicleDetails) {
        Vehicle existing = vehicleService.findById(id).orElse(null);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        Ownership.require(existing.getDriver() == null ? null : existing.getDriver().getId(), actorId, "vehicle");
        return ResponseEntity.ok(vehicleService.update(id, vehicleDetails));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteVehicle(@PathVariable Long id, @RequestParam Long actorId) {
        Vehicle existing = vehicleService.findById(id).orElse(null);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        Ownership.require(existing.getDriver() == null ? null : existing.getDriver().getId(), actorId, "vehicle");
        vehicleService.delete(id);
        return ResponseEntity.ok().build();
    }
}
