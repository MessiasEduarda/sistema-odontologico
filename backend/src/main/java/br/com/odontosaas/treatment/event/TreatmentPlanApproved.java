package br.com.odontosaas.treatment.event;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TreatmentPlanApproved(
        UUID tenantId,
        UUID planId,
        UUID patientId,
        String description,
        BigDecimal total,
        int installments,
        LocalDate firstDueDate) {}
