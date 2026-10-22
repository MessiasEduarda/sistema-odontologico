package br.com.odontosaas.finance.repository;

import java.math.BigDecimal;
import java.util.UUID;

public record InstallmentLockRow(UUID receivableId, BigDecimal amount, BigDecimal paidAmount, String status) {}
