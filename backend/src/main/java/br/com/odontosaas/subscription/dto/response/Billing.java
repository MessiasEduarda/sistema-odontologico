package br.com.odontosaas.subscription.dto.response;

import java.time.LocalDate;

public record Billing(String state, LocalDate dueDate, LocalDate blockedFrom) {

    public static final Billing OK = new Billing("OK", null, null);

    public boolean blocked() {
        return "BLOCKED".equals(state);
    }
}
