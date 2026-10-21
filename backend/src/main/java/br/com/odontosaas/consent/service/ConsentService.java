package br.com.odontosaas.consent.service;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.consent.dto.request.ConsentRequest;
import br.com.odontosaas.consent.dto.request.ConsentTemplateRequest;
import br.com.odontosaas.consent.dto.response.ConsentLinkView;
import br.com.odontosaas.consent.dto.response.ConsentListItem;
import br.com.odontosaas.consent.dto.response.ConsentTemplateView;
import br.com.odontosaas.consent.dto.response.ConsentView;
import br.com.odontosaas.consent.dto.response.PublicConsentView;
import br.com.odontosaas.consent.event.ConsentLinkRequested;
import br.com.odontosaas.consent.repository.ConsentRepository;
import br.com.odontosaas.consent.repository.ConsentRepository.ConsentTokenRow;
import br.com.odontosaas.consent.repository.ConsentRepository.IssueContextRow;
import br.com.odontosaas.consent.repository.ConsentRepository.PublicNamesRow;
import br.com.odontosaas.consent.repository.ConsentTemplateRepository;
import br.com.odontosaas.consent.support.ConsentExamples;
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

    private final ConsentTemplateRepository templates;
    private final ConsentRepository consents;
    private final TenantRefs refs;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final String publicUrl;

    public ConsentService(
            ConsentTemplateRepository templates,
            ConsentRepository consents,
            TenantRefs refs,
            AuditService audit,
            ApplicationEventPublisher events,
            @Value("${app.public-url}") String publicUrl) {
        this.templates = templates;
        this.consents = consents;
        this.refs = refs;
        this.audit = audit;
        this.events = events;
        this.publicUrl = publicUrl;
    }

    // ------------------------------------------------------------------ Modelos

    private static String category(String value) {
        return value == null || value.isBlank() ? "DENTAL" : value;
    }

    @Transactional(readOnly = true)
    public List<ConsentTemplateView> templates(boolean includeOldVersions) {
        return templates.findAll(TenantContext.requireTenantId(), includeOldVersions);
    }

    @Transactional
    public ConsentTemplateView createTemplate(ConsentTemplateRequest r) {
        UUID id = Ids.newId();
        templates.insertFirstVersion(
                id,
                TenantContext.requireTenantId(),
                r.title().strip(),
                category(r.category()),
                r.body().strip(),
                actor());
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
        templates.deactivateFamily(tenantId, current.familyId());
        UUID id = Ids.newId();
        templates.insertVersion(
                id,
                tenantId,
                current.familyId(),
                r.title().strip(),
                r.category() == null || r.category().isBlank() ? current.category() : r.category(),
                r.body().strip(),
                current.version() + 1,
                actor());
        audit.record(AuditAction.RECORD_UPDATED, "consent_template", id, Map.of("version", current.version() + 1));
        return template(id);
    }

    @Transactional
    public void archiveTemplate(UUID templateId) {
        UUID tenantId = TenantContext.requireTenantId();
        ConsentTemplateView current = template(templateId);
        templates.deactivateFamily(tenantId, current.familyId());
        audit.record(AuditAction.RECORD_DEACTIVATED, "consent_template", templateId, Map.of());
    }

    /** Adiciona os modelos de exemplo que a clínica ainda não tem (pelo título). Retorna quantos foram criados. */
    @Transactional
    public int addExamples(List<String> categories) {
        UUID tenantId = TenantContext.requireTenantId();
        int created = 0;
        for (ConsentExamples.Example e : ConsentExamples.ALL) {
            if (categories != null && !categories.isEmpty() && !categories.contains(e.category())) continue;
            boolean exists = templates.existsByTitle(tenantId, e.title());
            if (exists) continue;
            UUID id = Ids.newId();
            templates.insertFirstVersion(id, tenantId, e.title(), e.category(), e.body(), actor());
            audit.record(AuditAction.RECORD_CREATED, "consent_template", id, Map.of("example", true));
            created++;
        }
        return created;
    }

    private ConsentTemplateView template(UUID id) {
        return templates
                .find(TenantContext.requireTenantId(), id)
                .orElseThrow(() -> ApiException.notFound("Modelo de termo"));
    }

    // ------------------------------------------------------------------ Termos do paciente

    @Transactional(readOnly = true)
    public List<ConsentView> list(UUID patientId) {
        UUID tenantId = TenantContext.requireTenantId();
        refs.require("patients", tenantId, patientId, "Paciente");
        return consents.findByPatient(tenantId, patientId);
    }

    @Transactional(readOnly = true)
    public ConsentView get(UUID patientId, UUID consentId) {
        return consents.find(TenantContext.requireTenantId(), patientId, consentId)
                .orElseThrow(() -> ApiException.notFound("Termo"));
    }

    /** Termos de todos os pacientes (página Termos), com filtros de situação, categoria e nome do paciente. */
    @Transactional(readOnly = true)
    public List<ConsentListItem> listAll(String status, String category, String query) {
        String q = query == null || query.isBlank() ? null : "%" + query.strip().toLowerCase() + "%";
        return consents.findAll(
                TenantContext.requireTenantId(),
                status == null || status.isBlank() ? null : status,
                category == null || category.isBlank() ? null : category,
                q);
    }

    /** Emite o termo para o paciente: preenche os campos do modelo e guarda o texto final + hash. */
    @Transactional
    public ConsentView issue(UUID patientId, ConsentRequest r) {
        UUID tenantId = TenantContext.requireTenantId();
        refs.require("patients", tenantId, patientId, "Paciente");
        refs.require("professionals", tenantId, r.professionalId(), "Profissional");
        ConsentTemplateView template = template(r.templateId());
        if (!template.active()) throw ApiException.businessRule("OLD_VERSION", "Use a versão atual do modelo.");

        IssueContextRow ctx = consents.findIssueContext(tenantId, patientId, r.professionalId());
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
        consents.insert(
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
                actor());
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
        consents.saveSignToken(tenantId, consentId, SecureTokens.sha256Hex(raw), expires);
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
        consents.revoke(tenantId, consentId, reason.strip());
        audit.record(AuditAction.CONSENT_REVOKED, "patient_consent", consentId, Map.of());
        return get(patientId, consentId);
    }

    // ------------------------------------------------------------------ Público (link do paciente)

    private ConsentTokenRow resolve(String rawToken) {
        ApiException invalid = ApiException.badRequest("INVALID_TOKEN", "Este link é inválido ou expirou.");
        if (!SecureTokens.looksValid(rawToken)) throw invalid;
        String hash = SecureTokens.sha256Hex(rawToken);
        Optional<ConsentTokenRow> target = TenantContext.runWithToken(hash, () -> consents.findByTokenHash(hash));
        return target.orElseThrow(() -> invalid);
    }

    @Transactional(readOnly = true)
    public PublicConsentView publicLookup(String rawToken) {
        ConsentTokenRow t = resolve(rawToken);
        return TenantContext.runAs(t.tenantId(), null, () -> {
            ConsentView c = get(t.patientId(), t.consentId());
            PublicNamesRow names = consents.findPublicNames(t.tenantId(), t.patientId());
            return new PublicConsentView(
                    names.clinic(), names.patient(), c.title(), c.body(), c.status(), c.signedAt());
        });
    }

    @Transactional
    public PublicConsentView publicSign(String rawToken, String name, String document, String image) {
        ConsentTokenRow t = resolve(rawToken);
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
        consents.markSigned(
                tenantId,
                consent.id(),
                name.strip(),
                document == null || document.isBlank() ? null : document.strip(),
                image,
                RequestInfo.clientIp(),
                RequestInfo.userAgent());
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
