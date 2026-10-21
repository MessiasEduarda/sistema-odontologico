package br.com.odontosaas.aesthetics.service;

import br.com.odontosaas.aesthetics.dto.request.AestheticRequest;
import br.com.odontosaas.aesthetics.dto.request.Region;
import br.com.odontosaas.aesthetics.dto.response.AestheticView;
import br.com.odontosaas.aesthetics.repository.AestheticRepository;
import br.com.odontosaas.aesthetics.repository.AestheticRepository.NewAesthetic;
import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.clinic.ProfessionalAuthorship;
import br.com.odontosaas.shared.ApiException;
import br.com.odontosaas.shared.Ids;
import br.com.odontosaas.shared.TenantRefs;
import br.com.odontosaas.tenancy.TenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AestheticService {

    private final AestheticRepository aesthetics;
    private final TenantRefs refs;
    private final AuditService audit;
    private final ProfessionalAuthorship authorship;

    public AestheticService(
            AestheticRepository aesthetics, TenantRefs refs, AuditService audit, ProfessionalAuthorship authorship) {
        this.aesthetics = aesthetics;
        this.refs = refs;
        this.audit = audit;
        this.authorship = authorship;
    }

    @Transactional(readOnly = true)
    public List<AestheticView> byPatient(UUID patientId) {
        UUID tenantId = TenantContext.requireTenantId();
        refs.require("patients", tenantId, patientId, "Paciente");
        return aesthetics.findByPatient(tenantId, patientId);
    }

    @Transactional(readOnly = true)
    public List<AestheticView> search(String batch, String query, LocalDate returnsUntil) {
        String b = batch == null || batch.isBlank() ? null : batch.strip().toLowerCase();
        String q = query == null || query.isBlank() ? null : "%" + query.strip().toLowerCase() + "%";
        return aesthetics.search(TenantContext.requireTenantId(), b, q, returnsUntil);
    }

    @Transactional
    public AestheticView create(UUID patientId, AestheticRequest r) {
        UUID tenantId = TenantContext.requireTenantId();
        boolean enabled = aesthetics.isAestheticsEnabled(tenantId);
        if (!enabled) {
            throw ApiException.businessRule(
                    "AESTHETICS_DISABLED",
                    "Ative a harmonização orofacial em Configurações para registrar aplicações.");
        }
        refs.require("patients", tenantId, patientId, "Paciente");
        refs.require("professionals", tenantId, r.professionalId(), "Profissional");
        authorship.require(tenantId, r.professionalId());
        if (r.consentId() != null) {
            boolean ok = aesthetics.consentBelongsToPatient(tenantId, r.consentId(), patientId);
            if (!ok) throw ApiException.notFound("Termo");
        }
        Instant performedAt = r.performedAt() == null ? Instant.now() : r.performedAt();
        if (performedAt.isAfter(Instant.now().plusSeconds(300))) {
            throw ApiException.invalidField("performedAt", "A data da aplicação não pode estar no futuro.");
        }
        if (r.expiryDate() != null
                && r.expiryDate()
                        .isBefore(performedAt.atZone(java.time.ZoneOffset.UTC).toLocalDate())) {
            throw ApiException.invalidField("expiryDate", "O produto estava vencido na data da aplicação.");
        }
        BigDecimal total = r.regions().stream().map(Region::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        UUID id = Ids.newId();
        aesthetics.insert(new NewAesthetic(
                id,
                tenantId,
                patientId,
                r.professionalId(),
                performedAt,
                r.procedureType(),
                r.productName().strip(),
                blank(r.manufacturer()),
                blank(r.anvisaRegistry()),
                r.batch().strip(),
                r.expiryDate(),
                r.unit(),
                r.regions(),
                total,
                blank(r.technique()),
                blank(r.notes()),
                r.returnDate(),
                r.consentId(),
                TenantContext.userId().orElse(null)));
        audit.record(
                AuditAction.RECORD_CREATED,
                "aesthetic_procedure",
                id,
                Map.of("batch", r.batch().strip()));
        return aesthetics.get(tenantId, id);
    }

    @Transactional
    public void voidRecord(UUID patientId, UUID id, String reason) {
        UUID tenantId = TenantContext.requireTenantId();
        int updated = aesthetics.voidRecord(
                tenantId, patientId, id, TenantContext.userId().orElse(null), reason.strip());
        if (updated == 0) throw ApiException.notFound("Registro de aplicação");
        audit.record(AuditAction.RECORD_VOIDED, "aesthetic_procedure", id, Map.of());
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
