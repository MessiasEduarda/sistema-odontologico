package br.com.odontosaas.identity.service;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.identity.dto.response.MemberView;
import br.com.odontosaas.identity.dto.response.RoleView;
import br.com.odontosaas.identity.entity.Membership;
import br.com.odontosaas.identity.entity.Role;
import br.com.odontosaas.identity.entity.User;
import br.com.odontosaas.identity.repository.AccessQueries;
import br.com.odontosaas.identity.repository.MembershipRepository;
import br.com.odontosaas.identity.repository.RoleRepository;
import br.com.odontosaas.identity.repository.SessionStore;
import br.com.odontosaas.identity.repository.UserRepository;
import br.com.odontosaas.identity.security.AuthenticatedUser;
import br.com.odontosaas.identity.support.RoleTemplates;
import br.com.odontosaas.shared.ApiException;
import br.com.odontosaas.tenancy.TenantContext;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {

    private final MembershipRepository memberships;
    private final RoleRepository roles;
    private final UserRepository users;
    private final AccessQueries access;
    private final SessionStore sessions;
    private final AuditService audit;

    public MemberService(
            MembershipRepository memberships,
            RoleRepository roles,
            UserRepository users,
            AccessQueries access,
            SessionStore sessions,
            AuditService audit) {
        this.memberships = memberships;
        this.roles = roles;
        this.users = users;
        this.access = access;
        this.sessions = sessions;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<MemberView> list() {
        UUID tenantId = TenantContext.requireTenantId();
        List<Membership> list = memberships.findByTenantIdOrderByCreatedAt(tenantId);
        Map<UUID, User> usersById = users
                .findAllById(list.stream().map(Membership::getUserId).toList())
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<UUID, Set<String>> rolesByMembership = access.roleCodesByMembership(tenantId);
        return list.stream()
                .map(m -> {
                    User u = usersById.get(m.getUserId());
                    return new MemberView(
                            m.getId(),
                            m.getUserId(),
                            u == null ? "" : u.getName(),
                            u == null ? "" : u.getEmail(),
                            m.getStatus().name(),
                            rolesByMembership.getOrDefault(m.getId(), Set.of()));
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RoleView> roles() {
        UUID tenantId = TenantContext.requireTenantId();
        Map<UUID, Set<String>> permissions = access.permissionsByRole(tenantId);
        return roles.findByTenantIdOrderByName(tenantId).stream()
                .map(r -> new RoleView(
                        r.getId(),
                        r.getCode(),
                        r.getName(),
                        r.isSystemRole(),
                        permissions.getOrDefault(r.getId(), Set.of())))
                .toList();
    }

    @Transactional
    public MemberView updateRoles(UUID membershipId, List<String> roleCodes) {
        UUID tenantId = TenantContext.requireTenantId();
        Membership membership = memberships
                .findByIdAndTenantId(membershipId, tenantId)
                .orElseThrow(() -> ApiException.notFound("Membro"));
        Set<String> wanted = new HashSet<>(roleCodes);
        List<Role> found = roles.findByTenantIdAndCodeIn(tenantId, wanted);
        if (found.size() != wanted.size()) {
            throw ApiException.invalidField("roleCodes", "Um ou mais papéis não existem.");
        }
        boolean losesOwner =
                access.hasRole(tenantId, membershipId, RoleTemplates.OWNER) && !wanted.contains(RoleTemplates.OWNER);
        if (losesOwner && membership.isActive() && access.countActiveOwners(tenantId) <= 1) {
            throw ApiException.businessRule("LAST_OWNER", "A clínica precisa ter pelo menos um administrador.");
        }
        access.replaceMembershipRoles(
                tenantId, membershipId, found.stream().map(Role::getId).toList());
        audit.record(
                AuditAction.MEMBER_ROLES_CHANGED,
                "membership",
                membershipId,
                Map.of("roles", new TreeSet<>(wanted).toString()));
        User user = users.findById(membership.getUserId()).orElseThrow();
        return new MemberView(
                membershipId,
                user.getId(),
                user.getName(),
                user.getEmail(),
                membership.getStatus().name(),
                new TreeSet<>(wanted));
    }

    @Transactional
    public void deactivate(UUID membershipId, AuthenticatedUser actor) {
        UUID tenantId = TenantContext.requireTenantId();
        Membership membership = memberships
                .findByIdAndTenantId(membershipId, tenantId)
                .orElseThrow(() -> ApiException.notFound("Membro"));
        if (membership.getUserId().equals(actor.userId())) {
            throw ApiException.businessRule("CANNOT_DEACTIVATE_SELF", "Você não pode desativar o próprio acesso.");
        }
        if (!membership.isActive()) return;
        if (access.hasRole(tenantId, membershipId, RoleTemplates.OWNER) && access.countActiveOwners(tenantId) <= 1) {
            throw ApiException.businessRule("LAST_OWNER", "A clínica precisa ter pelo menos um administrador.");
        }
        membership.deactivate();
        memberships.saveAndFlush(membership);
        sessions.revokeForUserInTenant(membership.getUserId(), tenantId, "MEMBERSHIP_DISABLED");
        audit.record(AuditAction.MEMBER_DEACTIVATED, "membership", membershipId, Map.of());
    }
}
