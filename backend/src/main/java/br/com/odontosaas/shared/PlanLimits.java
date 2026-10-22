package br.com.odontosaas.shared;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public interface PlanLimits {

    int availablePatients(UUID tenantId);

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
