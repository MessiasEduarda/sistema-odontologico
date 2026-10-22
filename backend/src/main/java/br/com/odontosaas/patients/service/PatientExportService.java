package br.com.odontosaas.patients.service;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.patients.repository.PatientExportRepository;
import br.com.odontosaas.shared.ApiException;
import br.com.odontosaas.tenancy.TenantContext;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
public class PatientExportService {

    private final PatientExportRepository exports;
    private final AuditService audit;
    private final JsonMapper json;

    public PatientExportService(PatientExportRepository exports, AuditService audit, JsonMapper json) {
        this.exports = exports;
        this.audit = audit;
        this.json = json;
    }

    @Transactional
    public Map<String, Object> export(UUID patientId) {
        UUID tenantId = TenantContext.requireTenantId();
        String patient =
                exports.findPatientJson(tenantId, patientId).orElseThrow(() -> ApiException.notFound("Paciente"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("formato", "Odonto SaaS — exportação de dados do paciente (LGPD, art. 18, V)");
        result.put("geradoEm", Instant.now().toString());
        result.put("clinica", exports.clinicName(tenantId));
        result.put("paciente", parse(patient));
        exports.directSections(tenantId, patientId).forEach((label, rows) -> result.put(label, list(rows)));
        result.put("itens_dos_orcamentos", list(exports.treatmentPlanItems(tenantId, patientId)));
        result.put("parcelas", list(exports.installments(tenantId, patientId)));
        result.put("pagamentos", list(exports.payments(tenantId, patientId)));
        audit.record(AuditAction.PATIENT_DATA_EXPORTED, "patient", patientId, Map.of());
        return result;
    }

    private List<Object> list(String value) {
        return json.readValue(value, new TypeReference<List<Object>>() {});
    }

    private Map<String, Object> parse(String value) {
        return json.readValue(value, new TypeReference<Map<String, Object>>() {});
    }
}
