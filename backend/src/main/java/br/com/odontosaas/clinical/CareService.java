package br.com.odontosaas.clinical;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.clinical.ClinicalDtos.CareIssueRequest;
import br.com.odontosaas.clinical.ClinicalDtos.CareTemplateRequest;
import br.com.odontosaas.clinical.ClinicalDtos.CareTemplateView;
import br.com.odontosaas.clinical.ClinicalDtos.DocumentRequest;
import br.com.odontosaas.clinical.ClinicalDtos.DocumentView;
import br.com.odontosaas.shared.ApiException;
import br.com.odontosaas.shared.Ids;
import br.com.odontosaas.tenancy.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orientações e cuidados ao paciente (pré e pós-procedimento). Os modelos são escritos pela própria clínica — o
 * sistema não traz textos clínicos prontos. Ao entregar, o texto vira documento do prontuário (tipo INSTRUCTIONS) e
 * pode ser enviado por e-mail.
 */
@Service
public class CareService {

    private static final String SELECT =
            "select id, title, category, moment, body, active, updated_at from care_instruction_templates";

    private final JdbcClient jdbc;
    private final AuditService audit;
    private final ClinicalService clinical;
    private final ApplicationEventPublisher events;

    public CareService(
            JdbcClient jdbc, AuditService audit, ClinicalService clinical, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.clinical = clinical;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<CareTemplateView> templates(boolean includeInactive) {
        return jdbc.sql(SELECT + " where tenant_id = ?" + (includeInactive ? "" : " and active")
                        + " order by category, moment, title")
                .param(TenantContext.requireTenantId())
                .query(CareTemplateView.class)
                .list();
    }

    private CareTemplateView template(UUID tenantId, UUID id) {
        return jdbc.sql(SELECT + " where tenant_id = ? and id = ?")
                .params(tenantId, id)
                .query(CareTemplateView.class)
                .optional()
                .orElseThrow(() -> ApiException.notFound("Modelo de orientação"));
    }

    @Transactional
    public CareTemplateView create(CareTemplateRequest r) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID id = Ids.newId();
        jdbc.sql("""
                        insert into care_instruction_templates (id, tenant_id, title, category, moment, body, active,
                            created_by) values (?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(
                        id,
                        tenantId,
                        r.title().strip(),
                        r.category(),
                        r.moment(),
                        r.body().strip(),
                        r.active(),
                        TenantContext.userId().orElse(null))
                .update();
        audit.record(AuditAction.RECORD_CREATED, "care_instruction_template", id, Map.of());
        return template(tenantId, id);
    }

    @Transactional
    public CareTemplateView update(UUID id, CareTemplateRequest r) {
        UUID tenantId = TenantContext.requireTenantId();
        template(tenantId, id);
        jdbc.sql("""
                        update care_instruction_templates set title = ?, category = ?, moment = ?, body = ?, active = ?,
                            updated_at = now() where tenant_id = ? and id = ?
                        """)
                .params(r.title().strip(), r.category(), r.moment(), r.body().strip(), r.active(), tenantId, id)
                .update();
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
