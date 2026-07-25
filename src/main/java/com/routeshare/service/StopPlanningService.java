package com.routeshare.service;

import com.routeshare.model.RideRequest;
import com.routeshare.model.TripOffer;
import com.routeshare.model.Vehicle;
import com.routeshare.model.dto.PlannedStop;
import com.routeshare.model.dto.StopSequenceResult;
import com.routeshare.service.integration.MappingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

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
        // Instrumentation: how hard did the search work, and which constraints pruned.
        long nodesExplored = 0;
        long candidatesConsidered = 0;
        long[] prunedByConstraint = new long[5]; // C1 ordering, C2 capacity, C3 stop limit, C4 time window, C5 detour bound
        // Instrumentation: how many mapping lookups the memo served without a call-out.
        long legLookups = 0;
        long legCacheHits = 0;
    }

    /**
     * Per-plan memo over the MappingService.
     *
     * The DFS asks for the same (from, to) legs over and over — every node evaluates
     * the leg home, and every candidate extension evaluates its own leg plus the leg
     * home from there. With the local approximation that is merely wasteful; with a
     * real Google Maps key each repeat is an HTTP round-trip, which is the difference
     * between a search that returns and one that times out. Caching for the lifetime
     * of a single planRoute call also guarantees the search sees one consistent view
     * of the map while it compares alternatives.
     */
    private static final class LegCache {
        private final MappingService delegate;
        private final SearchContext stats;
        // Keyed on the (from, to) pair itself — no separator, so no place name can
        // ever be mistaken for a different leg.
        private final Map<List<String>, Integer> times = new HashMap<>();
        private final Map<List<String>, Double> distances = new HashMap<>();

        LegCache(MappingService delegate, SearchContext stats) {
            this.delegate = delegate;
            this.stats = stats;
        }

        int travelTimeMinutes(String from, String to) {
            return cached(times, from, to, () -> delegate.getTravelTimeMinutes(from, to));
        }

        double distanceKm(String from, String to) {
            return cached(distances, from, to, () -> delegate.getDistanceKm(from, to));
        }

        private <T> T cached(Map<List<String>, T> store, String from, String to, Supplier<T> compute) {
            List<String> key = Arrays.asList(from, to);
            if (stats != null) {
                stats.legLookups++;
            }
            T hit = store.get(key);
            if (hit != null) {
                if (stats != null) {
                    stats.legCacheHits++;
                }
                return hit;
            }
            T value = compute.get();
            store.put(key, value);
            return value;
        }
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

        /** The structured form handed to API consumers; the label is derived from it. */
        public PlannedStop toPlannedStop() {
            PlannedStop.Kind kind = type == Type.PICKUP ? PlannedStop.Kind.PICKUP : PlannedStop.Kind.DROPOFF;
            return new PlannedStop(kind, getLocation(), request.getPassenger().getName());
        }

        public String getLabel() {
            return toPlannedStop().getLabel();
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

        SearchContext context = new SearchContext();
        LegCache legs = new LegCache(mappingService, context);

        double directDistance = legs.distanceKm(offer.getOrigin(), offer.getDestination());
        int directTime = legs.travelTimeMinutes(offer.getOrigin(), offer.getDestination());

        if (requests == null || requests.isEmpty()) {
            StopSequenceResult direct = new StopSequenceResult(null, directTime, directDistance, true, null);
            direct.setStops(endpointsAround(offer, List.of()));
            return direct;
        }

        // Generate candidate stops (PICKUP and DROPOFF for each request)
        List<StopNode> availableStops = new ArrayList<>();
        for (RideRequest req : requests) {
            availableStops.add(new StopNode(StopNode.Type.PICKUP, req));
            availableStops.add(new StopNode(StopNode.Type.DROPOFF, req));
        }

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
                directTime,
                context,
                legs
        );

        if (!context.bestFeasible || context.bestSequence == null) {
            StopSequenceResult infeasible = new StopSequenceResult(
                    new ArrayList<>(),
                    0,
                    0.0,
                    false,
                    "No feasible stopping sequence could be found satisfying all constraints."
            );
            infeasible.setSearchStats(buildSearchStats(context, requests.size()));
            return infeasible;
        }

        StopSequenceResult result = new StopSequenceResult(
                null,
                context.bestTime,
                context.bestDistance,
                true,
                null
        );
        result.setStops(endpointsAround(offer, context.bestSequence));
        result.setSearchStats(buildSearchStats(context, requests.size()));
        return result;
    }

    /** Wraps the planned passenger stops in the driver's own origin and destination. */
    private static List<PlannedStop> endpointsAround(TripOffer offer, List<StopNode> middle) {
        List<PlannedStop> stops = new ArrayList<>();
        stops.add(new PlannedStop(PlannedStop.Kind.ORIGIN, offer.getOrigin(), null));
        for (StopNode node : middle) {
            stops.add(node.toPlannedStop());
        }
        stops.add(new PlannedStop(PlannedStop.Kind.DESTINATION, offer.getDestination(), null));
        return stops;
    }

    /**
     * Packages the DFS instrumentation for API consumers (algorithm transparency, NFR-8):
     * how many partial sequences were explored and how many extensions each of the five
     * pruning constraints rejected. Sums make the pruning effectiveness auditable.
     */
    private Map<String, Object> buildSearchStats(SearchContext context, int requestCount) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("requests", requestCount);
        stats.put("nodesExplored", context.nodesExplored);
        stats.put("candidatesConsidered", context.candidatesConsidered);
        Map<String, Long> pruned = new LinkedHashMap<>();
        pruned.put("C1_ordering", context.prunedByConstraint[0]);
        pruned.put("C2_capacity", context.prunedByConstraint[1]);
        pruned.put("C3_stopLimit", context.prunedByConstraint[2]);
        pruned.put("C4_timeWindow", context.prunedByConstraint[3]);
        pruned.put("C5_detourBound", context.prunedByConstraint[4]);
        stats.put("prunedByConstraint", pruned);
        long total = 0;
        for (long v : context.prunedByConstraint) total += v;
        stats.put("prunedTotal", total);
        stats.put("legLookups", context.legLookups);
        stats.put("legCacheHits", context.legCacheHits);
        return stats;
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
            int directTime,
            SearchContext context,
            LegCache legs
    ) {
        context.nodesExplored++;

        // Evaluate the final leg back to the driver's destination
        int timeToDestination = legs.travelTimeMinutes(currentLocation, offer.getDestination());
        double distanceToDestination = legs.distanceKm(currentLocation, offer.getDestination());

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
            context.candidatesConsidered++;

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
                    context.prunedByConstraint[0]++;
                    continue; // Prune: cannot drop off a passenger before picking them up
                }
            }

            // CONSTRAINT 2: Capacity - Cannot exceed vehicle capacity
            int nextPassengers = currentPassengers;
            if (nextStop.getType() == StopNode.Type.PICKUP) {
                nextPassengers += 1;
                if (nextPassengers > vehicle.getCapacity()) {
                    context.prunedByConstraint[1]++;
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
                    context.prunedByConstraint[2]++;
                    continue; // Prune: exceeds driver's maximum stops limit
                }
            }

            // Calculate travel metadata to next stop
            int travelTime = legs.travelTimeMinutes(currentLocation, nextStop.getLocation());
            double travelDistance = legs.distanceKm(currentLocation, nextStop.getLocation());
            LocalDateTime arrivalTime = currentTime.plusMinutes(travelTime);

            // CONSTRAINT 4: Time Window - Check if arrival fits within passenger pickup window
            if (nextStop.getType() == StopNode.Type.PICKUP) {
                RideRequest req = nextStop.getRequest();
                if (arrivalTime.isAfter(req.getPickupTimeWindowEnd())) {
                    context.prunedByConstraint[3]++;
                    continue; // Prune: arrived after passenger time window end
                }
                // If early, driver waits (arrival time matches passenger start window)
                if (arrivalTime.isBefore(req.getPickupTimeWindowStart())) {
                    arrivalTime = req.getPickupTimeWindowStart();
                }
            }

            // CONSTRAINT 5: Detour Budget Check (Lower bound prune)
            int timeToNext = travelTime;
            int timeToDestFromNext = legs.travelTimeMinutes(nextStop.getLocation(), offer.getDestination());
            int accumTime = (int) minutesElapsed;
            int detourLowerBound = (accumTime + timeToNext + timeToDestFromNext) - directTime;

            if (detourLowerBound > offer.getMaxDetourMinutes()) {
                context.prunedByConstraint[4]++;
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
                    directTime,
                    context,
                    legs
            );

            // Backtrack
            currentSequence.remove(currentSequence.size() - 1);
        }
    }
}
