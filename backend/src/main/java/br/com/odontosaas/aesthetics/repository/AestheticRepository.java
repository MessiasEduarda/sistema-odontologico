package br.com.odontosaas.aesthetics.repository;

import br.com.odontosaas.aesthetics.dto.request.Region;
import br.com.odontosaas.aesthetics.dto.response.AestheticView;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class AestheticRepository {

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

    public record NewAesthetic(
            UUID id,
            UUID tenantId,
            UUID patientId,
            UUID professionalId,
            Instant performedAt,
            String procedureType,
            String productName,
            String manufacturer,
            String anvisaRegistry,
            String batch,
            LocalDate expiryDate,
            String unit,
            List<Region> regions,
            BigDecimal totalQuantity,
            String technique,
            String notes,
            LocalDate returnDate,
            UUID consentId,
            UUID createdBy) {}

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public AestheticRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
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

    public List<AestheticView> findByPatient(UUID tenantId, UUID patientId) {
        return jdbc.sql(SELECT + " where a.tenant_id = ? and a.patient_id = ? order by a.performed_at desc")
                .params(tenantId, patientId)
                .query((rs, n) -> map(rs))
                .list();
    }

    public List<AestheticView> search(UUID tenantId, String batch, String query, LocalDate returnsUntil) {
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
                .param("tenant", tenantId)
                .param("batch", batch)
                .param("q", query)
                .param("returns", returnsUntil == null ? null : Date.valueOf(returnsUntil))
                .query((rs, n) -> map(rs))
                .list();
    }

    public boolean isAestheticsEnabled(UUID tenantId) {
        return jdbc.sql("select coalesce((select aesthetics_enabled from clinic_settings where tenant_id = ?),"
                        + " false)")
                .param(tenantId)
                .query(Boolean.class)
                .single();
    }

    public boolean consentBelongsToPatient(UUID tenantId, UUID consentId, UUID patientId) {
        return jdbc.sql("""
                        select exists(select 1 from patient_consents
                            where tenant_id = ? and id = ? and patient_id = ?)
                        """)
                .params(tenantId, consentId, patientId)
                .query(Boolean.class)
                .single();
    }

    public void insert(NewAesthetic n) {
        jdbc.sql("""
                        insert into aesthetic_procedures (id, tenant_id, patient_id, professional_id, performed_at,
                            procedure_type, product_name, manufacturer, anvisa_registry, batch, expiry_date, unit,
                            regions, total_quantity, technique, notes, return_date, consent_id, created_by)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?, ?, ?, ?)
                        """)
                .params(
                        n.id(),
                        n.tenantId(),
                        n.patientId(),
                        n.professionalId(),
                        Timestamp.from(n.performedAt()),
                        n.procedureType(),
                        n.productName(),
                        n.manufacturer(),
                        n.anvisaRegistry(),
                        n.batch(),
                        n.expiryDate() == null ? null : Date.valueOf(n.expiryDate()),
                        n.unit(),
                        json.writeValueAsString(n.regions()),
                        n.totalQuantity(),
                        n.technique(),
                        n.notes(),
                        n.returnDate() == null ? null : Date.valueOf(n.returnDate()),
                        n.consentId(),
                        n.createdBy())
                .update();
    }

    public int voidRecord(UUID tenantId, UUID patientId, UUID id, UUID voidedBy, String reason) {
        return jdbc.sql("""
                        update aesthetic_procedures set voided_at = now(), voided_by = ?, void_reason = ?
                        where tenant_id = ? and patient_id = ? and id = ? and voided_at is null
                        """).params(voidedBy, reason, tenantId, patientId, id).update();
    }

    public AestheticView get(UUID tenantId, UUID id) {
        return jdbc.sql(SELECT + " where a.tenant_id = ? and a.id = ?")
                .params(tenantId, id)
                .query((rs, n) -> map(rs))
                .single();
    }
}
