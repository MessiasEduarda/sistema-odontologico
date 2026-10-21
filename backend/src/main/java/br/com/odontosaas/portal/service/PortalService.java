package br.com.odontosaas.portal.service;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.communication.service.CommunicationService;
import br.com.odontosaas.consent.service.ConsentService;
import br.com.odontosaas.portal.dto.request.ContactBody;
import br.com.odontosaas.portal.dto.request.RespondBody;
import br.com.odontosaas.portal.dto.request.SignBody;
import br.com.odontosaas.portal.dto.response.AccessView;
import br.com.odontosaas.portal.dto.response.PortalAppointment;
import br.com.odontosaas.portal.dto.response.PortalConsent;
import br.com.odontosaas.portal.dto.response.PortalContact;
import br.com.odontosaas.portal.dto.response.PortalOverview;
import br.com.odontosaas.portal.repository.PortalRepository;
import br.com.odontosaas.portal.repository.PortalRepository.AccessRow;
import br.com.odontosaas.portal.repository.PortalRepository.ClinicRow;
import br.com.odontosaas.scheduling.service.AppointmentService;
import br.com.odontosaas.shared.ApiException;
import br.com.odontosaas.shared.Ids;
import br.com.odontosaas.shared.SecureTokens;
import br.com.odontosaas.shared.TenantRefs;
import br.com.odontosaas.tenancy.TenantContext;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Portal do paciente: acesso por link pessoal (token de 256 bits, válido por 30 dias, revogável), sem senha. O
 * paciente vê apenas os PRÓPRIOS dados não clínicos: próximas consultas, termos a assinar e contato.
 */
@Service
public class PortalService {

    static final Duration TTL = Duration.ofDays(30);

    private final PortalRepository repository;
    private final TenantRefs refs;
    private final AuditService audit;
    private final AppointmentService appointments;
    private final ConsentService consents;
    private final CommunicationService communication;
    private final String publicUrl;

    public PortalService(
            PortalRepository repository,
            TenantRefs refs,
            AuditService audit,
            AppointmentService appointments,
            ConsentService consents,
            CommunicationService communication,
            @Value("${app.public-url}") String publicUrl) {
        this.repository = repository;
        this.refs = refs;
        this.audit = audit;
        this.appointments = appointments;
        this.consents = consents;
        this.communication = communication;
        this.publicUrl = publicUrl;
    }

    /** A clínica gera (ou renova) o link do paciente. Links anteriores são revogados. */
    @Transactional
    public AccessView grant(UUID patientId, boolean sendEmail) {
        UUID tenantId = TenantContext.requireTenantId();
        refs.require("patients", tenantId, patientId, "Paciente");
        repository.revokeTokens(tenantId, patientId);
        String raw = SecureTokens.generate();
        Instant expires = Instant.now().plus(TTL);
        repository.insertToken(
                Ids.newId(),
                tenantId,
                patientId,
                SecureTokens.sha256Hex(raw),
                Timestamp.from(expires),
                TenantContext.userId().orElse(null));
        String url = publicUrl + "/portal#token=" + raw;
        boolean queued = sendEmail
                && communication
                        .enqueue(patientId, null, "PORTAL_ACCESS", Map.of("link", url))
                        .isPresent();
        audit.record(AuditAction.PORTAL_ACCESS_GRANTED, "patient", patientId, Map.of("email", queued));
        return new AccessView(url, expires, queued);
    }

    @Transactional
    public void revoke(UUID patientId) {
        UUID tenantId = TenantContext.requireTenantId();
        refs.require("patients", tenantId, patientId, "Paciente");
        repository.revokeTokens(tenantId, patientId);
    }

    // ------------------------------------------------------------------ Público

    private <T> T withAccess(String rawToken, java.util.function.Function<AccessRow, T> action) {
        ApiException invalid = ApiException.badRequest("INVALID_TOKEN", "Este link é inválido ou expirou.");
        if (!SecureTokens.looksValid(rawToken)) throw invalid;
        String hash = SecureTokens.sha256Hex(rawToken);
        Optional<AccessRow> access = TenantContext.runWithToken(hash, () -> repository.findValidAccess(hash));
        AccessRow a = access.orElseThrow(() -> invalid);
        return TenantContext.runAs(a.tenantId(), null, (Supplier<T>) () -> {
            repository.touchLastUsed(a.tenantId(), a.id());
            return action.apply(a);
        });
    }

    @Transactional
    public PortalOverview overview(String token) {
        return withAccess(token, a -> {
            ClinicRow clinic = repository.clinic(a.tenantId());
            PortalContact contact = repository.contact(a.tenantId(), a.patientId());
            List<PortalAppointment> upcoming =
                    appointments
                            .list(Instant.now(), Instant.now().plus(Duration.ofDays(60)), null, a.patientId(), null)
                            .stream()
                            .filter(x -> !"CANCELLED".equals(x.status()))
                            .map(x -> new PortalAppointment(
                                    x.id(),
                                    x.startsAt(),
                                    x.endsAt(),
                                    x.professionalName(),
                                    x.status(),
                                    AppointmentService.canPatientRespond(x)))
                            .toList();
            List<PortalConsent> pending = consents.list(a.patientId()).stream()
                    .filter(c -> "PENDING".equals(c.status()))
                    .map(c -> new PortalConsent(c.id(), c.title(), c.body(), c.status(), c.createdAt()))
                    .toList();
            audit.record(AuditAction.PORTAL_ACCESSED, "patient", a.patientId(), Map.of());
            return new PortalOverview(clinic.name(), clinic.phone(), contact, upcoming, pending);
        });
    }

    @Transactional
    public PortalOverview respond(RespondBody body) {
        withAccess(body.token(), a -> {
            UUID owner = repository
                    .appointmentPatientId(a.tenantId(), body.appointmentId())
                    .orElse(null);
            if (!a.patientId().equals(owner)) throw ApiException.notFound("Consulta");
            return appointments.respondAsPatient(body.appointmentId(), body.action());
        });
        return overview(body.token());
    }

    @Transactional
    public PortalOverview sign(SignBody body) {
        withAccess(
                body.token(),
                a -> consents.signInClinic(
                        a.patientId(),
                        body.consentId(),
                        body.signerName(),
                        body.signerDocument(),
                        body.signatureImage()));
        return overview(body.token());
    }

    @Transactional
    public PortalOverview updateContact(ContactBody body) {
        withAccess(body.token(), a -> {
            repository.updateContact(
                    a.tenantId(),
                    a.patientId(),
                    blank(body.email()),
                    blank(body.mobile()),
                    blank(body.phone()),
                    body.allowEmail(),
                    body.allowSms(),
                    body.allowWhatsapp());
            audit.record(AuditAction.RECORD_UPDATED, "patient", a.patientId(), Map.of("via", "portal"));
            return null;
        });
        return overview(body.token());
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
