package com.sauti.billing;

public record BillingPlanChangeRequested(BillingPlanChangeRequest request, String businessName, String ownerEmail) { }
