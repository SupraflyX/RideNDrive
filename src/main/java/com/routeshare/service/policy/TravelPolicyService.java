package com.routeshare.service.policy;

import com.routeshare.model.DriverTravelRule;
import com.routeshare.model.RideRequest;
import com.routeshare.model.TripOffer;
import com.routeshare.model.User;
import com.routeshare.model.enums.IncentiveTier;
import com.routeshare.model.enums.LuggageSize;
import com.routeshare.repository.DriverTravelRuleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/* the rules a driver sets about who they'll take. checks a candidate against every rule the
   driver switched on (one break is enough to reject, but all violations are collected so
   the driver can see why), then ranks the ones that pass */
@Service
public class TravelPolicyService {

    public static class PolicyDecision {
        private final boolean allowed;
        private final List<String> violations;

        public PolicyDecision(boolean allowed, List<String> violations) {
            this.allowed = allowed;
            this.violations = violations;
        }

        public boolean isAllowed() { return allowed; }
        public List<String> getViolations() { return violations; }
    }

    public static class RankedCandidate {
        private final RideRequest request;
        private final double score;
        private final Map<String, Double> breakdown;

        public RankedCandidate(RideRequest request, double score, Map<String, Double> breakdown) {
            this.request = request;
            this.score = score;
            this.breakdown = breakdown;
        }

        public RideRequest getRequest() { return request; }
        public double getScore() { return score; }
        public Map<String, Double> getBreakdown() { return breakdown; }
    }

    private final DriverTravelRuleRepository ruleRepository;

    @Autowired
    public TravelPolicyService(DriverTravelRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    // one broken rule is enough to reject, but we still collect all of them
    public PolicyDecision evaluate(Long driverId, TripOffer offer, RideRequest candidate) {
        List<DriverTravelRule> rules = ruleRepository.findByDriverIdAndEnabledTrueOrderByPriorityAsc(driverId);
        List<String> violations = new ArrayList<>();

        for (DriverTravelRule rule : rules) {
            switch (rule.getType()) {
                case MIN_PASSENGER_REPUTATION -> {
                    double min = rule.getNumericValue() == null ? 0.0 : rule.getNumericValue();
                    User passenger = candidate.getPassenger();
                    double rep = passenger == null ? 0.0 : passenger.getReputationScore();
                    if (rep < min) {
                        violations.add(String.format("MIN_PASSENGER_REPUTATION: candidate has %.2f, driver requires ≥ %.2f", rep, min));
                    }
                }
                case SAME_DESTINATION_ONLY -> {
                    String tripDest = offer.getDestination() == null ? "" : offer.getDestination().trim();
                    String reqDest = candidate.getDestination() == null ? "" : candidate.getDestination().trim();
                    if (!tripDest.equalsIgnoreCase(reqDest)) {
                        violations.add("SAME_DESTINATION_ONLY: candidate destination '" + reqDest
                                + "' differs from trip destination '" + tripDest + "'");
                    }
                }
                case NO_LARGE_LUGGAGE -> {
                    LuggageSize size = candidate.getLuggageSize() == null ? LuggageSize.NONE : candidate.getLuggageSize();
                    if (size == LuggageSize.LARGE) {
                        violations.add("NO_LARGE_LUGGAGE: candidate declared LARGE luggage");
                    }
                }
            }
        }
        return new PolicyDecision(violations.isEmpty(), violations);
    }

    /* scores the candidates that got through, highest first:
       reputation  score * 10                                  (0-50)
       tier        STANDARD 0 / SILVER 5 / GOLD 10 / PREMIUM 15
       same place  8 if they're going where the driver is going
       luggage     none +3 / small +1 / large +0 */
    public List<RankedCandidate> rankCandidates(Long driverId, TripOffer offer, List<RideRequest> candidates) {
        List<RankedCandidate> ranked = new ArrayList<>();

        for (RideRequest candidate : candidates) {
            if (!evaluate(driverId, offer, candidate).isAllowed()) {
                continue; // vetoed candidates are not ranked
            }

            Map<String, Double> parts = new LinkedHashMap<>();
            User passenger = candidate.getPassenger();

            double reputation = (passenger == null ? 0.0 : passenger.getReputationScore()) * 10.0;
            parts.put("reputation", reputation);

            IncentiveTier tier = passenger == null ? IncentiveTier.STANDARD : passenger.getIncentiveTier();
            double tierBonus = switch (tier) {
                case PREMIUM_PRICING -> 15.0;
                case GOLD -> 10.0;
                case SILVER -> 5.0;
                default -> 0.0;
            };
            parts.put("tierBonus", tierBonus);

            boolean sameZone = offer.getDestination() != null && candidate.getDestination() != null
                    && offer.getDestination().trim().equalsIgnoreCase(candidate.getDestination().trim());
            parts.put("zoneBonus", sameZone ? 8.0 : 0.0);

            LuggageSize size = candidate.getLuggageSize() == null ? LuggageSize.NONE : candidate.getLuggageSize();
            double luggageBonus = switch (size) {
                case NONE -> 3.0;
                case SMALL -> 1.0;
                default -> 0.0;
            };
            parts.put("luggageBonus", luggageBonus);

            double score = parts.values().stream().mapToDouble(Double::doubleValue).sum();
            ranked.add(new RankedCandidate(candidate, Math.round(score * 10.0) / 10.0, parts));
        }

        ranked.sort(Comparator.comparingDouble(RankedCandidate::getScore).reversed());
        return ranked;
    }
}
