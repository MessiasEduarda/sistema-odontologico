package br.com.odontosaas.shared;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Limites do plano da clínica (pacientes e profissionais ativos), consultados pelos serviços que criam ou reativam
 * esses cadastros. A regra vale no serviço, e não pela URL da requisição, para valer também para a importação em
 * massa, a reativação e qualquer rota nova.
 *
 * <p>Os métodos devem ser chamados dentro da transação que fará a inclusão: a implementação serializa a contagem por
 * clínica até o fim dela, então duas inclusões simultâneas não passam juntas do limite.
 */
public interface PlanLimits {

    /** Vagas restantes de pacientes ativos; {@link Integer#MAX_VALUE} quando o plano não tem limite. */
    int availablePatients(UUID tenantId);

    /** Vagas restantes de profissionais ativos; {@link Integer#MAX_VALUE} quando o plano não tem limite. */
    int availableProfessionals(UUID tenantId);

    default void requirePatientSlot(UUID tenantId) {
        if (availablePatients(tenantId) < 1) {
            throw limitReached("Limite de pacientes ativos do plano atingido.");
        }
    }

    default void requireProfessionalSlot(UUID tenantId) {
        if (availableProfessionals(tenantId) < 1) {
            throw limitReached("Limite de profissionais ativos do plano atingido.");
        }
    }

    private static ApiException limitReached(String detail) {
        return new ApiException(HttpStatus.FORBIDDEN, "PLAN_LIMIT_REACHED", "Indisponível no plano", detail);
    }
}
