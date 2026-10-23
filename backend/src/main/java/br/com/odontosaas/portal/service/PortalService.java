package br.com.odontosaas.portal.service;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.consent.service.ConsentService;
import br.com.odontosaas.finance.service.FinanceService;
import br.com.odontosaas.medicalrecord.service.AttachmentService;
import br.com.odontosaas.medicalrecord.service.ClinicalService;
import br.com.odontosaas.portal.dto.request.ContactBody;
import br.com.odontosaas.portal.dto.request.RespondBody;
import br.com.odontosaas.portal.dto.request.SignBody;
import br.com.odontosaas.portal.dto.response.PortalAppointment;
import br.com.odontosaas.portal.dto.response.PortalConsent;
import br.com.odontosaas.portal.dto.response.PortalContact;
import br.com.odontosaas.portal.dto.response.PortalOverview;
import br.com.odontosaas.portal.dto.response.PortalRecords;
import br.com.odontosaas.portal.repository.PortalRepository;
import br.com.odontosaas.portal.repository.PortalRepository.ClinicRow;
import br.com.odontosaas.portal.service.PortalAuthService.Profile;
import br.com.odontosaas.scheduling.service.AppointmentService;
import br.com.odontosaas.shared.ApiException;
import br.com.odontosaas.tenancy.TenantContext;
import br.com.odontosaas.treatment.service.TreatmentService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortalService {

    private static final Set<String> VISIBLE_PLAN_STATUS = Set.of("PROPOSED", "APPROVED", "COMPLETED");

    private final PortalRepository repository;
    private final AuditService audit;
    private final AppointmentService appointments;
    private final ConsentService consents;
    private final ClinicalService clinical;
    private final AttachmentService attachments;
    private final TreatmentService treatments;
    private final FinanceService finance;

    public PortalService(
            PortalRepository repository,
            AuditService audit,
            AppointmentService appointments,
            ConsentService consents,
            ClinicalService clinical,
            AttachmentService attachments,
            TreatmentService treatments,
            FinanceService finance) {
        this.repository = repository;
        this.audit = audit;
        this.appointments = appointments;
        this.consents = consents;
        this.clinical = clinical;
        this.attachments = attachments;
        this.treatments = treatments;
        this.finance = finance;
    }

    private static <T> T inProfile(Profile p, Supplier<T> action) {
        return TenantContext.runAs(p.tenantId(), null, action);
    }

    @Transactional
    public PortalOverview overview(Profile p) {
        return inProfile(p, () -> {
            ClinicRow clinic = repository.clinic(p.tenantId());
            PortalContact contact = repository.contact(p.tenantId(), p.patientId());
            List<PortalAppointment> upcoming =
                    appointments
                            .list(Instant.now(), Instant.now().plus(Duration.ofDays(60)), null, p.patientId(), null)
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
            List<PortalConsent> pending = consents.list(p.patientId()).stream()
                    .filter(c -> "PENDING".equals(c.status()))
                    .map(c -> new PortalConsent(c.id(), c.title(), c.body(), c.status(), c.createdAt()))
                    .toList();
            audit.record(AuditAction.PORTAL_ACCESSED, "patient", p.patientId(), Map.of());
            return new PortalOverview(clinic.name(), clinic.phone(), contact, upcoming, pending);
        });
    }

    @Transactional
    public PortalRecords records(Profile p) {
        return inProfile(p, () -> {
            UUID patientId = p.patientId();
            PortalRecords records = new PortalRecords(
                    repository.clinicDetails(p.tenantId()),
                    repository.patient(p.tenantId(), patientId),
                    clinical.sharedDocuments(patientId),
                    consents.list(patientId).stream()
                            .filter(c -> "SIGNED".equals(c.status()))
                            .toList(),
                    treatments.list(patientId, null).stream()
                            .filter(x -> VISIBLE_PLAN_STATUS.contains(x.status()))
                            .map(x -> treatments.get(x.id()))
                            .toList(),
                    finance.receivables(null, patientId, null).stream()
                            .filter(r -> !"CANCELLED".equals(r.status()))
                            .map(r -> finance.receivable(r.id()))
                            .toList(),
                    attachments.listShared(patientId));
            audit.record(AuditAction.PORTAL_ACCESSED, "patient", patientId, Map.of("section", "documents"));
            return records;
        });
    }

    @Transactional
    public AttachmentService.StoredFile attachment(Profile p, UUID attachmentId) {
        return inProfile(p, () -> attachments.downloadShared(p.patientId(), attachmentId));
    }

    @Transactional
    public PortalOverview respond(Profile p, RespondBody body) {
        inProfile(p, () -> {
            UUID owner = repository
                    .appointmentPatientId(p.tenantId(), body.appointmentId())
                    .orElse(null);
            if (!p.patientId().equals(owner)) throw ApiException.notFound("Consulta");
            return appointments.respondAsPatient(body.appointmentId(), body.action());
        });
        return overview(p);
    }

    @Transactional
    public PortalOverview sign(Profile p, SignBody body) {
        inProfile(
                p,
                () -> consents.signInClinic(
                        p.patientId(),
                        body.consentId(),
                        body.signerName(),
                        body.signerDocument(),
                        body.signatureImage()));
        return overview(p);
    }

    @Transactional
    public PortalOverview updateContact(Profile p, ContactBody body) {
        inProfile(p, () -> {
            repository.updateContact(
                    p.tenantId(),
                    p.patientId(),
                    stripped(body.mobile()),
                    stripped(body.phone()),
                    body.allowEmail(),
                    body.allowSms(),
                    body.allowWhatsapp());
            audit.record(AuditAction.RECORD_UPDATED, "patient", p.patientId(), Map.of("via", "portal"));
            return null;
        });
        return overview(p);
    }

    private static String stripped(String value) {
        return value == null ? null : value.strip();
    }
}
