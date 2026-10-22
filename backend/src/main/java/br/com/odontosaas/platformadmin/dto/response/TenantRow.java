package br.com.odontosaas.platformadmin.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TenantRow(
        UUID id,
        String name,
        String slug,
        String status,
        Instant createdAt,
        String planCode,
        String planName,
        String subscriptionStatus,
        LocalDate trialEndsAt,
        BigDecimal priceMonthly) {}
