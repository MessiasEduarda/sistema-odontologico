package br.com.odontosaas.clinic.repository;

import br.com.odontosaas.clinic.dto.response.RoomView;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Acesso a dados das salas (consultórios) da clínica. */
@Repository
public class RoomRepository {

    private final JdbcClient jdbc;

    public RoomRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<RoomView> list(UUID tenantId) {
        return jdbc.sql("select id, name, active from rooms where tenant_id = ? order by name")
                .param(tenantId)
                .query(RoomView.class)
                .list();
    }

    public void insert(UUID id, UUID tenantId, String name, boolean active) {
        jdbc.sql("insert into rooms (id, tenant_id, name, active) values (?, ?, ?, ?)")
                .params(id, tenantId, name, active)
                .update();
    }

    /** Atualiza o nome e, se informado, o estado ativo; devolve as linhas afetadas. */
    public int update(UUID tenantId, UUID id, String name, Boolean active) {
        return jdbc.sql("update rooms set name = ?, active = coalesce(cast(? as boolean), active)"
                        + " where tenant_id = ? and id = ?")
                .params(name, active, tenantId, id)
                .update();
    }

    public RoomView get(UUID tenantId, UUID id) {
        return jdbc.sql("select id, name, active from rooms where tenant_id = ? and id = ?")
                .params(tenantId, id)
                .query(RoomView.class)
                .single();
    }
}
