package br.com.odontosaas.clinic;

import br.com.odontosaas.clinic.repository.ProfessionalRepository;
import br.com.odontosaas.identity.security.AuthenticatedUser;
import br.com.odontosaas.identity.security.CurrentUser;
import br.com.odontosaas.identity.security.Permission;
import br.com.odontosaas.shared.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Impede que um registro clínico (evolução, procedimento, receituário, atestado, harmonização) seja emitido em nome
 * de outro profissional. O {@code professionalId} vem do corpo da requisição, então precisa ser conferido com o
 * usuário logado: só o administrador da clínica pode registrar em nome de qualquer profissional; os demais, apenas no
 * cadastro de profissional vinculado ao próprio usuário.
 */
@Component
public class ProfessionalAuthorship {

    private final ProfessionalRepository professionals;

    public ProfessionalAuthorship(ProfessionalRepository professionals) {
        this.professionals = professionals;
    }

    /** Sem {@code professionalId} (campo opcional) não há autoria a conferir. */
    public void require(UUID tenantId, UUID professionalId) {
        if (professionalId == null) return;
        AuthenticatedUser user = CurrentUser.require();
        if (user.has(Permission.CLINIC_SETTINGS_MANAGE)) return;
        boolean own = professionals.isLinkedToUser(tenantId, professionalId, user.userId());
        if (!own) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "PROFESSIONAL_MISMATCH",
                    "Acesso negado",
                    "Você só pode registrar em nome do seu próprio cadastro de profissional.");
        }
    }
}
