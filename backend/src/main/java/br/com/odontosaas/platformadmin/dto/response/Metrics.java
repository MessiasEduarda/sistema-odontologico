package br.com.odontosaas.platformadmin.dto.response;

import java.math.BigDecimal;

public record Metrics(
        long tenants,
        long activeTenants,
        long suspendedTenants,
        long trialing,
        long payingSubscriptions,
        long pastDue,
        BigDecimal monthlyRecurringRevenue,
        long users,
        long activeSessions24h) {}
