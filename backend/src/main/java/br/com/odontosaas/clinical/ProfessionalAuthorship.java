package br.com.odontosaas.clinical;

import br.com.odontosaas.identity.AuthenticatedUser;
import br.com.odontosaas.identity.CurrentUser;
import br.com.odontosaas.identity.Permission;
import br.com.odontosaas.shared.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Impede que um registro clínico (evolução, procedimento, receituário, atestado, harmonização) seja emitido em nome
 * de outro profissional. O {@code professionalId} vem do corpo da requisição, então precisa ser conferido com o
 * usuário logado: só o administrador da clínica pode registrar em nome de qualquer profissional; os demais, apenas no
 * cadastro de profissional vinculado ao próprio usuário.
 */
@Component
class ProfessionalAuthorship {

    private final JdbcClient jdbc;

    ProfessionalAuthorship(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Sem {@code professionalId} (campo opcional) não há autoria a conferir. */
    void require(UUID tenantId, UUID professionalId) {
        if (professionalId == null) return;
        AuthenticatedUser user = CurrentUser.require();
        if (user.has(Permission.CLINIC_SETTINGS_MANAGE)) return;
        boolean own = jdbc.sql(
                        "select exists(select 1 from professionals where tenant_id = ? and id = ? and user_id = ?)")
                .params(tenantId, professionalId, user.userId())
                .query(Boolean.class)
                .single();
        if (!own) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "PROFESSIONAL_MISMATCH",
                    "Acesso negado",
                    "Você só pode registrar em nome do seu próprio cadastro de profissional.");
        }
    }
}
