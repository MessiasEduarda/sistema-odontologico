package br.com.odontosaas.scheduling.service;

import br.com.odontosaas.scheduling.dto.response.AppointmentView;
import br.com.odontosaas.scheduling.dto.response.PublicAppointmentView;
import br.com.odontosaas.scheduling.repository.AppointmentRepository;
import br.com.odontosaas.shared.ApiException;
import br.com.odontosaas.shared.SecureTokens;
import br.com.odontosaas.tenancy.TenantContext;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentLinks {

    private final AppointmentRepository repository;
    private final AppointmentService appointments;

    public AppointmentLinks(AppointmentRepository repository, AppointmentService appointments) {
        this.repository = repository;
        this.appointments = appointments;
    }

    @Transactional
    public String issueToken(UUID appointmentId) {
        UUID tenantId = TenantContext.requireTenantId();
        String raw = SecureTokens.generate();
        repository.updateConfirmationTokenHash(tenantId, appointmentId, SecureTokens.sha256Hex(raw));
        return raw;
    }

    @Transactional
    public void markReminderSent(UUID appointmentId) {
        repository.markReminderSent(TenantContext.requireTenantId(), appointmentId);
    }

    @Transactional(readOnly = true)
    public PublicAppointmentView lookup(String rawToken) {
        Target target = resolve(rawToken);
        return TenantContext.runAs(target.tenantId(), null, () -> {
            AppointmentView a = appointments.get(target.appointmentId());
            String clinic = repository.clinicName(target.tenantId());
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
                () -> repository.findByTokenHash(hash).map(row -> new Target(row.tenantId(), row.appointmentId())));
        return target.orElseThrow(() -> invalid);
    }
}
