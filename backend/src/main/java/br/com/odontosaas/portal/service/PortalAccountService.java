package br.com.odontosaas.portal.service;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.portal.dto.response.PortalAccountStatus;
import br.com.odontosaas.portal.repository.PortalAccountRepository;
import br.com.odontosaas.portal.repository.PortalAccountRepository.AccountRow;
import br.com.odontosaas.portal.repository.PortalAccountRepository.LinkRow;
import br.com.odontosaas.portal.repository.PortalAccountRepository.PatientInfo;
import br.com.odontosaas.shared.ApiException;
import br.com.odontosaas.shared.SecureTokens;
import br.com.odontosaas.shared.TenantRefs;
import br.com.odontosaas.tenancy.TenantContext;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortalAccountService {

    static final Duration ACTIVATION_TTL = Duration.ofHours(48);

    private final PortalAccountRepository accounts;
    private final TenantRefs refs;
    private final AuditService audit;
    private final PortalMailer mailer;

    public PortalAccountService(
            PortalAccountRepository accounts, TenantRefs refs, AuditService audit, PortalMailer mailer) {
        this.accounts = accounts;
        this.refs = refs;
        this.audit = audit;
        this.mailer = mailer;
    }

    @Transactional(readOnly = true)
    public PortalAccountStatus status(UUID patientId) {
        UUID tenantId = TenantContext.requireTenantId();
        refs.require("patients", tenantId, patientId, "Paciente");
        Optional<LinkRow> link = accounts.findLink(tenantId, patientId);
        if (link.isEmpty()) return new PortalAccountStatus("NONE", null, null, null, null);
        AccountRow account = accounts.find(link.get().accountId()).orElseThrow();
        String status =
                link.get().blockedAt() != null ? "BLOCKED" : "ACTIVE".equals(account.status()) ? "ACTIVE" : "INVITED";
        return new PortalAccountStatus(
                status, account.email(), link.get().invitedAt(), account.activatedAt(), account.lastLoginAt());
    }

    @Transactional
    public PortalAccountStatus invite(UUID patientId) {
        UUID tenantId = TenantContext.requireTenantId();
        refs.require("patients", tenantId, patientId, "Paciente");
        PatientInfo patient =
                accounts.patient(tenantId, patientId).orElseThrow(() -> ApiException.notFound("Paciente"));
        if (patient.email() == null || patient.email().isBlank()) {
            throw ApiException.businessRule(
                    "PATIENT_WITHOUT_EMAIL", "Cadastre o e-mail do paciente para enviar o acesso ao portal.");
        }
        AccountRow account = accounts.findByEmail(patient.email())
                .orElseGet(() ->
                        accounts.find(accounts.insertAccount(patient.email())).orElseThrow());
        Optional<LinkRow> previous = accounts.findLink(tenantId, patientId);
        if (previous.isPresent() && !previous.get().accountId().equals(account.id())) {
            accounts.revokeProfileSessions(tenantId, patientId);
        }
        accounts.saveLink(tenantId, patientId, account.id());

        String firstName = patient.fullName().split(" ")[0];
        String clinicName = accounts.clinicName(tenantId);
        if ("ACTIVE".equals(account.status())) {
            mailer.sendNewClinicAccess(account.email(), firstName, clinicName);
        } else {
            accounts.invalidateTokens(account.id(), "ACTIVATION");
            String raw = SecureTokens.generate();
            accounts.insertToken(
                    account.id(),
                    "ACTIVATION",
                    SecureTokens.sha256Hex(raw),
                    tenantId,
                    patientId,
                    Instant.now().plus(ACTIVATION_TTL));
            mailer.sendActivation(account.email(), firstName, clinicName, raw);
        }
        audit.record(AuditAction.PORTAL_ACCESS_GRANTED, "patient", patientId, Map.of());
        return status(patientId);
    }

    @Transactional
    public PortalAccountStatus setBlocked(UUID patientId, boolean blocked) {
        UUID tenantId = TenantContext.requireTenantId();
        refs.require("patients", tenantId, patientId, "Paciente");
        if (accounts.setBlocked(tenantId, patientId, blocked) == 0) {
            throw ApiException.notFound("Acesso ao portal");
        }
        if (blocked) accounts.revokeProfileSessions(tenantId, patientId);
        audit.record(
                blocked ? AuditAction.PORTAL_ACCESS_BLOCKED : AuditAction.PORTAL_ACCESS_GRANTED,
                "patient",
                patientId,
                Map.of("blocked", blocked));
        return status(patientId);
    }

    @Transactional
    public void emailChanged(UUID tenantId, UUID patientId) {
        TenantContext.runAs(tenantId, TenantContext.userId().orElse(null), () -> {
            accounts.revokeProfileSessions(tenantId, patientId);
            accounts.deleteLink(tenantId, patientId);
            return invite(patientId);
        });
    }

    @Transactional
    public void registered(UUID tenantId, UUID patientId) {
        TenantContext.runAs(tenantId, TenantContext.userId().orElse(null), () -> invite(patientId));
    }
}
