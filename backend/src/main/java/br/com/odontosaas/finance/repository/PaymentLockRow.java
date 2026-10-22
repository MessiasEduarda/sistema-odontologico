package br.com.odontosaas.finance.repository;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentLockRow(UUID installmentId, BigDecimal amount, BigDecimal refundedAmount, UUID receivableId) {}
