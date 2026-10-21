package br.com.odontosaas.subscription.dto.response;

import java.time.LocalDate;

/**
 * Situação da cobrança. {@code state}: OK; DUE (vencida, ainda dentro da tolerância: aviso para pagar); BLOCKED
 * (tolerância esgotada: só a tela de pagamento funciona). {@code blockedFrom}: primeiro dia de bloqueio.
 */
public record Billing(String state, LocalDate dueDate, LocalDate blockedFrom) {

    public static final Billing OK = new Billing("OK", null, null);

    public boolean blocked() {
        return "BLOCKED".equals(state);
    }
}
