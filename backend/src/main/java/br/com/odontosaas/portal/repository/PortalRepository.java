package br.com.odontosaas.portal.repository;

import br.com.odontosaas.portal.dto.response.PortalClinic;
import br.com.odontosaas.portal.dto.response.PortalContact;
import br.com.odontosaas.portal.dto.response.PortalPatient;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PortalRepository {

    public record ClinicRow(String name, String phone) {}

    private final JdbcClient jdbc;

    public PortalRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public ClinicRow clinic(UUID tenantId) {
        return jdbc.sql("select t.name, s.phone from tenants t left join clinic_settings s on s.tenant_id = t.id"
                        + " where t.id = ?")
                .param(tenantId)
                .query(ClinicRow.class)
                .single();
    }

    public PortalClinic clinicDetails(UUID tenantId) {
        return jdbc.sql("""
                        select t.name, s.legal_name, s.document, s.phone, s.email, s.zip_code, s.address, s.city,
                               s.state
                        from tenants t left join clinic_settings s on s.tenant_id = t.id
                        where t.id = ?
                        """).param(tenantId).query(PortalClinic.class).single();
    }

    public PortalPatient patient(UUID tenantId, UUID patientId) {
        return jdbc.sql("""
                        select full_name, social_name, cpf, birth_date, mobile, phone, guardian_name, guardian_cpf
                        from patients where tenant_id = ? and id = ?
                        """)
                .params(tenantId, patientId)
                .query(PortalPatient.class)
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
            String mobile,
            String phone,
            Boolean allowEmail,
            Boolean allowSms,
            Boolean allowWhatsapp) {
        jdbc.sql("""
                        update patients set mobile = coalesce(nullif(?, ''), mobile),
                            phone = nullif(coalesce(?, phone), ''),
                            allow_email = coalesce(?, allow_email), allow_sms = coalesce(?, allow_sms),
                            allow_whatsapp = coalesce(?, allow_whatsapp), updated_at = now(), version = version + 1
                        where tenant_id = ? and id = ?
                        """)
                .params(mobile, phone, allowEmail, allowSms, allowWhatsapp, tenantId, patientId)
                .update();
    }
}
