package br.com.odontosaas.patients.service;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.shared.ApiException;
import br.com.odontosaas.tenancy.TenantContext;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Portabilidade (LGPD, art. 18, V): todos os dados do paciente na clínica, em JSON estruturado. Campos internos de
 * segurança (hashes de tokens, chave de armazenamento) e a clínica (tenant_id) ficam de fora; arquivos anexos vão
 * como lista (nome, tipo, data) — o arquivo em si é baixado pelo prontuário.
 */
@Service
public class PatientExportService {

    /** Colunas que nunca saem na exportação. */
    private static final String HIDDEN =
            " - 'tenant_id' - 'confirmation_token_hash' - 'sign_token_hash' - 'token_hash' - 'storage_key'";

    /** Seções diretas do paciente: rótulo → tabela. */
    private static final Map<String, String> SECTIONS = sections();

    private static Map<String, String> sections() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("consultas", "appointments");
        map.put("anamneses", "anamneses");
        map.put("odontograma", "dental_chart_entries");
        map.put("evolucoes", "clinical_notes");
        map.put("procedimentos_realizados", "performed_procedures");
        map.put("documentos_emitidos", "clinical_documents");
        map.put("termos_de_consentimento", "patient_consents");
        map.put("harmonizacao", "aesthetic_procedures");
        map.put("anexos", "attachments");
        map.put("orcamentos", "treatment_plans");
        map.put("contas_a_receber", "receivables");
        map.put("mensagens_enviadas", "outbound_messages");
        map.put("lista_de_espera", "waitlist_entries");
        return map;
    }

    private final JdbcClient jdbc;
    private final AuditService audit;
    private final JsonMapper json;

    public PatientExportService(JdbcClient jdbc, AuditService audit, JsonMapper json) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.json = json;
    }

    /** Não é somente leitura: a exportação fica registrada na auditoria. */
    @Transactional
    public Map<String, Object> export(UUID patientId) {
        UUID tenantId = TenantContext.requireTenantId();
        String patient = jdbc.sql(
                        "select (to_jsonb(p)" + HIDDEN + ")::text from patients p where tenant_id = ? and id = ?")
                .params(tenantId, patientId)
                .query(String.class)
                .optional()
                .orElseThrow(() -> ApiException.notFound("Paciente"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("formato", "Odonto SaaS — exportação de dados do paciente (LGPD, art. 18, V)");
        result.put("geradoEm", Instant.now().toString());
        result.put(
                "clinica",
                jdbc.sql("select name from tenants where id = ?")
                        .param(tenantId)
                        .query(String.class)
                        .single());
        result.put("paciente", parse(patient));
        SECTIONS.forEach((label, table) -> result.put(label, rows(table, "patient_id = ?", tenantId, patientId)));
        result.put(
                "itens_dos_orcamentos",
                rows(
                        "treatment_plan_items",
                        "plan_id in (select id from treatment_plans where tenant_id = ? and patient_id = ?)",
                        tenantId,
                        tenantId,
                        patientId));
        result.put(
                "parcelas",
                rows(
                        "installments",
                        "receivable_id in (select id from receivables where tenant_id = ? and patient_id = ?)",
                        tenantId,
                        tenantId,
                        patientId));
        result.put(
                "pagamentos",
                rows(
                        "payments",
                        "installment_id in (select i.id from installments i join receivables r on r.id = i.receivable_id"
                                + " where r.tenant_id = ? and r.patient_id = ?)",
                        tenantId,
                        tenantId,
                        patientId));
        audit.record(AuditAction.PATIENT_DATA_EXPORTED, "patient", patientId, Map.of());
        return result;
    }

    private List<Object> rows(String table, String condition, Object... params) {
        String sql = "select coalesce(jsonb_agg(to_jsonb(t)" + HIDDEN + " order by t.id), '[]'::jsonb)::text from "
                + table + " t where t.tenant_id = ? and " + condition;
        String value = jdbc.sql(sql).params(params).query(String.class).single();
        return json.readValue(value, new TypeReference<List<Object>>() {});
    }

    private Map<String, Object> parse(String value) {
        return json.readValue(value, new TypeReference<Map<String, Object>>() {});
    }
}
