package br.com.odontosaas.patients.repository;

import br.com.odontosaas.patients.dto.response.PatientSummary;
import br.com.odontosaas.patients.dto.response.PatientView;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Acesso a dados dos pacientes da clínica (busca paginada, leitura, gravação e verificações de existência). */
@Repository
public class PatientRepository {

    private static final String COLUMNS = """
            id, full_name, social_name, birth_date, cpf, rg, sex, phone, mobile, email, zip_code, street, number,
            complement, district, city, state, occupation, referral_source, health_insurance, insurance_number,
            guardian_name, guardian_cpf, guardian_phone, notes, allow_email, allow_sms, allow_whatsapp, active,
            created_at, updated_at, version
            """;

    /** Página de resultados da busca: itens da página e total de registros que atendem aos filtros. */
    public record SearchResult(List<PatientSummary> items, long total) {}

    private final JdbcClient jdbc;

    public PatientRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Busca por nome (parcial), CPF, telefone ou e-mail, com filtros opcionais e paginação já normalizada. */
    public SearchResult search(UUID tenantId, String query, Boolean active, int pageSize, int pageIndex) {
        StringBuilder where = new StringBuilder(" where p.tenant_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(tenantId);
        if (active != null) {
            where.append(" and p.active = ?");
            params.add(active);
        }
        if (query != null && !query.isBlank()) {
            String term = query.strip().toLowerCase();
            String digits = term.replaceAll("\\D", "");
            where.append(" and (lower(p.full_name) like ? or lower(coalesce(p.social_name, '')) like ?"
                    + " or lower(coalesce(p.email, '')) like ?");
            params.add("%" + term + "%");
            params.add("%" + term + "%");
            params.add("%" + term + "%");
            if (digits.length() >= 3) {
                where.append(" or p.cpf like ? or regexp_replace(coalesce(p.mobile, '') || ' '"
                        + " || coalesce(p.phone, ''), '\\D', '', 'g') like ?");
                params.add(digits + "%");
                params.add("%" + digits + "%");
            }
            where.append(")");
        }

        long total = jdbc.sql("select count(*) from patients p" + where)
                .params(params)
                .query(Long.class)
                .single();

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageSize);
        pageParams.add((long) pageIndex * pageSize);
        List<PatientSummary> items = jdbc.sql("""
                        select p.id, p.full_name, p.social_name, p.birth_date, p.cpf, p.mobile, p.phone, p.email,
                               p.active,
                               (select min(a.starts_at) from appointments a where a.tenant_id = p.tenant_id
                                  and a.patient_id = p.id and a.starts_at >= now()
                                  and a.status not in ('CANCELLED', 'NO_SHOW')) as next_appointment,
                               (select max(a.starts_at) from appointments a where a.tenant_id = p.tenant_id
                                  and a.patient_id = p.id and a.starts_at < now()
                                  and a.status = 'COMPLETED') as last_appointment
                        from patients p""" + where + " order by lower(p.full_name) limit ? offset ?")
                .params(pageParams)
                .query(PatientSummary.class)
                .list();
        return new SearchResult(items, total);
    }

    public Optional<PatientView> find(UUID tenantId, UUID id) {
        return jdbc.sql("select " + COLUMNS + " from patients where tenant_id = ? and id = ?")
                .params(tenantId, id)
                .query(PatientView.class)
                .optional();
    }

    /**
     * Insere o paciente. {@code values} segue a ordem das colunas do insert: id, tenant_id, 24 campos cadastrais
     * (nome até observações) e as três permissões de contato (e-mail, SMS, WhatsApp).
     */
    public void insert(List<Object> values) {
        jdbc.sql("""
                        insert into patients (id, tenant_id, full_name, social_name, birth_date, cpf, rg, sex, phone,
                            mobile, email, zip_code, street, number, complement, district, city, state, occupation,
                            referral_source, health_insurance, insurance_number, guardian_name, guardian_cpf,
                            guardian_phone, notes, allow_email, allow_sms, allow_whatsapp)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                            coalesce(cast(? as boolean), false), coalesce(cast(? as boolean), false),
                            coalesce(cast(? as boolean), false))
                        """).params(values).update();
    }

    /**
     * Atualiza com controle otimista de versão. {@code params} segue a ordem do update: 27 campos cadastrais, tenant_id,
     * id e a versão (duas vezes). Devolve as linhas afetadas.
     */
    public int update(List<Object> params) {
        return jdbc.sql("""
                        update patients set full_name = ?, social_name = ?, birth_date = ?, cpf = ?, rg = ?, sex = ?,
                            phone = ?, mobile = ?, email = ?, zip_code = ?, street = ?, number = ?, complement = ?,
                            district = ?, city = ?, state = ?, occupation = ?, referral_source = ?,
                            health_insurance = ?, insurance_number = ?, guardian_name = ?, guardian_cpf = ?,
                            guardian_phone = ?, notes = ?, allow_email = coalesce(cast(? as boolean), allow_email),
                            allow_sms = coalesce(cast(? as boolean), allow_sms),
                            allow_whatsapp = coalesce(cast(? as boolean), allow_whatsapp),
                            updated_at = now(), version = version + 1
                        where tenant_id = ? and id = ? and (cast(? as bigint) is null or version = ?)
                        """).params(params).update();
    }

    /** Ativa ou inativa o paciente; devolve as linhas afetadas. */
    public int setActive(UUID tenantId, UUID id, boolean active) {
        return jdbc.sql("update patients set active = ?, updated_at = now(), version = version + 1"
                        + " where tenant_id = ? and id = ?")
                .params(active, tenantId, id)
                .update();
    }

    public boolean isInactive(UUID tenantId, UUID id) {
        return jdbc.sql("select exists(select 1 from patients where tenant_id = ? and id = ? and not active)")
                .params(tenantId, id)
                .query(Boolean.class)
                .single();
    }

    public boolean exists(UUID tenantId, UUID id) {
        return jdbc.sql("select exists(select 1 from patients where tenant_id = ? and id = ?)")
                .params(tenantId, id)
                .query(Boolean.class)
                .single();
    }

    public boolean cpfExists(UUID tenantId, String cpf) {
        return jdbc.sql("select exists(select 1 from patients where tenant_id = ? and cpf = ?)")
                .params(tenantId, cpf)
                .query(Boolean.class)
                .single();
    }

    /** Já existe outro paciente (diferente de {@code exceptId}, se informado) com esse CPF? */
    public boolean cpfTakenByAnother(UUID tenantId, String cpf, UUID exceptId) {
        return jdbc.sql("select exists(select 1 from patients where tenant_id = ? and cpf = ?"
                        + " and (cast(? as uuid) is null or id <> ?))")
                .params(tenantId, cpf, exceptId, exceptId)
                .query(Boolean.class)
                .single();
    }
}
