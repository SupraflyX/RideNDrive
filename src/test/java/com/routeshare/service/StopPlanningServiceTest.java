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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/* checks the stop planner: that it finds the shortest working order, and that each
   of the five constraints actually rejects what it should */
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

        request1 = new RideRequest(
                passenger1,
                "ZoneC",
                "ZoneD",
                LocalDateTime.of(2026, 6, 2, 8, 0),
                LocalDateTime.of(2026, 6, 2, 8, 30)
        );
        request1.setId(20L);

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
        vehicle.setCapacity(1);

        when(mappingService.getTravelTimeMinutes("ZoneA", "ZoneB")).thenReturn(15);

        /* Setup mappings such that picking up both Bob (ZoneC) and Charlie (ZoneE) before dropping off is short,
           but overflows capacity. Bob must be dropped off before Charlie is picked up. */
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
        /* Driver arrives at Bob's pickup at 8:40 (departure 8:00 + travel 40 mins)
           But Bob's time window ends at 8:30. This is a time window violation! */
        when(mappingService.getTravelTimeMinutes("ZoneA", "ZoneC")).thenReturn(40);

        StopSequenceResult result = stopPlanningService.planRoute(offer, Arrays.asList(request1), vehicle);

        assertFalse(result.isFeasible());
        assertNotNull(result.getViolationReason());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSearchStatsAreReportedAndConsistent() {
        // Same happy-path fixture as testFindsOptimalSequence
        when(mappingService.getTravelTimeMinutes("ZoneA", "ZoneB")).thenReturn(15);
        when(mappingService.getDistanceKm("ZoneA", "ZoneB")).thenReturn(10.0);
        when(mappingService.getTravelTimeMinutes("ZoneA", "ZoneC")).thenReturn(5);
        when(mappingService.getDistanceKm("ZoneA", "ZoneC")).thenReturn(3.0);
        when(mappingService.getTravelTimeMinutes("ZoneC", "ZoneD")).thenReturn(5);
        when(mappingService.getDistanceKm("ZoneC", "ZoneD")).thenReturn(3.0);
        when(mappingService.getTravelTimeMinutes("ZoneD", "ZoneB")).thenReturn(5);
        when(mappingService.getDistanceKm("ZoneD", "ZoneB")).thenReturn(3.0);

        StopSequenceResult result = stopPlanningService.planRoute(offer, Arrays.asList(request1), vehicle);

        assertTrue(result.isFeasible());
        assertNotNull(result.getSearchStats(), "search stats must accompany every planned route");
        java.util.Map<String, Object> stats = result.getSearchStats();
        assertEquals(1, stats.get("requests"));
        assertTrue(((Number) stats.get("nodesExplored")).longValue() >= 1);
        java.util.Map<String, Long> pruned = (java.util.Map<String, Long>) stats.get("prunedByConstraint");
        assertEquals(5, pruned.size(), "all five constraints must be reported");
        long sum = pruned.values().stream().mapToLong(Long::longValue).sum();
        assertEquals(((Number) stats.get("prunedTotal")).longValue(), sum, "prunedTotal must equal the per-constraint sum");
        // C1 must have pruned at least once: DROPOFF(Bob) is a candidate before his PICKUP at the root
        assertTrue(pruned.get("C1_ordering") >= 1);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSearchStatsCountDetourPrunings() {
        // Infeasible fixture from testNoFeasibleRoute: every extension violates the detour bound
        offer.setMaxDetourMinutes(5);
        when(mappingService.getTravelTimeMinutes("ZoneA", "ZoneB")).thenReturn(10);
        when(mappingService.getTravelTimeMinutes("ZoneA", "ZoneC")).thenReturn(5);
        when(mappingService.getTravelTimeMinutes("ZoneC", "ZoneD")).thenReturn(5);
        when(mappingService.getTravelTimeMinutes("ZoneD", "ZoneB")).thenReturn(10);

        StopSequenceResult result = stopPlanningService.planRoute(offer, Arrays.asList(request1), vehicle);

        assertFalse(result.isFeasible());
        assertNotNull(result.getSearchStats(), "stats must be reported even for infeasible searches");
        java.util.Map<String, Long> pruned =
                (java.util.Map<String, Long>) result.getSearchStats().get("prunedByConstraint");
        assertTrue(pruned.get("C5_detourBound") >= 1, "the detour lower bound must have pruned");
    }

    @Test
    public void testMappingLookupsAreMemoisedWithinOnePlan() {
        /* The DFS re-evaluates the same legs constantly (every node prices the leg home).
           With a live Maps key each repeat would be an HTTP call, so the per-plan memo
           must absorb them: the mapping service sees each distinct leg exactly once. */
        when(mappingService.getTravelTimeMinutes("ZoneA", "ZoneB")).thenReturn(15);
        when(mappingService.getDistanceKm("ZoneA", "ZoneB")).thenReturn(10.0);
        when(mappingService.getTravelTimeMinutes("ZoneA", "ZoneC")).thenReturn(5);
        when(mappingService.getDistanceKm("ZoneA", "ZoneC")).thenReturn(3.0);
        when(mappingService.getTravelTimeMinutes("ZoneC", "ZoneD")).thenReturn(5);
        when(mappingService.getDistanceKm("ZoneC", "ZoneD")).thenReturn(3.0);
        when(mappingService.getTravelTimeMinutes("ZoneD", "ZoneB")).thenReturn(5);
        when(mappingService.getDistanceKm("ZoneD", "ZoneB")).thenReturn(3.0);

        StopSequenceResult result = stopPlanningService.planRoute(offer, Arrays.asList(request1), vehicle);

        assertTrue(result.isFeasible());
        java.util.Map<String, Object> stats = result.getSearchStats();
        long lookups = ((Number) stats.get("legLookups")).longValue();
        long hits = ((Number) stats.get("legCacheHits")).longValue();
        assertTrue(hits > 0, "the memo must absorb repeated legs");
        assertTrue(lookups > hits, "some lookups must still reach the mapping service");

        // Every distinct leg is fetched exactly once, however often the search asks for it.
        verify(mappingService, times(1)).getTravelTimeMinutes("ZoneA", "ZoneB");
        verify(mappingService, times(1)).getTravelTimeMinutes("ZoneC", "ZoneD");
        verify(mappingService, times(1)).getDistanceKm("ZoneA", "ZoneC");
    }

    @Test
    public void testNoFeasibleRoute() {
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
