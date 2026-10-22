package br.com.odontosaas.subscription.repository;

import br.com.odontosaas.subscription.dto.request.PlanRequest;
import br.com.odontosaas.subscription.dto.response.PlanView;
import java.sql.Array;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PlanRepository {

    private final JdbcClient jdbc;

    public PlanRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    static List<String> features(Array array) throws SQLException {
        return array == null ? List.of() : Arrays.asList((String[]) array.getArray());
    }

    public List<PlanView> list() {
        return jdbc.sql("""
                        select p.id, p.code, p.name, p.price_monthly, p.max_professionals, p.max_patients, p.features,
                               p.active, (select count(*) from subscriptions s where s.plan_id = p.id
                                   and s.status <> 'CANCELLED') as tenants
                        from plans p order by p.price_monthly, p.name
                        """)
                .query((rs, n) -> new PlanView(
                        rs.getObject("id", UUID.class),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getBigDecimal("price_monthly"),
                        (Integer) rs.getObject("max_professionals"),
                        (Integer) rs.getObject("max_patients"),
                        features(rs.getArray("features")),
                        rs.getBoolean("active"),
                        rs.getLong("tenants")))
                .list();
    }

    public void insert(UUID id, PlanRequest r, String[] features) {
        jdbc.sql("""
                        insert into plans (id, code, name, price_monthly, max_professionals, max_patients, features,
                            active) values (?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(
                        id,
                        r.code(),
                        r.name().strip(),
                        r.priceMonthly(),
                        r.maxProfessionals(),
                        r.maxPatients(),
                        features,
                        r.active())
                .update();
    }

    public int update(UUID id, PlanRequest r, String[] features) {
        return jdbc.sql("""
                        update plans set code = ?, name = ?, price_monthly = ?, max_professionals = ?,
                            max_patients = ?, features = ?, active = ? where id = ?
                        """)
                .params(
                        r.code(),
                        r.name().strip(),
                        r.priceMonthly(),
                        r.maxProfessionals(),
                        r.maxPatients(),
                        features,
                        r.active(),
                        id)
                .update();
    }

    public Optional<UUID> findIdByCode(String code) {
        return jdbc.sql("select id from plans where code = ?")
                .param(code)
                .query(UUID.class)
                .optional();
    }
}
