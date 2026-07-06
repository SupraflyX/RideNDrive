package com.routeshare.service;

import com.routeshare.model.*;
import com.routeshare.model.enums.UserRole;
import com.routeshare.model.dto.StopSequenceResult;
import com.routeshare.service.integration.MappingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * StopPlanningServiceTest verifies the constrained DFS routing sequencing algorithm.
 *
 * Demonstrates:
 * - V&V / Quality Correctness (Ch. 2, Ch. 5): Proving functional requirements via automated tests.
 * - Equivalence Partitioning (Ch. 10): Testing boundaries of capacity, stops, time-windows, and detours.
 */
public class StopPlanningServiceTest {

    private MappingService mappingService;
    private StopPlanningService stopPlanningService;

    private User driver;
    private Vehicle vehicle;
    private TripOffer offer;

    private User passenger1;
    private User passenger2;
    private RideRequest request1;
    private RideRequest request2;

    @BeforeEach
    public void setUp() {
        mappingService = Mockito.mock(MappingService.class);
        stopPlanningService = new StopPlanningService(mappingService);

        driver = new User("Alice (Driver)", UserRole.DRIVER);
        driver.setId(1L);

        vehicle = new Vehicle(driver, 4, "Tesla", "Model 3");

        offer = new TripOffer(
                driver,
                "ZoneA",
                "ZoneB",
                LocalDateTime.of(2026, 6, 2, 8, 0),
                3, // maxStops
                30 // maxDetourMinutes
        );
        offer.setId(10L);

        passenger1 = new User("Bob (Passenger)", UserRole.PASSENGER);
        passenger1.setId(2L);

        passenger2 = new User("Charlie (Passenger)", UserRole.PASSENGER);
        passenger2.setId(3L);

        // Bob wants to go from ZoneC to ZoneD
        request1 = new RideRequest(
                passenger1,
                "ZoneC",
                "ZoneD",
                LocalDateTime.of(2026, 6, 2, 8, 0),
                LocalDateTime.of(2026, 6, 2, 8, 30)
        );
        request1.setId(20L);

        // Charlie wants to go from ZoneE to ZoneF
        request2 = new RideRequest(
                passenger2,
                "ZoneE",
                "ZoneF",
                LocalDateTime.of(2026, 6, 2, 8, 0),
                LocalDateTime.of(2026, 6, 2, 8, 45)
        );
        request2.setId(21L);
    }

    @Test
    public void testFindsOptimalSequence() {
        // Direct route: ZoneA -> ZoneB is 15 minutes, 10.0 km
        when(mappingService.getTravelTimeMinutes("ZoneA", "ZoneB")).thenReturn(15);
        when(mappingService.getDistanceKm("ZoneA", "ZoneB")).thenReturn(10.0);

        // Passenger Bob: ZoneA -> ZoneC (5 mins), ZoneC -> ZoneD (5 mins), ZoneD -> ZoneB (5 mins)
        when(mappingService.getTravelTimeMinutes("ZoneA", "ZoneC")).thenReturn(5);
        when(mappingService.getDistanceKm("ZoneA", "ZoneC")).thenReturn(3.0);

        when(mappingService.getTravelTimeMinutes("ZoneC", "ZoneD")).thenReturn(5);
        when(mappingService.getDistanceKm("ZoneC", "ZoneD")).thenReturn(3.0);

        when(mappingService.getTravelTimeMinutes("ZoneD", "ZoneB")).thenReturn(5);
        when(mappingService.getDistanceKm("ZoneD", "ZoneB")).thenReturn(3.0);

        StopSequenceResult result = stopPlanningService.planRoute(offer, Arrays.asList(request1), vehicle);

        assertTrue(result.isFeasible());
        assertEquals(15, result.getTotalTimeMinutes()); // 5 + 5 + 5
        assertEquals(9.0, result.getTotalDistanceKm()); // 3 + 3 + 3
        assertEquals(4, result.getSequence().size());
        assertEquals("Origin: ZoneA", result.getSequence().get(0));
        assertEquals("PICKUP(Bob (Passenger)) at ZoneC", result.getSequence().get(1));
        assertEquals("DROPOFF(Bob (Passenger)) at ZoneD", result.getSequence().get(2));
        assertEquals("Destination: ZoneB", result.getSequence().get(3));
    }

