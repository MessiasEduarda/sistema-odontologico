package br.com.odontosaas.clinical;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.clinical.ClinicalDtos.ConsentLinkView;
import br.com.odontosaas.clinical.ClinicalDtos.ConsentListItem;
import br.com.odontosaas.clinical.ClinicalDtos.ConsentRequest;
import br.com.odontosaas.clinical.ClinicalDtos.ConsentTemplateRequest;
import br.com.odontosaas.clinical.ClinicalDtos.ConsentTemplateView;
import br.com.odontosaas.clinical.ClinicalDtos.ConsentView;
import br.com.odontosaas.clinical.ClinicalDtos.PublicConsentView;
import br.com.odontosaas.shared.ApiException;
import br.com.odontosaas.shared.Ids;
import br.com.odontosaas.shared.RequestInfo;
import br.com.odontosaas.shared.SecureTokens;
import br.com.odontosaas.shared.TenantRefs;
import br.com.odontosaas.tenancy.TenantContext;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Termos de consentimento. O texto final (com os dados do paciente já preenchidos) é gravado no momento da emissão,
 * junto com o SHA-256, e é exatamente esse texto que o paciente aceita. Assinatura: nome, documento, imagem da
 * assinatura desenhada, data/hora, IP e navegador.
 *
 * <p>Validade jurídica (ex.: exigência de certificado ICP-Brasil) deve ser avaliada por assessoria jurídica.
 */
@Service
public class ConsentService {

    static final Duration LINK_TTL = Duration.ofDays(7);
    private static final DateTimeFormatter BR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final JdbcClient jdbc;
    private final TenantRefs refs;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final String publicUrl;

    public ConsentService(
            JdbcClient jdbc,
            TenantRefs refs,
            AuditService audit,
            ApplicationEventPublisher events,
            @Value("${app.public-url}") String publicUrl) {
        this.jdbc = jdbc;
        this.refs = refs;
        this.audit = audit;
        this.events = events;
        this.publicUrl = publicUrl;
    }

    // ------------------------------------------------------------------ Modelos

    private static final String TEMPLATE_SELECT =
            "select id, family_id, title, category, body, version, active, created_at from consent_templates";

    private static String category(String value) {
        return value == null || value.isBlank() ? "DENTAL" : value;
    }

    @Transactional(readOnly = true)
    public List<ConsentTemplateView> templates(boolean includeOldVersions) {
        return jdbc.sql(TEMPLATE_SELECT + " where tenant_id = ?" + (includeOldVersions ? "" : " and active")
                        + " order by category, title, version desc")
                .param(TenantContext.requireTenantId())
                .query(ConsentTemplateView.class)
                .list();
    }

    @Transactional
    public ConsentTemplateView createTemplate(ConsentTemplateRequest r) {
        UUID id = Ids.newId();
        jdbc.sql("""
                        insert into consent_templates (id, tenant_id, family_id, title, category, body, version,
                            created_by) values (?, ?, ?, ?, ?, ?, 1, ?)
                        """)
                .params(
                        id,
                        TenantContext.requireTenantId(),
                        id,
                        r.title().strip(),
                        category(r.category()),
                        r.body().strip(),
                        actor())
                .update();
        audit.record(AuditAction.RECORD_CREATED, "consent_template", id, Map.of());
        return template(id);
    }

