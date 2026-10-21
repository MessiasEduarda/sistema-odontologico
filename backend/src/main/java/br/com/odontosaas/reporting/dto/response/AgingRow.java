package br.com.odontosaas.reporting.dto.response;

import java.math.BigDecimal;

public record AgingRow(String bucket, long installments, BigDecimal amount) {}
