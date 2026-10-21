package br.com.odontosaas.finance.dto.response;

import java.math.BigDecimal;

public record MethodTotal(String method, BigDecimal amount) {}
