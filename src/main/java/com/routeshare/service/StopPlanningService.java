package com.routeshare.service;

import com.routeshare.model.RideRequest;
import com.routeshare.model.TripOffer;
import com.routeshare.model.Vehicle;
import com.routeshare.model.dto.StopSequenceResult;
import com.routeshare.service.integration.MappingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * StopPlanningService implements the custom constrained route sequencing algorithm.
 *
 * Design Pattern:
 * - Strategy/Template-like structure: Aggregates MappingService to calculate route times and distances.
 *
 * Algorithmic Complexity:
 * - Worst-case: O((2n)!) where n is the number of ride requests (exploring all permutations of pickup/dropoff stops).
 * - Average-case: Extensively optimized via 5 backtracking constraints which prune infeasible branches early.
 *
 * Course Syllabus Concepts:
 * - Rigor & Formality (SE Principle 1): Formal verification of constraints at each step of the search space.
 * - Robustness (Ch. 2): Prevents system failures by handling invalid parameters and backtracking gracefully.
 * - Separation of Concerns (SE Principle 2): Separates route search logic from pricing and reputation.
 */
@Service
public class StopPlanningService {

    private final MappingService mappingService;

    // DFS search variables (thread-safe by keeping them local to method execution or in a helper context class)
    private static class SearchContext {
        List<StopNode> bestSequence = null;
        int bestTime = Integer.MAX_VALUE;
        double bestDistance = 0.0;
        boolean bestFeasible = false;
    }

    /**
     * StopNode represents a candidate stop (either pickup or dropoff) in the search space.
     */
    public static class StopNode {
        public enum Type { PICKUP, DROPOFF }

        private final Type type;
        private final RideRequest request;

        public StopNode(Type type, RideRequest request) {
            this.type = type;
            this.request = request;
        }

        public Type getType() {
            return type;
        }

        public RideRequest getRequest() {
            return request;
        }

        public String getLocation() {
            return type == Type.PICKUP ? request.getOrigin() : request.getDestination();
        }

        public String getLabel() {
            return type + "(" + request.getPassenger().getName() + ") at " + getLocation();
        }
    }

    @Autowired
    public StopPlanningService(MappingService mappingService) {
        this.mappingService = mappingService;
    }

    /**
     * Computes the optimal stopping sequence matching a driver's trip offer and passenger requests.
     *
     * @param offer The driver's TripOffer.
     * @param requests The list of candidate RideRequests.
     * @param vehicle The driver's Vehicle.
     * @return StopSequenceResult including the sequence list, times, and feasibility flag.
     */
    public StopSequenceResult planRoute(TripOffer offer, List<RideRequest> requests, Vehicle vehicle) {
        if (offer == null || vehicle == null) {
            return new StopSequenceResult(new ArrayList<>(), 0, 0.0, false, "Invalid input parameters.");
        }

        double directDistance = mappingService.getDistanceKm(offer.getOrigin(), offer.getDestination());
        int directTime = mappingService.getTravelTimeMinutes(offer.getOrigin(), offer.getDestination());

        if (requests == null || requests.isEmpty()) {
            List<String> emptySequence = new ArrayList<>();
            emptySequence.add("Origin: " + offer.getOrigin());
            emptySequence.add("Destination: " + offer.getDestination());
            return new StopSequenceResult(emptySequence, directTime, directDistance, true, null);
        }

        // Generate candidate stops (PICKUP and DROPOFF for each request)
        List<StopNode> availableStops = new ArrayList<>();
        for (RideRequest req : requests) {
            availableStops.add(new StopNode(StopNode.Type.PICKUP, req));
            availableStops.add(new StopNode(StopNode.Type.DROPOFF, req));
        }

        SearchContext context = new SearchContext();

        // Start DFS search
        search(
                offer.getOrigin(),
                offer.getDepartureTime(),
                0,
                0,
                0.0,
                new ArrayList<>(),
                availableStops,
                offer,
                vehicle,
                directDistance,
                directTime,
                context
        );

        if (!context.bestFeasible || context.bestSequence == null) {
            return new StopSequenceResult(
                    new ArrayList<>(),
                    0,
                    0.0,
                    false,
                    "No feasible stopping sequence could be found satisfying all constraints."
            );
        }

        List<String> finalSequence = new ArrayList<>();
        finalSequence.add("Origin: " + offer.getOrigin());
        for (StopNode node : context.bestSequence) {
            finalSequence.add(node.getLabel());
        }
        finalSequence.add("Destination: " + offer.getDestination());

        return new StopSequenceResult(
                finalSequence,
                context.bestTime,
                context.bestDistance,
                true,
                null
        );
    }