    @Test
    public void testRejectsOverCapacity() {
        // Limit capacity to 1
        vehicle.setCapacity(1);

        when(mappingService.getTravelTimeMinutes("ZoneA", "ZoneB")).thenReturn(15);

        // Setup mappings such that picking up both Bob (ZoneC) and Charlie (ZoneE) before dropping off is short,
        // but overflows capacity. Bob must be dropped off before Charlie is picked up.
        when(mappingService.getTravelTimeMinutes("ZoneA", "ZoneC")).thenReturn(5);
        when(mappingService.getTravelTimeMinutes("ZoneC", "ZoneE")).thenReturn(5); // pickup Charlie -> Capacity exceeded!
        when(mappingService.getTravelTimeMinutes("ZoneC", "ZoneD")).thenReturn(5); // Bob dropoff
        when(mappingService.getTravelTimeMinutes("ZoneD", "ZoneE")).thenReturn(5); // Charlie pickup
        when(mappingService.getTravelTimeMinutes("ZoneE", "ZoneF")).thenReturn(5); // Charlie dropoff
        when(mappingService.getTravelTimeMinutes("ZoneF", "ZoneB")).thenReturn(5);

        // Stub alternative routes to be slow/infeasible, forcing Bob to be scheduled before Charlie
        when(mappingService.getTravelTimeMinutes("ZoneA", "ZoneE")).thenReturn(50);
        when(mappingService.getTravelTimeMinutes("ZoneF", "ZoneC")).thenReturn(50);

        StopSequenceResult result = stopPlanningService.planRoute(offer, Arrays.asList(request1, request2), vehicle);

        assertTrue(result.isFeasible());
        // Verify Charlie is not picked up before Bob is dropped off
        int indexOfBobPickup = -1;
        int indexOfBobDropoff = -1;
        int indexOfCharliePickup = -1;

        for (int i = 0; i < result.getSequence().size(); i++) {
            String step = result.getSequence().get(i);
            if (step.contains("PICKUP(Bob")) indexOfBobPickup = i;
            if (step.contains("DROPOFF(Bob")) indexOfBobDropoff = i;
            if (step.contains("PICKUP(Charlie")) indexOfCharliePickup = i;
        }

        assertTrue(indexOfBobPickup < indexOfBobDropoff);
        assertTrue(indexOfBobDropoff < indexOfCharliePickup, "Bob must be dropped off before Charlie is picked up to satisfy capacity constraint of 1.");
    }

    @Test
    public void testRespectsMaxStops() {
        // Driver only wants 1 stop (pickup)
        offer.setMaxStops(1);

        when(mappingService.getTravelTimeMinutes("ZoneA", "ZoneB")).thenReturn(15);
        when(mappingService.getTravelTimeMinutes("ZoneA", "ZoneC")).thenReturn(5);
        when(mappingService.getTravelTimeMinutes("ZoneC", "ZoneD")).thenReturn(5);
        when(mappingService.getTravelTimeMinutes("ZoneD", "ZoneB")).thenReturn(5);

        // We supply 2 requests, but because of maxStops = 1, we can only schedule 1 passenger.
        StopSequenceResult result = stopPlanningService.planRoute(offer, Arrays.asList(request1, request2), vehicle);

        assertTrue(result.isFeasible());
        // The sequence size should be 4 (Origin + Pickup Bob + Dropoff Bob + Destination)
        assertEquals(4, result.getSequence().size());
    }

    @Test
    public void testRespectsTimeWindow() {
        when(mappingService.getTravelTimeMinutes("ZoneA", "ZoneB")).thenReturn(15);
        // Driver arrives at Bob's pickup at 8:40 (departure 8:00 + travel 40 mins)
        // But Bob's time window ends at 8:30. This is a time window violation!
        when(mappingService.getTravelTimeMinutes("ZoneA", "ZoneC")).thenReturn(40);

        StopSequenceResult result = stopPlanningService.planRoute(offer, Arrays.asList(request1), vehicle);

        assertFalse(result.isFeasible());
        assertNotNull(result.getViolationReason());
    }

    @Test
    public void testNoFeasibleRoute() {
        // Driver detour budget is 5 minutes
        offer.setMaxDetourMinutes(5);

        when(mappingService.getTravelTimeMinutes("ZoneA", "ZoneB")).thenReturn(10);
        // Bob route takes 5 + 5 + 10 = 20 minutes (detour is 10 minutes > 5 minutes budget)
        when(mappingService.getTravelTimeMinutes("ZoneA", "ZoneC")).thenReturn(5);
        when(mappingService.getTravelTimeMinutes("ZoneC", "ZoneD")).thenReturn(5);
        when(mappingService.getTravelTimeMinutes("ZoneD", "ZoneB")).thenReturn(10);

        StopSequenceResult result = stopPlanningService.planRoute(offer, Arrays.asList(request1), vehicle);

        assertFalse(result.isFeasible());
    }
}
