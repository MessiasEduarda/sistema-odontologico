package br.com.odontosaas.finance.repository;

import java.math.BigDecimal;
import java.util.UUID;

/** Parcela travada para recebimento (select ... for update). */
public record InstallmentLockRow(UUID receivableId, BigDecimal amount, BigDecimal paidAmount, String status) {}
