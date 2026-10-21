package br.com.odontosaas.patients.repository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Leituras da exportação de dados do paciente (LGPD). Cada consulta devolve o JSON (texto) montado pelo banco, sem os
 * campos internos de segurança; a conversão para objetos fica com o serviço.
 */
@Repository
public class PatientExportRepository {

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

    public PatientExportRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** JSON do cadastro do paciente, se existir na clínica. */
    public Optional<String> findPatientJson(UUID tenantId, UUID patientId) {
        return jdbc.sql("select (to_jsonb(p)" + HIDDEN + ")::text from patients p where tenant_id = ? and id = ?")
                .params(tenantId, patientId)
                .query(String.class)
                .optional();
    }

    public String clinicName(UUID tenantId) {
        return jdbc.sql("select name from tenants where id = ?")
                .param(tenantId)
                .query(String.class)
                .single();
    }

    /** Seções ligadas direto ao paciente (rótulo → JSON de uma lista), na ordem de exportação. */
    public Map<String, String> directSections(UUID tenantId, UUID patientId) {
        Map<String, String> result = new LinkedHashMap<>();
        SECTIONS.forEach((label, table) -> result.put(label, rows(table, "patient_id = ?", tenantId, patientId)));
        return result;
    }

    public String treatmentPlanItems(UUID tenantId, UUID patientId) {
        return rows(
                "treatment_plan_items",
                "plan_id in (select id from treatment_plans where tenant_id = ? and patient_id = ?)",
                tenantId,
                tenantId,
                patientId);
    }

    public String installments(UUID tenantId, UUID patientId) {
        return rows(
                "installments",
                "receivable_id in (select id from receivables where tenant_id = ? and patient_id = ?)",
                tenantId,
                tenantId,
                patientId);
    }

    public String payments(UUID tenantId, UUID patientId) {
        return rows(
                "payments",
                "installment_id in (select i.id from installments i join receivables r on r.id = i.receivable_id"
                        + " where r.tenant_id = ? and r.patient_id = ?)",
                tenantId,
                tenantId,
                patientId);
    }

    private String rows(String table, String condition, Object... params) {
        // O corpo das mensagens guarda links de acesso do paciente (#token=...): sai mascarado, senão a exportação
        // entregaria links ainda válidos do portal, de termo ou de consulta.
        String row = "outbound_messages".equals(table)
                ? "(to_jsonb(t) || jsonb_build_object('body', regexp_replace(t.body, '#token=[A-Za-z0-9_-]+',"
                        + " '#token=[removido]', 'g')))"
                : "to_jsonb(t)";
        String sql = "select coalesce(jsonb_agg(" + row + HIDDEN + " order by t.id), '[]'::jsonb)::text from " + table
                + " t where t.tenant_id = ? and " + condition;
        return jdbc.sql(sql).params(params).query(String.class).single();
    }
}
