package br.com.odontosaas.portal;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.communication.CommunicationService;
import br.com.odontosaas.consent.service.ConsentService;
import br.com.odontosaas.scheduling.service.AppointmentService;
import br.com.odontosaas.shared.ApiException;
import br.com.odontosaas.shared.Ids;
import br.com.odontosaas.shared.SecureTokens;
import br.com.odontosaas.shared.TenantRefs;
import br.com.odontosaas.tenancy.TenantContext;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Portal do paciente: acesso por link pessoal (token de 256 bits, válido por 30 dias, revogável), sem senha. O
 * paciente vê apenas os PRÓPRIOS dados não clínicos: próximas consultas, termos a assinar e contato.
 */
@Service
public class PortalService {

    static final Duration TTL = Duration.ofDays(30);

    public record AccessView(String url, Instant expiresAt, boolean emailQueued) {}

    public record PortalAppointment(
            UUID id, Instant startsAt, Instant endsAt, String professionalName, String status, boolean canRespond) {}

    public record PortalConsent(UUID id, String title, String body, String status, Instant createdAt) {}

    public record PortalContact(
            String fullName,
            String email,
            String mobile,
            String phone,
            boolean allowEmail,
            boolean allowSms,
            boolean allowWhatsapp) {}

    public record PortalOverview(
            String clinicName,
            String clinicPhone,
            PortalContact patient,
            List<PortalAppointment> upcoming,
            List<PortalConsent> pendingConsents) {}

    public record TokenBody(@NotBlank @Size(max = 64) String token) {}

    public record RespondBody(
            @NotBlank @Size(max = 64) String token,
            @NotNull UUID appointmentId,
            @NotBlank @Pattern(regexp = "CONFIRM|CANCEL") String action) {}

    public record SignBody(
            @NotBlank @Size(max = 64) String token,
            @NotNull UUID consentId,
            @NotBlank @Size(max = 160) String signerName,
            @Size(max = 20) String signerDocument,

            @NotBlank
            @Size(max = 400000)
            @Pattern(regexp = "^data:image/png;base64,[A-Za-z0-9+/=]+$", message = "assinatura inválida")
            String signatureImage,

            @AssertTrue(message = "é necessário declarar que leu e concorda com o termo")
            boolean accepted) {}

    public record ContactBody(
            @NotBlank @Size(max = 64) String token,
            @Email @Size(max = 254) String email,
            @Size(max = 20) String mobile,
            @Size(max = 20) String phone,
            boolean allowEmail,
            boolean allowSms,
            boolean allowWhatsapp) {}

    private final JdbcClient jdbc;
    private final TenantRefs refs;
    private final AuditService audit;
    private final AppointmentService appointments;
    private final ConsentService consents;
    private final CommunicationService communication;
    private final String publicUrl;

    public PortalService(
            JdbcClient jdbc,
            TenantRefs refs,
            AuditService audit,
            AppointmentService appointments,
            ConsentService consents,
            CommunicationService communication,
            @Value("${app.public-url}") String publicUrl) {
        this.jdbc = jdbc;
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
        jdbc.sql("update portal_access_tokens set revoked_at = now() where tenant_id = ? and patient_id = ?"
                        + " and revoked_at is null")
                .params(tenantId, patientId)
                .update();
        String raw = SecureTokens.generate();
        Instant expires = Instant.now().plus(TTL);
        jdbc.sql("""
                        insert into portal_access_tokens (id, tenant_id, patient_id, token_hash, expires_at, created_by)
                        values (?, ?, ?, ?, ?, ?)
                        """)
                .params(
                        Ids.newId(),
                        tenantId,
                        patientId,
                        SecureTokens.sha256Hex(raw),
                        Timestamp.from(expires),
                        TenantContext.userId().orElse(null))
                .update();
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
        jdbc.sql("update portal_access_tokens set revoked_at = now() where tenant_id = ? and patient_id = ?"
                        + " and revoked_at is null")
                .params(tenantId, patientId)
                .update();
    }

    // ------------------------------------------------------------------ Público

    private record Access(UUID id, UUID tenantId, UUID patientId) {}

    private <T> T withAccess(String rawToken, java.util.function.Function<Access, T> action) {
        ApiException invalid = ApiException.badRequest("INVALID_TOKEN", "Este link é inválido ou expirou.");
        if (!SecureTokens.looksValid(rawToken)) throw invalid;
        String hash = SecureTokens.sha256Hex(rawToken);
        Optional<Access> access = TenantContext.runWithToken(
                hash, () -> jdbc.sql("""
                        select id, tenant_id, patient_id from portal_access_tokens
                        where token_hash = ? and revoked_at is null and expires_at > now()
                        """).param(hash).query(Access.class).optional());
        Access a = access.orElseThrow(() -> invalid);
        return TenantContext.runAs(a.tenantId(), null, (Supplier<T>) () -> {
            jdbc.sql("update portal_access_tokens set last_used_at = now() where tenant_id = ? and id = ?")
                    .params(a.tenantId(), a.id())
                    .update();
            return action.apply(a);
        });
    }

    @Transactional
    public PortalOverview overview(String token) {
        return withAccess(token, a -> {
            record Clinic(String name, String phone) {}
            Clinic clinic = jdbc.sql(
                            "select t.name, s.phone from tenants t left join clinic_settings s on s.tenant_id = t.id"
                                    + " where t.id = ?")
                    .param(a.tenantId())
                    .query(Clinic.class)
                    .single();
            PortalContact contact = jdbc.sql("""
                            select full_name, email, mobile, phone, allow_email, allow_sms, allow_whatsapp
                            from patients where tenant_id = ? and id = ?
                            """)
                    .params(a.tenantId(), a.patientId())
                    .query(PortalContact.class)
                    .single();
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
            UUID owner = jdbc.sql("select patient_id from appointments where tenant_id = ? and id = ?")
                    .params(a.tenantId(), body.appointmentId())
                    .query(UUID.class)
                    .optional()
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
            jdbc.sql("""
                            update patients set email = ?, mobile = ?, phone = ?, allow_email = ?, allow_sms = ?,
                                allow_whatsapp = ?, updated_at = now(), version = version + 1
                            where tenant_id = ? and id = ?
                            """)
                    .params(
                            blank(body.email()),
                            blank(body.mobile()),
                            blank(body.phone()),
                            body.allowEmail(),
                            body.allowSms(),
                            body.allowWhatsapp(),
                            a.tenantId(),
                            a.patientId())
                    .update();
            audit.record(AuditAction.RECORD_UPDATED, "patient", a.patientId(), Map.of("via", "portal"));
            return null;
        });
        return overview(body.token());
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
