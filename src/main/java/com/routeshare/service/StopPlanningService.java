package com.routeshare.service;

import com.routeshare.model.RideRequest;
import com.routeshare.model.TripOffer;
import com.routeshare.model.Vehicle;
import com.routeshare.model.dto.PlannedStop;
import com.routeshare.model.dto.StopSequenceResult;
import com.routeshare.service.integration.MappingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/* works out what order a driver should make their stops in.

   every ride request becomes two stops, a pickup and a dropoff. we try orderings
   depth-first and keep the best one. five checks cut branches off early:
     1. can't drop someone off before we've picked them up
     2. can't fit more people in the car than there are seats
     3. can't make more pickups than the driver asked for
     4. can't turn up after a passenger's pickup window has closed
     5. can't blow the driver's detour budget

   worst case O((2n)!) for n requests, but the checks kill most branches long before that */
@Service
public class StopPlanningService {

    private static final String[] CHECK_NAMES = {
            "C1_ordering", "C2_capacity", "C3_stopLimit", "C4_timeWindow", "C5_detourBound"
    };

    private final MappingService mappingService;

    @Autowired
    public StopPlanningService(MappingService mappingService) {
        this.mappingService = mappingService;
    }

    public StopSequenceResult planRoute(TripOffer offer, List<RideRequest> requests, Vehicle vehicle) {
        if (offer == null || vehicle == null) {
            return new StopSequenceResult(new ArrayList<>(), 0, 0.0, false, "Invalid input parameters.");
        }

        LegCache legs = new LegCache(mappingService);
        double directDistance = legs.distanceKm(offer.getOrigin(), offer.getDestination());
        int directTime = legs.travelTimeMinutes(offer.getOrigin(), offer.getDestination());

        if (requests == null || requests.isEmpty()) {
            StopSequenceResult direct = new StopSequenceResult(null, directTime, directDistance, true, null);
            direct.setStops(endpointsAround(offer, List.of()));
            return direct;
        }

        List<StopNode> availableStops = new ArrayList<>();
        for (RideRequest req : requests) {
            availableStops.add(new StopNode(StopNode.Type.PICKUP, req));
            availableStops.add(new StopNode(StopNode.Type.DROPOFF, req));
        }

        Planner planner = new Planner(offer, vehicle, directTime, legs);
        planner.search(offer.getOrigin(), offer.getDepartureTime(), 0, 0, 0.0,
                new ArrayList<>(), availableStops);
        return planner.result(requests.size());
    }

    private static List<PlannedStop> endpointsAround(TripOffer offer, List<StopNode> middle) {
        List<PlannedStop> stops = new ArrayList<>();
        stops.add(new PlannedStop(PlannedStop.Kind.ORIGIN, offer.getOrigin(), null));
        for (StopNode node : middle) {
            stops.add(node.toPlannedStop());
        }
        stops.add(new PlannedStop(PlannedStop.Kind.DESTINATION, offer.getDestination(), null));
        return stops;
    }

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

