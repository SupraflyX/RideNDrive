package com.routeshare.repository;

import com.routeshare.model.DriverTravelRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DriverTravelRuleRepository extends JpaRepository<DriverTravelRule, Long> {
    List<DriverTravelRule> findByDriverIdOrderByPriorityAsc(Long driverId);
    List<DriverTravelRule> findByDriverIdAndEnabledTrueOrderByPriorityAsc(Long driverId);
}
