package br.com.odontosaas.scheduling;

import br.com.odontosaas.scheduling.SchedulingDtos.AppointmentView;
import br.com.odontosaas.scheduling.SchedulingDtos.PublicAppointmentView;
import br.com.odontosaas.shared.ApiException;
import br.com.odontosaas.shared.SecureTokens;
import br.com.odontosaas.tenancy.TenantContext;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Link de confirmação/cancelamento enviado ao paciente. O token é a única autorização: o RLS libera somente o
 * agendamento com aquele hash, e a resposta pública não contém dados clínicos.
 */
@Service
public class AppointmentLinks {

    private final JdbcClient jdbc;
    private final AppointmentService appointments;

    public AppointmentLinks(JdbcClient jdbc, AppointmentService appointments) {
        this.jdbc = jdbc;
        this.appointments = appointments;
    }

    /** Gera (ou renova) o token do agendamento. Exige o contexto da clínica dona do agendamento. */
    @Transactional
    public String issueToken(UUID appointmentId) {
        UUID tenantId = TenantContext.requireTenantId();
        String raw = SecureTokens.generate();
        jdbc.sql("update appointments set confirmation_token_hash = ? where tenant_id = ? and id = ?")
                .params(SecureTokens.sha256Hex(raw), tenantId, appointmentId)
                .update();
        return raw;
    }

    @Transactional
    public void markReminderSent(UUID appointmentId) {
        jdbc.sql("update appointments set reminder_sent_at = now() where tenant_id = ? and id = ?")
                .params(TenantContext.requireTenantId(), appointmentId)
                .update();
    }

    @Transactional(readOnly = true)
    public PublicAppointmentView lookup(String rawToken) {
        Target target = resolve(rawToken);
        return TenantContext.runAs(target.tenantId(), null, () -> {
            AppointmentView a = appointments.get(target.appointmentId());
            String clinic = jdbc.sql("select name from tenants where id = ?")
                    .param(target.tenantId())
                    .query(String.class)
                    .single();
            return new PublicAppointmentView(
                    clinic,
                    a.patientName().split(" ")[0],
                    a.professionalName(),
                    a.startsAt(),
                    a.endsAt(),
                    a.status(),
                    AppointmentService.canPatientRespond(a));
        });
    }

    @Transactional
    public PublicAppointmentView respond(String rawToken, String action) {
        Target target = resolve(rawToken);
        TenantContext.runAs(
                target.tenantId(), null, () -> appointments.respondAsPatient(target.appointmentId(), action));
        return lookup(rawToken);
    }

    private record Target(UUID tenantId, UUID appointmentId) {}

    private Target resolve(String rawToken) {
        ApiException invalid = ApiException.badRequest("INVALID_TOKEN", "Este link é inválido ou expirou.");
        if (!SecureTokens.looksValid(rawToken)) throw invalid;
        String hash = SecureTokens.sha256Hex(rawToken);
        Optional<Target> target = TenantContext.runWithToken(
                hash,
                () -> jdbc.sql(
                                "select tenant_id, id as appointment_id from appointments where confirmation_token_hash = ?")
                        .param(hash)
                        .query(Target.class)
                        .optional());
        return target.orElseThrow(() -> invalid);
    }
}