    private void search(
            String currentLocation,
            LocalDateTime currentTime,
            int currentPassengers,
            int currentPickups,
            double currentDistance,
            List<StopNode> currentSequence,
            List<StopNode> availableStops,
            TripOffer offer,
            Vehicle vehicle,
            double directDistance,
            int directTime,
            SearchContext context
    ) {
        // Evaluate the final leg back to the driver's destination
        int timeToDestination = mappingService.getTravelTimeMinutes(currentLocation, offer.getDestination());
        double distanceToDestination = mappingService.getDistanceKm(currentLocation, offer.getDestination());

        long minutesElapsed = java.time.Duration.between(offer.getDepartureTime(), currentTime).toMinutes();
        int totalSequenceTime = (int) minutesElapsed + timeToDestination;
        int detour = totalSequenceTime - directTime;

        // Check if current state represents a valid candidate sequence where all picked-up requests are also dropped off
        boolean allDroppedOff = true;
        for (StopNode node : currentSequence) {
            if (node.getType() == StopNode.Type.PICKUP) {
                boolean dropped = false;
                for (StopNode other : currentSequence) {
                    if (other.getType() == StopNode.Type.DROPOFF &&
                            other.getRequest().getId().equals(node.getRequest().getId())) {
                        dropped = true;
                        break;
                    }
                }
                if (!dropped) {
                    allDroppedOff = false;
                    break;
                }
            }
        }

        // We only consider a sequence as valid if it serves at least one request and does not exceed the detour budget
        if (allDroppedOff && !currentSequence.isEmpty() && detour <= offer.getMaxDetourMinutes()) {
            double totalDistance = currentDistance + distanceToDestination;
            int served = currentSequence.size() / 2;
            int bestServed = context.bestSequence == null ? 0 : context.bestSequence.size() / 2;

            // Goal: Maximize requests served, then minimize total travel time
            if (served > bestServed || (served == bestServed && totalSequenceTime < context.bestTime)) {
                context.bestSequence = new ArrayList<>(currentSequence);
                context.bestTime = totalSequenceTime;
                context.bestDistance = totalDistance;
                context.bestFeasible = true;
            }
        }

        // Explore extending the route sequence
        for (int i = 0; i < availableStops.size(); i++) {
            StopNode nextStop = availableStops.get(i);

            // CONSTRAINT 1: Ordering - Dropoff requires corresponding Pickup to be in currentSequence
            if (nextStop.getType() == StopNode.Type.DROPOFF) {
                boolean pickupDone = false;
                for (StopNode s : currentSequence) {
                    if (s.getType() == StopNode.Type.PICKUP &&
                            s.getRequest().getId().equals(nextStop.getRequest().getId())) {
                        pickupDone = true;
                        break;
                    }
                }
                if (!pickupDone) {
                    continue; // Prune: cannot drop off a passenger before picking them up
                }
            }

            // CONSTRAINT 2: Capacity - Cannot exceed vehicle capacity
            int nextPassengers = currentPassengers;
            if (nextStop.getType() == StopNode.Type.PICKUP) {
                nextPassengers += 1;
                if (nextPassengers > vehicle.getCapacity()) {
                    continue; // Prune: vehicle capacity exceeded
                }
            } else {
                nextPassengers -= 1;
            }

            // CONSTRAINT 3: Stop Limit - Driver defines the maximum pickups (stops) they want to make
            int nextPickups = currentPickups;
            if (nextStop.getType() == StopNode.Type.PICKUP) {
                nextPickups += 1;
                if (nextPickups > offer.getMaxStops()) {
                    continue; // Prune: exceeds driver's maximum stops limit
                }
            }

            // Calculate travel metadata to next stop
            int travelTime = mappingService.getTravelTimeMinutes(currentLocation, nextStop.getLocation());
            double travelDistance = mappingService.getDistanceKm(currentLocation, nextStop.getLocation());
            LocalDateTime arrivalTime = currentTime.plusMinutes(travelTime);

            // CONSTRAINT 4: Time Window - Check if arrival fits within passenger pickup window
            if (nextStop.getType() == StopNode.Type.PICKUP) {
                RideRequest req = nextStop.getRequest();
                if (arrivalTime.isAfter(req.getPickupTimeWindowEnd())) {
                    continue; // Prune: arrived after passenger time window end
                }
                // If early, driver waits (arrival time matches passenger start window)
                if (arrivalTime.isBefore(req.getPickupTimeWindowStart())) {
                    arrivalTime = req.getPickupTimeWindowStart();
                }
            }

            // CONSTRAINT 5: Detour Budget Check (Lower bound prune)
            int timeToNext = travelTime;
            int timeToDestFromNext = mappingService.getTravelTimeMinutes(nextStop.getLocation(), offer.getDestination());
            int accumTime = (int) minutesElapsed;
            int detourLowerBound = (accumTime + timeToNext + timeToDestFromNext) - directTime;

            if (detourLowerBound > offer.getMaxDetourMinutes()) {
                continue; // Prune: detour lower bound exceeds driver's detour budget
            }

            // All constraints passed -> recurse (DFS with backtracking)
            currentSequence.add(nextStop);
            List<StopNode> nextAvailable = new ArrayList<>(availableStops);
            nextAvailable.remove(i);

            search(
                    nextStop.getLocation(),
                    arrivalTime,
                    nextPassengers,
                    nextPickups,
                    currentDistance + travelDistance,
                    currentSequence,
                    nextAvailable,
                    offer,
                    vehicle,
                    directDistance,
                    directTime,
                    context
            );

            // Backtrack
            currentSequence.remove(currentSequence.size() - 1);
        }
    }
}
