package br.com.odontosaas.subscription.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SubscriptionView(
        UUID tenantId,
        String planCode,
        String planName,
        BigDecimal priceMonthly,
        String status,
        LocalDate trialEndsAt,
        LocalDate currentPeriodEnd,
        List<String> features,
        Integer maxProfessionals,
        Integer maxPatients,
        long professionals,
        long patients,
        String notes,
        Instant updatedAt,
        String paymentUrl,
        Billing billing) {}
