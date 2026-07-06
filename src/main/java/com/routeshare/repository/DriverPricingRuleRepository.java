package com.routeshare.repository;

import com.routeshare.model.DriverPricingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DriverPricingRuleRepository extends JpaRepository<DriverPricingRule, Long> {
    List<DriverPricingRule> findByDriverIdOrderByPriorityAsc(Long driverId);
    List<DriverPricingRule> findByDriverIdAndEnabledTrueOrderByPriorityAsc(Long driverId);
}