        public PlannedStop toPlannedStop() {
            PlannedStop.Kind kind = type == Type.PICKUP ? PlannedStop.Kind.PICKUP : PlannedStop.Kind.DROPOFF;
            return new PlannedStop(kind, getLocation(), request.getPassenger().getName());
        }
    }

    /* caches leg lookups for one planRoute call and counts its own hits. the search asks for
       the same (from, to) pairs repeatedly, and with a real maps key every repeat is an http
       call. caching per call also means all options get compared against the same numbers */
    private static final class LegCache {
        private final MappingService delegate;
        /* the key is the pair itself rather than a joined string, so a place name
           containing the separator can never look like a different leg */
        private final Map<List<String>, Integer> times = new HashMap<>();
        private final Map<List<String>, Double> distances = new HashMap<>();
        private long lookups;
        private long hits;

        LegCache(MappingService delegate) {
            this.delegate = delegate;
        }

        int travelTimeMinutes(String from, String to) {
            return cached(times, from, to, () -> delegate.getTravelTimeMinutes(from, to));
        }

        double distanceKm(String from, String to) {
            return cached(distances, from, to, () -> delegate.getDistanceKm(from, to));
        }

        long lookups() {
            return lookups;
        }

        long hits() {
            return hits;
        }

        private <T> T cached(Map<List<String>, T> store, String from, String to, Supplier<T> compute) {
            List<String> key = Arrays.asList(from, to);
            lookups++;
            T hit = store.get(key);
            if (hit != null) {
                hits++;
                return hit;
            }
            T value = compute.get();
            store.put(key, value);
            return value;
        }
    }

    /* one run of the search. holds what stays the same all the way down (trip, car, direct
       time, leg cache) so it isn't passed to every recursive call, plus the best route so far.
       built fresh per planRoute call, so two drivers planning at once never collide */
    private static final class Planner {

        private final TripOffer offer;
        private final Vehicle vehicle;
        private final int directTime;
        private final LegCache legs;

        private List<StopNode> bestSequence;
        private int bestTime = Integer.MAX_VALUE;
        private double bestDistance;
        private boolean bestFeasible;

        private long nodesExplored;
        private long candidatesConsidered;
        private final long[] prunedByCheck = new long[CHECK_NAMES.length];

        Planner(TripOffer offer, Vehicle vehicle, int directTime, LegCache legs) {
            this.offer = offer;
            this.vehicle = vehicle;
            this.directTime = directTime;
            this.legs = legs;
        }

        void search(String currentLocation,
                    LocalDateTime currentTime,
                    int currentPassengers,
                    int currentPickups,
                    double currentDistance,
                    List<StopNode> currentSequence,
                    List<StopNode> availableStops) {
            nodesExplored++;

            int timeToDestination = legs.travelTimeMinutes(currentLocation, offer.getDestination());
            double distanceToDestination = legs.distanceKm(currentLocation, offer.getDestination());

            long minutesElapsed = Duration.between(offer.getDepartureTime(), currentTime).toMinutes();
            int totalSequenceTime = (int) minutesElapsed + timeToDestination;
            int detour = totalSequenceTime - directTime;

            /* nobody still in the car means everyone we picked up has been dropped off again.
               currentPassengers already counts that: +1 per pickup, -1 per dropoff, and check 1
               below stops a dropoff ever happening before its pickup */
            boolean allDroppedOff = currentPassengers == 0;

            if (allDroppedOff && !currentSequence.isEmpty() && detour <= offer.getMaxDetourMinutes()) {
                int served = currentSequence.size() / 2;
                int bestServed = bestSequence == null ? 0 : bestSequence.size() / 2;

                // serve as many people as possible first, then prefer the quicker route
                if (served > bestServed || (served == bestServed && totalSequenceTime < bestTime)) {
                    bestSequence = new ArrayList<>(currentSequence);
                    bestTime = totalSequenceTime;
                    bestDistance = currentDistance + distanceToDestination;
                    bestFeasible = true;
                }
            }

            for (int i = 0; i < availableStops.size(); i++) {
                StopNode nextStop = availableStops.get(i);
                boolean isPickup = nextStop.getType() == StopNode.Type.PICKUP;
                candidatesConsidered++;

                // 1. can't drop someone off before we've picked them up
                if (!isPickup && !alreadyPickedUp(currentSequence, nextStop)) {
                    prunedByCheck[0]++;
                    continue;
                }

                int nextPassengers = currentPassengers;
                int nextPickups = currentPickups;
                if (isPickup) {
                    nextPassengers++;
                    nextPickups++;
                    // 2. car would be over capacity
                    if (nextPassengers > vehicle.getCapacity()) {
                        prunedByCheck[1]++;
                        continue;
                    }
                    // 3. more pickups than the driver wants to make
                    if (nextPickups > offer.getMaxStops()) {
                        prunedByCheck[2]++;
                        continue;
                    }
                } else {
                    nextPassengers--;
                }

                int travelTime = legs.travelTimeMinutes(currentLocation, nextStop.getLocation());
                LocalDateTime arrivalTime = currentTime.plusMinutes(travelTime);

                // 4. passenger's pickup window
                if (isPickup) {
                    RideRequest req = nextStop.getRequest();
                    if (arrivalTime.isAfter(req.getPickupTimeWindowEnd())) {
                        prunedByCheck[3]++;
                        continue;
                    }
                    // turned up early, so the driver waits until the window opens
                    if (arrivalTime.isBefore(req.getPickupTimeWindowStart())) {
                        arrivalTime = req.getPickupTimeWindowStart();
                    }
                }

                /* 5. even going straight to the destination from the next stop would already
                   blow the detour budget, so nothing below this branch can work either */
                int timeToDestFromNext = legs.travelTimeMinutes(nextStop.getLocation(), offer.getDestination());
                int detourLowerBound = (int) minutesElapsed + travelTime + timeToDestFromNext - directTime;
                if (detourLowerBound > offer.getMaxDetourMinutes()) {
                    prunedByCheck[4]++;
                    continue;
                }

                    List<StopNode> nextAvailable = new ArrayList<>(availableStops);
                nextAvailable.remove(i);
                currentSequence.add(nextStop);

                search(nextStop.getLocation(), arrivalTime, nextPassengers, nextPickups,
                        currentDistance + legs.distanceKm(currentLocation, nextStop.getLocation()),
                        currentSequence, nextAvailable);

                currentSequence.remove(currentSequence.size() - 1);
            }
        }

        private static boolean alreadyPickedUp(List<StopNode> sequence, StopNode dropoff) {
            for (StopNode s : sequence) {
                if (s.getType() == StopNode.Type.PICKUP
                        && s.getRequest().getId().equals(dropoff.getRequest().getId())) {
                    return true;
                }
            }
            return false;
        }

        StopSequenceResult result(int requestCount) {
            StopSequenceResult result = bestFeasible && bestSequence != null
                    ? new StopSequenceResult(null, bestTime, bestDistance, true, null)
                    : new StopSequenceResult(new ArrayList<>(), 0, 0.0, false,
                            "No feasible stopping sequence could be found satisfying all constraints.");
            if (result.isFeasible()) {
                result.setStops(endpointsAround(offer, bestSequence));
            }
            result.setSearchStats(searchStats(requestCount));
            return result;
        }

        private Map<String, Object> searchStats(int requestCount) {
            Map<String, Long> pruned = new LinkedHashMap<>();
            long prunedTotal = 0;
            for (int i = 0; i < CHECK_NAMES.length; i++) {
                pruned.put(CHECK_NAMES[i], prunedByCheck[i]);
                prunedTotal += prunedByCheck[i];
            }

            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("requests", requestCount);
            stats.put("nodesExplored", nodesExplored);
            stats.put("candidatesConsidered", candidatesConsidered);
            stats.put("prunedByConstraint", pruned);
            stats.put("prunedTotal", prunedTotal);
            stats.put("legLookups", legs.lookups());
            stats.put("legCacheHits", legs.hits());
            return stats;
        }
    }
}
