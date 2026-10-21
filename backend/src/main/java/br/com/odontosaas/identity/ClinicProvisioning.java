package br.com.odontosaas.identity;

import br.com.odontosaas.identity.entity.Membership;
import br.com.odontosaas.identity.entity.Role;
import br.com.odontosaas.identity.entity.User;
import br.com.odontosaas.identity.repository.MembershipRepository;
import br.com.odontosaas.identity.repository.RoleRepository;
import br.com.odontosaas.identity.repository.UserRepository;
import br.com.odontosaas.identity.service.AccessQueries;
import br.com.odontosaas.identity.service.InvitationService;
import br.com.odontosaas.identity.support.RoleTemplates;
import br.com.odontosaas.tenancy.TenantContext;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API do módulo de identidade para preparar clínicas novas (usada pela administração da plataforma e pelo seed de
 * desenvolvimento). Cada operação define internamente o contexto da clínica alvo, já validada pelo chamador.
 */
@Service
public class ClinicProvisioning {

    private final RoleRepository roles;
    private final MembershipRepository memberships;
    private final UserRepository users;
    private final AccessQueries access;
    private final InvitationService invitations;
    private final PasswordEncoder encoder;

    public ClinicProvisioning(
            RoleRepository roles,
            MembershipRepository memberships,
            UserRepository users,
            AccessQueries access,
            InvitationService invitations,
            PasswordEncoder encoder) {
        this.roles = roles;
        this.memberships = memberships;
        this.users = users;
        this.access = access;
        this.invitations = invitations;
        this.encoder = encoder;
    }

    /** Cria os papéis padrão (RoleTemplates) na clínica. */
    @Transactional
    public void createDefaultRoles(UUID tenantId, UUID actorUserId) {
        TenantContext.runAs(tenantId, actorUserId, () -> {
            RoleTemplates.ALL.forEach((code, template) -> {
                Role role = roles.saveAndFlush(new Role(tenantId, code, template.name(), true));
                access.replaceRolePermissions(tenantId, role.getId(), template.permissions());
            });
            return null;
        });
    }

    /** Convida o responsável pela clínica com o papel de administrador. */
    @Transactional
    public void inviteOwner(UUID tenantId, String email, UUID actorUserId) {
        TenantContext.runAs(tenantId, actorUserId, () -> invitations.invite(email, RoleTemplates.OWNER, actorUserId));
    }

    /** Cria usuário diretamente (seed de desenvolvimento e bootstrap do administrador da plataforma). */
    @Transactional
    public UUID createUser(String email, String name, String rawPassword, boolean platformAdmin) {
        User user = new User(email, name, encoder.encode(rawPassword));
        if (platformAdmin) user.grantPlatformAdmin();
        return users.saveAndFlush(user).getId();
    }

    /** Vincula um usuário existente à clínica com um papel (seed de desenvolvimento). */
    @Transactional
    public void addMember(UUID tenantId, UUID userId, String roleCode) {
        TenantContext.runAs(tenantId, userId, () -> {
            Role role = roles.findByTenantIdAndCode(tenantId, roleCode).orElseThrow();
            Membership membership = memberships.saveAndFlush(new Membership(tenantId, userId));
            access.addMembershipRole(tenantId, membership.getId(), role.getId());
            return null;
        });
    }

    public boolean userExists(String email) {
        return users.findByEmail(User.normalizeEmail(email)).isPresent();
    }

    public java.util.Optional<UUID> userId(String email) {
        return users.findByEmail(User.normalizeEmail(email)).map(User::getId);
    }

    public boolean platformAdminExists() {
        return users.existsByPlatformRole("PLATFORM_ADMIN");
    }
}
