package com.routeshare.service.pricing;

import com.routeshare.model.DriverPricingRule;
import com.routeshare.model.dto.PricingResult;
import com.routeshare.model.enums.IncentiveTier;
import com.routeshare.model.enums.PricingRuleType;
import com.routeshare.service.pricing.PricingPolicy;
import com.routeshare.service.pricing.RideContext;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PricingEngine {
    private final List<PricingPolicy> policies;

    @Autowired
    public PricingEngine(List<PricingPolicy> policies) {
        this.policies = (List)policies.stream().sorted(Comparator.comparingInt(PricingPolicy::getPriority)).collect(Collectors.toList());
    }

    public PricingResult calculateFare(RideContext context) {
        double baseFare;
        if (context == null) {
            return new PricingResult(0.0, 0.0, (List<String>)new ArrayList());
        }
        double currentFare = baseFare = 2.0 + context.getDistanceKm() * 0.5;
        ArrayList appliedPolicies = new ArrayList();
        for (PricingPolicy policy : this.policies) {
            if (!policy.appliesTo(context)) continue;
            double newFare = policy.applyPolicy(currentFare, context);
            appliedPolicies.add((Object)policy.getClass().getSimpleName());
            currentFare = newFare;
        }
        double finalFare = (double)Math.round((double)(currentFare * 100.0)) / 100.0;
        baseFare = (double)Math.round((double)(baseFare * 100.0)) / 100.0;
        return new PricingResult(baseFare, finalFare, (List<String>)appliedPolicies);
    }

    public PricingResult calculateFare(RideContext context, List<DriverPricingRule> driverRules) {
        double baseFare;
        if (driverRules == null || driverRules.isEmpty()) {
            return this.calculateFare(context);
        }
        if (context == null) {
            return new PricingResult(0.0, 0.0, (List<String>)new ArrayList());
        }
        ArrayList applied = new ArrayList();
        double ratePerKm = 0.5;
        for (DriverPricingRule rule : driverRules) {
            if (rule.getType() != PricingRuleType.BASE_RATE_PER_KM) continue;
            ratePerKm = rule.getValue();
            applied.add((Object)String.format((String)"DriverRule:BASE_RATE_PER_KM(%.2f\u20ac/km)", (Object[])new Object[]{rule.getValue()}));
            break;
        }
        double fare = baseFare = 2.0 + context.getDistanceKm() * ratePerKm;
        LocalTime time = context.getDepartureTime() == null ? null : context.getDepartureTime().toLocalTime();
        DayOfWeek day = context.getDepartureTime() == null ? null : context.getDepartureTime().getDayOfWeek();
        block7: for (DriverPricingRule rule : driverRules) {
            switch (rule.getType()) {
                case RUSH_HOUR_SURCHARGE_PCT: {
                    boolean weekday = day != null && day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
                    boolean rush = time != null && weekday && (!time.isBefore(LocalTime.of((int)7, (int)0)) && !time.isAfter(LocalTime.of((int)9, (int)0)) || !time.isBefore(LocalTime.of((int)17, (int)0)) && !time.isAfter(LocalTime.of((int)19, (int)0)));
                    if (!rush) continue block7;
                    fare *= 1.0 + rule.getValue() / 100.0;
                    applied.add((Object)String.format((String)"DriverRule:RUSH_HOUR_SURCHARGE(%.0f%%)", (Object[])new Object[]{rule.getValue()}));
                    break;
                }
                case LATE_NIGHT_FEE_EUR: {
                    boolean night = time != null && (!time.isBefore(LocalTime.of((int)23, (int)0)) || !time.isAfter(LocalTime.of((int)5, (int)0)));
                    if (!night) continue block7;
                    fare += rule.getValue();
                    applied.add((Object)String.format((String)"DriverRule:LATE_NIGHT_FEE(+%.2f\u20ac)", (Object[])new Object[]{rule.getValue()}));
                    break;
                }
                case SAME_DESTINATION_DISCOUNT_PCT: {
                    boolean sameZone = context.getPassengerDestination() != null && context.getDriverDestination() != null && context.getPassengerDestination().trim().equalsIgnoreCase(context.getDriverDestination().trim());
                    if (!sameZone) continue block7;
                    fare *= 1.0 - rule.getValue() / 100.0;
                    applied.add((Object)String.format((String)"DriverRule:SAME_DESTINATION_DISCOUNT(%.0f%%)", (Object[])new Object[]{rule.getValue()}));
                    break;
                }
                case LOYALTY_TIER_DISCOUNT_PCT: {
                    IncentiveTier tier = context.getPassengerTier();
                    boolean loyal = tier == IncentiveTier.GOLD || tier == IncentiveTier.PREMIUM_PRICING;
                    if (!loyal) continue block7;
                    fare *= 1.0 - rule.getValue() / 100.0;
                    applied.add((Object)String.format((String)"DriverRule:LOYALTY_TIER_DISCOUNT(%.0f%% for %s)", (Object[])new Object[]{rule.getValue(), tier}));
                    break;
                }
            }
        }
        double finalFare = Math.max((double)0.0, (double)((double)Math.round((double)(fare * 100.0)) / 100.0));
        return new PricingResult((double)Math.round((double)(baseFare * 100.0)) / 100.0, finalFare, (List<String>)applied);
    }
}
