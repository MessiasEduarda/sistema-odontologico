package br.com.odontosaas.treatment.event;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Publicado na aprovação do orçamento quando a clínica pede geração da conta a receber (o Financeiro reage). */
public record TreatmentPlanApproved(
        UUID tenantId,
        UUID planId,
        UUID patientId,
        String description,
        BigDecimal total,
        int installments,
        LocalDate firstDueDate) {}
