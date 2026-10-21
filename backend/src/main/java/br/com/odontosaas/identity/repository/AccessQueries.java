package br.com.odontosaas.identity.repository;

import br.com.odontosaas.identity.dto.response.ClinicSummary;
import br.com.odontosaas.identity.security.Permission;
import br.com.odontosaas.identity.support.RoleTemplates;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Consultas de acesso em SQL direto (tabelas de associação). Todas recebem o {@code tenantId} explicitamente e,
 * além disso, rodam sob RLS.
 */
@Component
public class AccessQueries {

    private final JdbcClient jdbc;

    public AccessQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Permissões do usuário na clínica, ou vazio se ele não tiver vínculo ativo com uma clínica ativa. Exige contexto
     * com {@code tenantId} = clínica consultada.
     */
    public Optional<Set<Permission>> permissionsIn(UUID tenantId, UUID userId) {
        Optional<UUID> membership =
                jdbc.sql("""
                        select m.id from memberships m join tenants t on t.id = m.tenant_id
                        where m.tenant_id = ? and m.user_id = ? and m.status = 'ACTIVE' and t.status = 'ACTIVE'
                        """).params(tenantId, userId).query(UUID.class).optional();
        if (membership.isEmpty()) return Optional.empty();

        List<String> codes = jdbc.sql("""
                        select distinct rp.permission
                        from membership_roles mr join role_permissions rp on rp.role_id = mr.role_id
                        where mr.tenant_id = ? and mr.membership_id = ?
                        """)
                .params(tenantId, membership.get())
                .query(String.class)
                .list();
        Set<Permission> permissions = EnumSet.noneOf(Permission.class);
        codes.forEach(code -> Permission.fromCode(code).ifPresent(permissions::add));
        return Optional.of(permissions);
    }

    /** Clínicas ativas em que o usuário tem vínculo ativo. Exige contexto com {@code userId}. */
    public List<ClinicSummary> activeClinicsOf(UUID userId) {
        return jdbc.sql("""
                        select t.id, t.name, t.slug from memberships m join tenants t on t.id = m.tenant_id
                        where m.user_id = ? and m.status = 'ACTIVE' and t.status = 'ACTIVE'
                        order by t.name
                        """).param(userId).query(ClinicSummary.class).list();
    }

    public Map<UUID, Set<String>> roleCodesByMembership(UUID tenantId) {
        Map<UUID, Set<String>> result = new HashMap<>();
        jdbc.sql("""
                        select mr.membership_id, r.code from membership_roles mr join roles r on r.id = mr.role_id
                        where mr.tenant_id = ?
                        """)
                .param(tenantId)
                .query((rs, n) -> {
                    result.computeIfAbsent(rs.getObject(1, UUID.class), k -> new TreeSet<>())
                            .add(rs.getString(2));
                    return null;
                })
                .list();
        return result;
    }

    public Map<UUID, Set<String>> permissionsByRole(UUID tenantId) {
        Map<UUID, Set<String>> result = new HashMap<>();
        jdbc.sql("select role_id, permission from role_permissions where tenant_id = ?")
                .param(tenantId)
                .query((rs, n) -> {
                    result.computeIfAbsent(rs.getObject(1, UUID.class), k -> new TreeSet<>())
                            .add(rs.getString(2));
                    return null;
                })
                .list();
        return result;
    }

    public void replaceMembershipRoles(UUID tenantId, UUID membershipId, Collection<UUID> roleIds) {
        jdbc.sql("delete from membership_roles where tenant_id = ? and membership_id = ?")
                .params(tenantId, membershipId)
                .update();
        for (UUID roleId : roleIds) {
            addMembershipRole(tenantId, membershipId, roleId);
        }
    }

    public void addMembershipRole(UUID tenantId, UUID membershipId, UUID roleId) {
        jdbc.sql("""
                        insert into membership_roles (tenant_id, membership_id, role_id) values (?, ?, ?)
                        on conflict do nothing
                        """).params(tenantId, membershipId, roleId).update();
    }

    public void replaceRolePermissions(UUID tenantId, UUID roleId, Collection<Permission> permissions) {
        jdbc.sql("delete from role_permissions where tenant_id = ? and role_id = ?")
                .params(tenantId, roleId)
                .update();
        for (Permission permission : permissions) {
            jdbc.sql("insert into role_permissions (tenant_id, role_id, permission) values (?, ?, ?)")
                    .params(tenantId, roleId, permission.code())
                    .update();
        }
    }

    /** Vínculos ativos que possuem o papel de administrador da clínica. */
    public long countActiveOwners(UUID tenantId) {
        return jdbc.sql("""
                        select count(distinct m.id) from memberships m
                        join membership_roles mr on mr.membership_id = m.id
                        join roles r on r.id = mr.role_id
                        where m.tenant_id = ? and m.status = 'ACTIVE' and r.code = ?
                        """)
                .params(tenantId, RoleTemplates.OWNER)
                .query(Long.class)
                .single();
    }

    public boolean hasRole(UUID tenantId, UUID membershipId, String roleCode) {
        return jdbc.sql("""
                        select exists(select 1 from membership_roles mr join roles r on r.id = mr.role_id
                                      where mr.tenant_id = ? and mr.membership_id = ? and r.code = ?)
                        """)
                .params(tenantId, membershipId, roleCode)
                .query(Boolean.class)
                .single();
    }
}
