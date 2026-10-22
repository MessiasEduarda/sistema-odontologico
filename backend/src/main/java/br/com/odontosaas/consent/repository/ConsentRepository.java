package br.com.odontosaas.consent.repository;

import br.com.odontosaas.consent.dto.response.ConsentListItem;
import br.com.odontosaas.consent.dto.response.ConsentView;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ConsentRepository {

    private static final String SELECT = """
            select c.id, c.title, c.category, c.body, c.body_sha256, c.template_version, c.procedure_description,
                   p.name as professional_name, c.status, c.signed_at, c.signer_name, c.signer_document,
                   c.signature_image, c.signed_ip, c.revoked_at, c.revoke_reason, c.created_at
            from patient_consents c
            left join professionals p on p.tenant_id = c.tenant_id and p.id = c.professional_id
            """;

    public record IssueContextRow(String patient, String cpf, String clinic, String professional, String cro) {}

    public record ConsentTokenRow(UUID tenantId, UUID patientId, UUID consentId) {}

    public record PublicNamesRow(String clinic, String patient) {}

    private final JdbcClient jdbc;

    public ConsentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<ConsentView> findByPatient(UUID tenantId, UUID patientId) {
        return jdbc.sql(SELECT + " where c.tenant_id = ? and c.patient_id = ? order by c.created_at desc")
                .params(tenantId, patientId)
                .query(ConsentView.class)
                .list();
    }

    public Optional<ConsentView> find(UUID tenantId, UUID patientId, UUID consentId) {
        return jdbc.sql(SELECT + " where c.tenant_id = ? and c.patient_id = ? and c.id = ?")
                .params(tenantId, patientId, consentId)
                .query(ConsentView.class)
                .optional();
    }

    public List<ConsentListItem> findAll(UUID tenantId, String status, String category, String query) {
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
                .param("tenant", tenantId)
                .param("status", status)
                .param("category", category)
                .param("q", query)
                .query(ConsentListItem.class)
                .list();
    }

    public IssueContextRow findIssueContext(UUID tenantId, UUID patientId, UUID professionalId) {
        return jdbc.sql("""
                        select pa.full_name as patient, pa.cpf, t.name as clinic, pr.name as professional,
                               case when pr.cro is not null then 'CRO-' || coalesce(pr.cro_state, '') || ' ' || pr.cro
                               end as cro
                        from patients pa join tenants t on t.id = pa.tenant_id
                        left join professionals pr on pr.tenant_id = pa.tenant_id and pr.id = ?
                        where pa.tenant_id = ? and pa.id = ?
                        """)
                .params(professionalId, tenantId, patientId)
                .query(IssueContextRow.class)
                .single();
    }

    public void insert(
            UUID id,
            UUID tenantId,
            UUID patientId,
            UUID templateId,
            int templateVersion,
            String title,
            String category,
            String body,
            String bodySha256,
            String procedureDescription,
            UUID professionalId,
            UUID createdBy) {
        jdbc.sql("""
                        insert into patient_consents (id, tenant_id, patient_id, template_id, template_version, title,
                            category, body, body_sha256, procedure_description, professional_id, created_by)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(
                        id,
                        tenantId,
                        patientId,
                        templateId,
                        templateVersion,
                        title,
                        category,
                        body,
                        bodySha256,
                        procedureDescription,
                        professionalId,
                        createdBy)
                .update();
    }

    public void saveSignToken(UUID tenantId, UUID consentId, String tokenHash, Instant expiresAt) {
        jdbc.sql("""
                        update patient_consents set sign_token_hash = ?, sign_token_expires_at = ?,
                            version = version + 1 where tenant_id = ? and id = ?
                        """)
                .params(tokenHash, java.sql.Timestamp.from(expiresAt), tenantId, consentId)
                .update();
    }

    public void revoke(UUID tenantId, UUID consentId, String reason) {
        jdbc.sql("""
                        update patient_consents set status = 'REVOKED', revoked_at = now(), revoke_reason = ?,
                            sign_token_hash = null, version = version + 1 where tenant_id = ? and id = ?
                        """).params(reason, tenantId, consentId).update();
    }

    public Optional<ConsentTokenRow> findByTokenHash(String tokenHash) {
        return jdbc.sql("""
                        select tenant_id, patient_id, id as consent_id from patient_consents
                        where sign_token_hash = ? and sign_token_expires_at > now()
                        """).param(tokenHash).query(ConsentTokenRow.class).optional();
    }

    public PublicNamesRow findPublicNames(UUID tenantId, UUID patientId) {
        return jdbc.sql("select t.name as clinic, p.full_name as patient from tenants t join patients p"
                        + " on p.tenant_id = t.id where t.id = ? and p.id = ?")
                .params(tenantId, patientId)
                .query(PublicNamesRow.class)
                .single();
    }

    public void markSigned(
            UUID tenantId,
            UUID consentId,
            String signerName,
            String signerDocument,
            String signatureImage,
            String signedIp,
            String signedUserAgent) {
        jdbc.sql("""
                        update patient_consents set status = 'SIGNED', signed_at = now(), signer_name = ?,
                            signer_document = ?, signature_image = ?, signed_ip = ?, signed_user_agent = ?,
                            version = version + 1
                        where tenant_id = ? and id = ? and status = 'PENDING'
                        """)
                .params(signerName, signerDocument, signatureImage, signedIp, signedUserAgent, tenantId, consentId)
                .update();
    }
}