    /** Editar = nova versão. A versão anterior fica inativa, mas termos já emitidos mantêm o texto original. */
    @Transactional
    public ConsentTemplateView newVersion(UUID templateId, ConsentTemplateRequest r) {
        UUID tenantId = TenantContext.requireTenantId();
        ConsentTemplateView current = template(templateId);
        if (!current.active()) {
            throw ApiException.businessRule("OLD_VERSION", "Edite a versão mais recente do modelo.");
        }
        jdbc.sql("update consent_templates set active = false where tenant_id = ? and family_id = ?")
                .params(tenantId, current.familyId())
                .update();
        UUID id = Ids.newId();
        jdbc.sql("""
                        insert into consent_templates (id, tenant_id, family_id, title, category, body, version,
                            created_by) values (?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(
                        id,
                        tenantId,
                        current.familyId(),
                        r.title().strip(),
                        r.category() == null || r.category().isBlank() ? current.category() : r.category(),
                        r.body().strip(),
                        current.version() + 1,
                        actor())
                .update();
        audit.record(AuditAction.RECORD_UPDATED, "consent_template", id, Map.of("version", current.version() + 1));
        return template(id);
    }

    @Transactional
    public void archiveTemplate(UUID templateId) {
        UUID tenantId = TenantContext.requireTenantId();
        ConsentTemplateView current = template(templateId);
        jdbc.sql("update consent_templates set active = false where tenant_id = ? and family_id = ?")
                .params(tenantId, current.familyId())
                .update();
        audit.record(AuditAction.RECORD_DEACTIVATED, "consent_template", templateId, Map.of());
    }

    /** Adiciona os modelos de exemplo que a clínica ainda não tem (pelo título). Retorna quantos foram criados. */
    @Transactional
    public int addExamples(List<String> categories) {
        UUID tenantId = TenantContext.requireTenantId();
        int created = 0;
        for (ConsentExamples.Example e : ConsentExamples.ALL) {
            if (categories != null && !categories.isEmpty() && !categories.contains(e.category())) continue;
            boolean exists = jdbc.sql(
                            "select exists(select 1 from consent_templates where tenant_id = ? and title = ?)")
                    .params(tenantId, e.title())
                    .query(Boolean.class)
                    .single();
            if (exists) continue;
            UUID id = Ids.newId();
            jdbc.sql("""
                            insert into consent_templates (id, tenant_id, family_id, title, category, body, version,
                                created_by) values (?, ?, ?, ?, ?, ?, 1, ?)
                            """)
                    .params(id, tenantId, id, e.title(), e.category(), e.body(), actor())
                    .update();
            audit.record(AuditAction.RECORD_CREATED, "consent_template", id, Map.of("example", true));
            created++;
        }
        return created;
    }

    private ConsentTemplateView template(UUID id) {
        return jdbc.sql(TEMPLATE_SELECT + " where tenant_id = ? and id = ?")
                .params(TenantContext.requireTenantId(), id)
                .query(ConsentTemplateView.class)
                .optional()
                .orElseThrow(() -> ApiException.notFound("Modelo de termo"));
    }

    // ------------------------------------------------------------------ Termos do paciente

    private static final String SELECT = """
            select c.id, c.title, c.category, c.body, c.body_sha256, c.template_version, c.procedure_description,
                   p.name as professional_name, c.status, c.signed_at, c.signer_name, c.signer_document,
                   c.signature_image, c.signed_ip, c.revoked_at, c.revoke_reason, c.created_at
            from patient_consents c
            left join professionals p on p.tenant_id = c.tenant_id and p.id = c.professional_id
            """;

    @Transactional(readOnly = true)
    public List<ConsentView> list(UUID patientId) {
        UUID tenantId = TenantContext.requireTenantId();
        refs.require("patients", tenantId, patientId, "Paciente");
        return jdbc.sql(SELECT + " where c.tenant_id = ? and c.patient_id = ? order by c.created_at desc")
                .params(tenantId, patientId)
                .query(ConsentView.class)
                .list();
    }

    @Transactional(readOnly = true)
    public ConsentView get(UUID patientId, UUID consentId) {
        return jdbc.sql(SELECT + " where c.tenant_id = ? and c.patient_id = ? and c.id = ?")
                .params(TenantContext.requireTenantId(), patientId, consentId)
                .query(ConsentView.class)
                .optional()
                .orElseThrow(() -> ApiException.notFound("Termo"));
    }

    /** Termos de todos os pacientes (página Termos), com filtros de situação, categoria e nome do paciente. */
    @Transactional(readOnly = true)
    public List<ConsentListItem> listAll(String status, String category, String query) {
        String q = query == null || query.isBlank() ? null : "%" + query.strip().toLowerCase() + "%";
        return jdbc.sql("""
                        select c.id, c.patient_id, pa.full_name as patient_name, c.title, c.category, c.status,
                               p.name as professional_name, c.procedure_description, c.created_at, c.signed_at
                        from patient_consents c
                        join patients pa on pa.tenant_id = c.tenant_id and pa.id = c.patient_id
                        left join professionals p on p.tenant_id = c.tenant_id and p.id = c.professional_id
                        where c.tenant_id = :tenant
                          and (cast(:status as varchar) is null or c.status = :status)
                          and (cast(:category as varchar) is null or c.category = :category)
                          and (cast(:q as varchar) is null or lower(pa.full_name) like :q)
                        order by c.created_at desc limit 300
                        """)
                .param("tenant", TenantContext.requireTenantId())
                .param("status", status == null || status.isBlank() ? null : status)
                .param("category", category == null || category.isBlank() ? null : category)
                .param("q", q)
                .query(ConsentListItem.class)
                .list();
    }

    /** Emite o termo para o paciente: preenche os campos do modelo e guarda o texto final + hash. */
    @Transactional
    public ConsentView issue(UUID patientId, ConsentRequest r) {
        UUID tenantId = TenantContext.requireTenantId();
        refs.require("patients", tenantId, patientId, "Paciente");
        refs.require("professionals", tenantId, r.professionalId(), "Profissional");
        ConsentTemplateView template = template(r.templateId());
        if (!template.active()) throw ApiException.businessRule("OLD_VERSION", "Use a versão atual do modelo.");

        record Ctx(String patient, String cpf, String clinic, String professional, String cro) {}
        Ctx ctx = jdbc.sql("""
                        select pa.full_name as patient, pa.cpf, t.name as clinic, pr.name as professional,
                               case when pr.cro is not null then 'CRO-' || coalesce(pr.cro_state, '') || ' ' || pr.cro
                               end as cro
                        from patients pa join tenants t on t.id = pa.tenant_id
                        left join professionals pr on pr.tenant_id = pa.tenant_id and pr.id = ?
                        where pa.tenant_id = ? and pa.id = ?
                        """)
                .params(r.professionalId(), tenantId, patientId)
                .query(Ctx.class)
                .single();
        String body = template.body()
                .replace("{{paciente}}", ctx.patient())
                .replace("{{cpf}}", ctx.cpf() == null ? "não informado" : formatCpf(ctx.cpf()))
                .replace("{{clinica}}", ctx.clinic())
                .replace("{{profissional}}", ctx.professional() == null ? "não informado" : ctx.professional())
                .replace("{{cro}}", ctx.cro() == null ? "CRO não informado" : ctx.cro())
                .replace(
                        "{{procedimento}}",
                        r.procedureDescription() == null
                                ? ""
                                : r.procedureDescription().strip())
                .replace("{{data}}", LocalDate.now().format(BR_DATE));
        UUID id = Ids.newId();
        jdbc.sql("""
                        insert into patient_consents (id, tenant_id, patient_id, template_id, template_version, title,
                            category, body, body_sha256, procedure_description, professional_id, created_by)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(
                        id,
                        tenantId,
                        patientId,
                        template.id(),
                        template.version(),
                        template.title(),
                        template.category(),
                        body,
                        SecureTokens.sha256Hex(body),
                        r.procedureDescription(),
                        r.professionalId(),
                        actor())
                .update();
        audit.record(AuditAction.RECORD_CREATED, "patient_consent", id, Map.of());
        return get(patientId, id);
    }

    /** Assinatura presencial (tablet/tela da clínica). */
    @Transactional
    public ConsentView signInClinic(UUID patientId, UUID consentId, String name, String document, String image) {
        UUID tenantId = TenantContext.requireTenantId();
        ConsentView consent = get(patientId, consentId);
        sign(tenantId, consent, name, document, image);
        return get(patientId, consentId);
    }

    /** Gera link de assinatura (válido por 7 dias) e, se pedido, envia por e-mail ao paciente. */
    @Transactional
    public ConsentLinkView createLink(UUID patientId, UUID consentId, boolean sendEmail) {
        UUID tenantId = TenantContext.requireTenantId();
        ConsentView consent = get(patientId, consentId);
        if (!"PENDING".equals(consent.status())) {
            throw ApiException.businessRule("CONSENT_NOT_PENDING", "Este termo não está aguardando assinatura.");
        }
        String raw = SecureTokens.generate();
        Instant expires = Instant.now().plus(LINK_TTL);
        jdbc.sql("""
                        update patient_consents set sign_token_hash = ?, sign_token_expires_at = ?,
                            version = version + 1 where tenant_id = ? and id = ?
                        """)
                .params(SecureTokens.sha256Hex(raw), java.sql.Timestamp.from(expires), tenantId, consentId)
                .update();
        String url = publicUrl + "/termo#token=" + raw;
        events.publishEvent(new ConsentLinkRequested(tenantId, patientId, consent.title(), url, sendEmail));
        audit.record(AuditAction.RECORD_UPDATED, "patient_consent", consentId, Map.of("link", true));
        return new ConsentLinkView(url, expires);
    }

    @Transactional
    public ConsentView revoke(UUID patientId, UUID consentId, String reason) {
        UUID tenantId = TenantContext.requireTenantId();
        ConsentView consent = get(patientId, consentId);
        if ("REVOKED".equals(consent.status())) return consent;
        jdbc.sql("""
                        update patient_consents set status = 'REVOKED', revoked_at = now(), revoke_reason = ?,
                            sign_token_hash = null, version = version + 1 where tenant_id = ? and id = ?
                        """).params(reason.strip(), tenantId, consentId).update();
        audit.record(AuditAction.CONSENT_REVOKED, "patient_consent", consentId, Map.of());
        return get(patientId, consentId);
    }

    // ------------------------------------------------------------------ Público (link do paciente)

    private record Target(UUID tenantId, UUID patientId, UUID consentId) {}

    private Target resolve(String rawToken) {
        ApiException invalid = ApiException.badRequest("INVALID_TOKEN", "Este link é inválido ou expirou.");
        if (!SecureTokens.looksValid(rawToken)) throw invalid;
        String hash = SecureTokens.sha256Hex(rawToken);
        Optional<Target> target = TenantContext.runWithToken(
                hash, () -> jdbc.sql("""
                        select tenant_id, patient_id, id as consent_id from patient_consents
                        where sign_token_hash = ? and sign_token_expires_at > now()
                        """).param(hash).query(Target.class).optional());
        return target.orElseThrow(() -> invalid);
    }

    @Transactional(readOnly = true)
    public PublicConsentView publicLookup(String rawToken) {
        Target t = resolve(rawToken);
        return TenantContext.runAs(t.tenantId(), null, () -> {
            ConsentView c = get(t.patientId(), t.consentId());
            record Names(String clinic, String patient) {}
            Names names = jdbc.sql("select t.name as clinic, p.full_name as patient from tenants t join patients p"
                            + " on p.tenant_id = t.id where t.id = ? and p.id = ?")
                    .params(t.tenantId(), t.patientId())
                    .query(Names.class)
                    .single();
            return new PublicConsentView(
                    names.clinic(), names.patient(), c.title(), c.body(), c.status(), c.signedAt());
        });
    }

    @Transactional
    public PublicConsentView publicSign(String rawToken, String name, String document, String image) {
        Target t = resolve(rawToken);
        TenantContext.runAs(t.tenantId(), null, () -> {
            sign(t.tenantId(), get(t.patientId(), t.consentId()), name, document, image);
            return null;
        });
        return publicLookup(rawToken);
    }

    private void sign(UUID tenantId, ConsentView consent, String name, String document, String image) {
        if (!"PENDING".equals(consent.status())) {
            throw ApiException.businessRule("CONSENT_NOT_PENDING", "Este termo não está aguardando assinatura.");
        }
        jdbc.sql("""
                        update patient_consents set status = 'SIGNED', signed_at = now(), signer_name = ?,
                            signer_document = ?, signature_image = ?, signed_ip = ?, signed_user_agent = ?,
                            version = version + 1
                        where tenant_id = ? and id = ? and status = 'PENDING'
                        """)
                .params(
                        name.strip(),
                        document == null || document.isBlank() ? null : document.strip(),
                        image,
                        RequestInfo.clientIp(),
                        RequestInfo.userAgent(),
                        tenantId,
                        consent.id())
                .update();
        audit.record(AuditAction.CONSENT_SIGNED, "patient_consent", consent.id(), Map.of());
    }

    private static UUID actor() {
        return TenantContext.userId().orElse(null);
    }

    private static String formatCpf(String d) {
        return d.length() == 11
                ? d.substring(0, 3) + "." + d.substring(3, 6) + "." + d.substring(6, 9) + "-" + d.substring(9)
                : d;
    }
}
