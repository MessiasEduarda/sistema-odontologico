package br.com.odontosaas.aesthetics.service;

import br.com.odontosaas.aesthetics.dto.request.AestheticRequest;
import br.com.odontosaas.aesthetics.dto.request.Region;
import br.com.odontosaas.aesthetics.dto.response.AestheticView;
import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.clinic.ProfessionalAuthorship;
import br.com.odontosaas.shared.ApiException;
import br.com.odontosaas.shared.Ids;
import br.com.odontosaas.shared.TenantRefs;
import br.com.odontosaas.tenancy.TenantContext;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
public class AestheticService {

    private static final TypeReference<List<Region>> REGIONS = new TypeReference<>() {};

    private static final String SELECT = """
            select a.id, a.patient_id, pa.full_name as patient_name, a.professional_id, p.name as professional_name,
                   a.performed_at, a.procedure_type, a.product_name, a.manufacturer, a.anvisa_registry, a.batch,
                   a.expiry_date, a.unit, a.regions::text as regions, a.total_quantity, a.technique, a.notes,
                   a.return_date, a.consent_id, c.status as consent_status, u.name as created_by_name, a.voided_at,
                   a.void_reason
            from aesthetic_procedures a
            join patients pa on pa.tenant_id = a.tenant_id and pa.id = a.patient_id
            join professionals p on p.tenant_id = a.tenant_id and p.id = a.professional_id
            left join patient_consents c on c.tenant_id = a.tenant_id and c.id = a.consent_id
            left join users u on u.id = a.created_by
            """;

    private final JdbcClient jdbc;
    private final TenantRefs refs;
    private final AuditService audit;
    private final JsonMapper json;
    private final ProfessionalAuthorship authorship;

    public AestheticService(
            JdbcClient jdbc, TenantRefs refs, AuditService audit, JsonMapper json, ProfessionalAuthorship authorship) {
        this.jdbc = jdbc;
        this.refs = refs;
        this.audit = audit;
        this.json = json;
        this.authorship = authorship;
    }

    private AestheticView map(ResultSet rs) throws SQLException {
        Date expiry = rs.getDate("expiry_date");
        Date ret = rs.getDate("return_date");
        Timestamp voided = rs.getTimestamp("voided_at");
        return new AestheticView(
                rs.getObject("id", UUID.class),
                rs.getObject("patient_id", UUID.class),
                rs.getString("patient_name"),
                rs.getObject("professional_id", UUID.class),
                rs.getString("professional_name"),
                rs.getTimestamp("performed_at").toInstant(),
                rs.getString("procedure_type"),
                rs.getString("product_name"),
                rs.getString("manufacturer"),
                rs.getString("anvisa_registry"),
                rs.getString("batch"),
                expiry == null ? null : expiry.toLocalDate(),
                rs.getString("unit"),
                json.readValue(rs.getString("regions"), REGIONS),
                rs.getBigDecimal("total_quantity"),
                rs.getString("technique"),
                rs.getString("notes"),
                ret == null ? null : ret.toLocalDate(),
                rs.getObject("consent_id", UUID.class),
                rs.getString("consent_status"),
                rs.getString("created_by_name"),
                voided == null ? null : voided.toInstant(),
                rs.getString("void_reason"));
    }

    @Transactional(readOnly = true)
    public List<AestheticView> byPatient(UUID patientId) {
        UUID tenantId = TenantContext.requireTenantId();
        refs.require("patients", tenantId, patientId, "Paciente");
        return jdbc.sql(SELECT + " where a.tenant_id = ? and a.patient_id = ? order by a.performed_at desc")
                .params(tenantId, patientId)
                .query((rs, n) -> map(rs))
                .list();
    }

