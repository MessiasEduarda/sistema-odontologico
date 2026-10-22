package br.com.odontosaas.subscription.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PlanView(
        UUID id,
        String code,
        String name,
        BigDecimal priceMonthly,
        Integer maxProfessionals,
        Integer maxPatients,
        List<String> features,
        boolean active,
        long tenants) {}
