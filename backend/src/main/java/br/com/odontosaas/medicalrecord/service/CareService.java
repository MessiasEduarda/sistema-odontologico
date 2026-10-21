package br.com.odontosaas.medicalrecord.service;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.medicalrecord.dto.request.CareIssueRequest;
import br.com.odontosaas.medicalrecord.dto.request.CareTemplateRequest;
import br.com.odontosaas.medicalrecord.dto.request.DocumentRequest;
import br.com.odontosaas.medicalrecord.dto.response.CareTemplateView;
import br.com.odontosaas.medicalrecord.dto.response.DocumentView;
import br.com.odontosaas.medicalrecord.event.CareInstructionsSent;
import br.com.odontosaas.medicalrecord.repository.CareTemplateRepository;
import br.com.odontosaas.shared.ApiException;
import br.com.odontosaas.shared.Flags;
import br.com.odontosaas.shared.Ids;
import br.com.odontosaas.tenancy.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orientações e cuidados ao paciente (pré e pós-procedimento). Os modelos são escritos pela própria clínica — o
 * sistema não traz textos clínicos prontos. Ao entregar, o texto vira documento do prontuário (tipo INSTRUCTIONS) e
 * pode ser enviado por e-mail.
 */
@Service
public class CareService {

    private final CareTemplateRepository careTemplates;
    private final AuditService audit;
    private final ClinicalService clinical;
    private final ApplicationEventPublisher events;

    public CareService(
            CareTemplateRepository careTemplates,
            AuditService audit,
            ClinicalService clinical,
            ApplicationEventPublisher events) {
        this.careTemplates = careTemplates;
        this.audit = audit;
        this.clinical = clinical;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<CareTemplateView> templates(boolean includeInactive) {
        return careTemplates.findAll(TenantContext.requireTenantId(), includeInactive);
    }

    private CareTemplateView template(UUID tenantId, UUID id) {
        return careTemplates.find(tenantId, id).orElseThrow(() -> ApiException.notFound("Modelo de orientação"));
    }

    @Transactional
    public CareTemplateView create(CareTemplateRequest r) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID id = Ids.newId();
        careTemplates.insert(
                id,
                tenantId,
                r.title().strip(),
                r.category(),
                r.moment(),
                r.body().strip(),
                Flags.activeIfOmitted(r.active()),
                TenantContext.userId().orElse(null));
        audit.record(AuditAction.RECORD_CREATED, "care_instruction_template", id, Map.of());
        return template(tenantId, id);
    }

    @Transactional
    public CareTemplateView update(UUID id, CareTemplateRequest r) {
        UUID tenantId = TenantContext.requireTenantId();
        template(tenantId, id);
        careTemplates.update(
                tenantId,
                id,
                r.title().strip(),
                r.category(),
                r.moment(),
                r.body().strip(),
                r.active());
        audit.record(AuditAction.RECORD_UPDATED, "care_instruction_template", id, Map.of());
        return template(tenantId, id);
    }

    /** Entrega ao paciente: registra no prontuário e, se pedido, envia por e-mail (respeitando o consentimento). */
    @Transactional
    public DocumentView issue(UUID patientId, CareIssueRequest r) {
        DocumentView doc = clinical.addDocument(
                patientId, new DocumentRequest(r.professionalId(), "INSTRUCTIONS", r.title(), r.content()));
        if (r.sendEmail()) {
            events.publishEvent(
                    new CareInstructionsSent(TenantContext.requireTenantId(), patientId, doc.title(), doc.content()));
        }
        return doc;
    }
}