    @Transactional(readOnly = true)
    public List<AestheticView> search(String batch, String query, LocalDate returnsUntil) {
        String b = batch == null || batch.isBlank() ? null : batch.strip().toLowerCase();
        String q = query == null || query.isBlank() ? null : "%" + query.strip().toLowerCase() + "%";
        return jdbc.sql(SELECT + """
                         where a.tenant_id = :tenant
                          and (cast(:batch as varchar) is null or lower(a.batch) = :batch)
                          and (cast(:q as varchar) is null or lower(pa.full_name) like :q
                               or lower(a.product_name) like :q)
                          and (cast(:returns as date) is null
                               or (a.voided_at is null and a.return_date is not null and a.return_date <= :returns))
                        order by case when cast(:returns as date) is null then a.performed_at end desc,
                                 a.return_date asc
                        limit 300
                        """)
                .param("tenant", TenantContext.requireTenantId())
                .param("batch", b)
                .param("q", q)
                .param("returns", returnsUntil == null ? null : Date.valueOf(returnsUntil))
                .query((rs, n) -> map(rs))
                .list();
    }

    @Transactional
    public AestheticView create(UUID patientId, AestheticRequest r) {
        UUID tenantId = TenantContext.requireTenantId();
        boolean enabled = jdbc.sql(
                        "select coalesce((select aesthetics_enabled from clinic_settings where tenant_id = ?),"
                                + " false)")
                .param(tenantId)
                .query(Boolean.class)
                .single();
        if (!enabled) {
            throw ApiException.businessRule(
                    "AESTHETICS_DISABLED",
                    "Ative a harmonização orofacial em Configurações para registrar aplicações.");
        }
        refs.require("patients", tenantId, patientId, "Paciente");
        refs.require("professionals", tenantId, r.professionalId(), "Profissional");
        authorship.require(tenantId, r.professionalId());
        if (r.consentId() != null) {
            boolean ok = jdbc.sql("""
                            select exists(select 1 from patient_consents
                                where tenant_id = ? and id = ? and patient_id = ?)
                            """)
                    .params(tenantId, r.consentId(), patientId)
                    .query(Boolean.class)
                    .single();
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
        jdbc.sql("""
                        insert into aesthetic_procedures (id, tenant_id, patient_id, professional_id, performed_at,
                            procedure_type, product_name, manufacturer, anvisa_registry, batch, expiry_date, unit,
                            regions, total_quantity, technique, notes, return_date, consent_id, created_by)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?, ?, ?, ?)
                        """)
                .params(
                        id,
                        tenantId,
                        patientId,
                        r.professionalId(),
                        Timestamp.from(performedAt),
                        r.procedureType(),
                        r.productName().strip(),
                        blank(r.manufacturer()),
                        blank(r.anvisaRegistry()),
                        r.batch().strip(),
                        r.expiryDate() == null ? null : Date.valueOf(r.expiryDate()),
                        r.unit(),
                        json.writeValueAsString(r.regions()),
                        total,
                        blank(r.technique()),
                        blank(r.notes()),
                        r.returnDate() == null ? null : Date.valueOf(r.returnDate()),
                        r.consentId(),
                        TenantContext.userId().orElse(null))
                .update();
        audit.record(
                AuditAction.RECORD_CREATED,
                "aesthetic_procedure",
                id,
                Map.of("batch", r.batch().strip()));
        return get(tenantId, id);
    }

    @Transactional
    public void voidRecord(UUID patientId, UUID id, String reason) {
        UUID tenantId = TenantContext.requireTenantId();
        int updated = jdbc.sql("""
                        update aesthetic_procedures set voided_at = now(), voided_by = ?, void_reason = ?
                        where tenant_id = ? and patient_id = ? and id = ? and voided_at is null
                        """)
                .params(TenantContext.userId().orElse(null), reason.strip(), tenantId, patientId, id)
                .update();
        if (updated == 0) throw ApiException.notFound("Registro de aplicação");
        audit.record(AuditAction.RECORD_VOIDED, "aesthetic_procedure", id, Map.of());
    }

    private AestheticView get(UUID tenantId, UUID id) {
        return jdbc.sql(SELECT + " where a.tenant_id = ? and a.id = ?")
                .params(tenantId, id)
                .query((rs, n) -> map(rs))
                .single();
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
