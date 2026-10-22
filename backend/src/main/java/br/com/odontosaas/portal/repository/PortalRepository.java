package br.com.odontosaas.portal.repository;

import br.com.odontosaas.portal.dto.response.PortalContact;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PortalRepository {

    public record AccessRow(UUID id, UUID tenantId, UUID patientId) {}

    public record ClinicRow(String name, String phone) {}

    private final JdbcClient jdbc;

    public PortalRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void revokeTokens(UUID tenantId, UUID patientId) {
        jdbc.sql("update portal_access_tokens set revoked_at = now() where tenant_id = ? and patient_id = ?"
                        + " and revoked_at is null")
                .params(tenantId, patientId)
                .update();
    }

    public void insertToken(
            UUID id, UUID tenantId, UUID patientId, String tokenHash, Timestamp expiresAt, UUID createdBy) {
        jdbc.sql("""
                        insert into portal_access_tokens (id, tenant_id, patient_id, token_hash, expires_at, created_by)
                        values (?, ?, ?, ?, ?, ?)
                        """)
                .params(id, tenantId, patientId, tokenHash, expiresAt, createdBy)
                .update();
    }

    public Optional<AccessRow> findValidAccess(String tokenHash) {
        return jdbc.sql("""
                select id, tenant_id, patient_id from portal_access_tokens
                where token_hash = ? and revoked_at is null and expires_at > now()
                """).param(tokenHash).query(AccessRow.class).optional();
    }

    public void touchLastUsed(UUID tenantId, UUID accessId) {
        jdbc.sql("update portal_access_tokens set last_used_at = now() where tenant_id = ? and id = ?")
                .params(tenantId, accessId)
                .update();
    }

    public ClinicRow clinic(UUID tenantId) {
        return jdbc.sql("select t.name, s.phone from tenants t left join clinic_settings s on s.tenant_id = t.id"
                        + " where t.id = ?")
                .param(tenantId)
                .query(ClinicRow.class)
                .single();
    }

    public PortalContact contact(UUID tenantId, UUID patientId) {
        return jdbc.sql("""
                        select full_name, email, mobile, phone, allow_email, allow_sms, allow_whatsapp
                        from patients where tenant_id = ? and id = ?
                        """)
                .params(tenantId, patientId)
                .query(PortalContact.class)
                .single();
    }

    public Optional<UUID> appointmentPatientId(UUID tenantId, UUID appointmentId) {
        return jdbc.sql("select patient_id from appointments where tenant_id = ? and id = ?")
                .params(tenantId, appointmentId)
                .query(UUID.class)
                .optional();
    }

    public void updateContact(
            UUID tenantId,
            UUID patientId,
            String email,
            String mobile,
            String phone,
            Boolean allowEmail,
            Boolean allowSms,
            Boolean allowWhatsapp) {
        jdbc.sql("""
                        update patients set email = nullif(coalesce(?, email), ''),
                            mobile = nullif(coalesce(?, mobile), ''), phone = nullif(coalesce(?, phone), ''),
                            allow_email = coalesce(?, allow_email), allow_sms = coalesce(?, allow_sms),
                            allow_whatsapp = coalesce(?, allow_whatsapp), updated_at = now(), version = version + 1
                        where tenant_id = ? and id = ?
                        """)
                .params(email, mobile, phone, allowEmail, allowSms, allowWhatsapp, tenantId, patientId)
                .update();
    }
}
