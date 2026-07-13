package com.routeshare.repository;

import com.routeshare.model.DriverPricingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * DriverPricingRuleRepository persists each driver's composed pricing policy (FR-16).
 *
 * Demonstrates:
 * - Repository Pattern with derived queries: the enabled rule set is fetched
 *   in application (priority) order directly by the database.
 */
@Repository
public interface DriverPricingRuleRepository extends JpaRepository<DriverPricingRule, Long> {

    List<DriverPricingRule> findByDriverIdOrderByPriorityAsc(Long driverId);

    List<DriverPricingRule> findByDriverIdAndEnabledTrueOrderByPriorityAsc(Long driverId);
}
