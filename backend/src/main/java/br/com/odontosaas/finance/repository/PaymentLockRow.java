package br.com.odontosaas.finance.repository;

import java.math.BigDecimal;
import java.util.UUID;

/** Recebimento travado para estorno (select ... for update of pa) com a conta a receber da parcela. */
public record PaymentLockRow(UUID installmentId, BigDecimal amount, BigDecimal refundedAmount, UUID receivableId) {}
