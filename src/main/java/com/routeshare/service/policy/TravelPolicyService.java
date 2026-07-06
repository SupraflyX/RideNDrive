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

/**
 * TravelPolicyService — the driver-defined travel policy engine (FR-15).
 *
 * Two responsibilities, mirroring the project guideline "rule/policy
 * customizability, letting the owner define her … traveling policy":
 *
 * 1. VETO EVALUATION — a candidate request is checked against every enabled
 *    rule of the trip's driver, in priority order. Any violated rule vetoes
 *    the match (conflict resolution: veto dominates), and every violation is
 *    reported for auditability.
 *
 * 2. CANDIDATE RANKING — among non-vetoed candidates, a deterministic score
 *    orders "the best passengers first": reputation (the incentive mechanism —
 *    good behaviour buys priority), incentive tier bonus, destination-zone
 *    affinity, and a small light-luggage bonus.
 *
 * Demonstrates:
 * - Specification Pattern (GoF-adjacent, Ch. 9): rules are composable
 *   predicates owned by the driver, interpreted generically by this service.
 * - Separation of Concerns: policy semantics live here, not in matching or
 *   booking code.
 */
@Service
public class TravelPolicyService {

    /** Result of evaluating one candidate against a driver's policy. */
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

    /** A ranked candidate with its score and a transparent breakdown. */
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

    /**
     * Evaluates a candidate request against the driver's enabled rules.
     * Veto semantics: every violated rule is collected; one violation suffices
     * to reject (documented precedence — veto dominates any positive signal).
     */
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

    /**
     * Ranks non-vetoed candidates for a driver's trip — "the best passengers"
     * first. Deterministic weighted score with a transparent breakdown:
     *
     *   reputation  = reputationScore * 10          (0–50)
     *   tierBonus   = STANDARD 0 / SILVER 5 / GOLD 10 / PREMIUM_PRICING 15
     *   zoneBonus   = 8 if same destination as the trip
     *   luggage     = NONE +3 / SMALL +1 / LARGE +0
     */
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
