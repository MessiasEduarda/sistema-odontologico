package br.com.odontosaas.clinic.repository;

import br.com.odontosaas.clinic.dto.request.ProcedureRequest;
import br.com.odontosaas.clinic.dto.response.ProcedureView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Acesso a dados do catálogo de procedimentos da clínica. */
@Repository
public class ProcedureRepository {

    private static final String PROCEDURE_COLUMNS =
            "id, code, name, category, default_price, duration_minutes, active, updated_at";

    private final JdbcClient jdbc;

    public ProcedureRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** {@code like} já vem no formato {@code %texto%} em minúsculas (ou nulo, sem filtro de texto). */
    public List<ProcedureView> list(UUID tenantId, String like, Boolean active) {
        return jdbc.sql("select " + PROCEDURE_COLUMNS + " from procedures where tenant_id = ?"
                        + " and (cast(? as text) is null or lower(name) like ? or lower(coalesce(code, '')) like ?)"
                        + " and (cast(? as boolean) is null or active = ?) order by name")
                .params(tenantId, like, like, like, active, active)
                .query(ProcedureView.class)
                .list();
    }

    public Optional<ProcedureView> find(UUID tenantId, UUID id) {
        return jdbc.sql("select " + PROCEDURE_COLUMNS + " from procedures where tenant_id = ? and id = ?")
                .params(tenantId, id)
                .query(ProcedureView.class)
                .optional();
    }

    public void insert(
            UUID id, UUID tenantId, String code, String name, String category, ProcedureRequest r, boolean active) {
        jdbc.sql("""
                        insert into procedures (id, tenant_id, code, name, category, default_price, duration_minutes,
                            active) values (?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(id, tenantId, code, name, category, r.defaultPrice(), r.durationMinutes(), active)
                .update();
    }

    /** Atualiza o procedimento; devolve as linhas afetadas. */
    public int update(UUID tenantId, UUID id, String code, String name, String category, ProcedureRequest r) {
        return jdbc.sql("""
                        update procedures set code = ?, name = ?, category = ?, default_price = ?,
                            duration_minutes = ?, active = coalesce(cast(? as boolean), active),
                            updated_at = now()
                        where tenant_id = ? and id = ?
                        """)
                .params(code, name, category, r.defaultPrice(), r.durationMinutes(), r.active(), tenantId, id)
                .update();
    }
}
