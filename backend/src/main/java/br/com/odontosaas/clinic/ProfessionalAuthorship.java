package br.com.odontosaas.clinic;

import br.com.odontosaas.clinic.repository.ProfessionalRepository;
import br.com.odontosaas.identity.security.AuthenticatedUser;
import br.com.odontosaas.identity.security.CurrentUser;
import br.com.odontosaas.identity.security.Permission;
import br.com.odontosaas.shared.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ProfessionalAuthorship {

    private final ProfessionalRepository professionals;

    public ProfessionalAuthorship(ProfessionalRepository professionals) {
        this.professionals = professionals;
    }

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
