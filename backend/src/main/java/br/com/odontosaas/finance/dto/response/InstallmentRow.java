package br.com.odontosaas.finance.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InstallmentRow(
        UUID id,
        UUID receivableId,
        UUID patientId,
        String patientName,
        String description,
        int number,
        int installmentsCount,
        LocalDate dueDate,
        BigDecimal amount,
        BigDecimal paidAmount,
        BigDecimal balance,
        String status,
        boolean overdue) {}
