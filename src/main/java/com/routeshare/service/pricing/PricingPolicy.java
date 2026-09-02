package com.routeshare.service.pricing;

/* one pricing rule. PricingEngine runs whichever ones apply, in priority order,
   so adding a new rule means adding a class and nothing else */
public interface PricingPolicy {

    // does this rule apply to this ride?
    boolean appliesTo(RideContext context);

    // adjust the running fare
    double applyPolicy(double currentFare, RideContext context);

    // lower runs earlier
    int getPriority();
}
