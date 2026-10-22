package br.com.odontosaas.identity.service;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.identity.config.SecurityProperties;
import br.com.odontosaas.identity.dto.request.AcceptInvitationRequest;
import br.com.odontosaas.identity.dto.response.InvitationPreview;
import br.com.odontosaas.identity.dto.response.InvitationView;
import br.com.odontosaas.identity.entity.Invitation;
import br.com.odontosaas.identity.entity.Membership;
import br.com.odontosaas.identity.entity.Role;
import br.com.odontosaas.identity.entity.User;
import br.com.odontosaas.identity.repository.AccessQueries;
import br.com.odontosaas.identity.repository.InvitationRepository;
import br.com.odontosaas.identity.repository.MembershipRepository;
import br.com.odontosaas.identity.repository.RoleRepository;
import br.com.odontosaas.identity.repository.UserRepository;
import br.com.odontosaas.identity.support.PasswordPolicy;
import br.com.odontosaas.shared.ApiException;
import br.com.odontosaas.shared.RequestInfo;
import br.com.odontosaas.shared.SecureTokens;
import br.com.odontosaas.tenancy.TenantContext;
import br.com.odontosaas.tenancy.entity.Tenant;
import br.com.odontosaas.tenancy.repository.TenantRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvitationService {

    private final InvitationRepository invitations;
    private final RoleRepository roles;
    private final MembershipRepository memberships;
    private final UserRepository users;
    private final TenantRepository tenants;
    private final AccessQueries access;
    private final PasswordEncoder encoder;
    private final PasswordPolicy policy;
    private final LoginThrottle throttle;
    private final IdentityMailer mailer;
    private final AuditService audit;
    private final SecurityProperties props;

    public InvitationService(
            InvitationRepository invitations,
            RoleRepository roles,
            MembershipRepository memberships,
            UserRepository users,
            TenantRepository tenants,
            AccessQueries access,
            PasswordEncoder encoder,
            PasswordPolicy policy,
            LoginThrottle throttle,
            IdentityMailer mailer,
            AuditService audit,
            SecurityProperties props) {
        this.invitations = invitations;
        this.roles = roles;
        this.memberships = memberships;
        this.users = users;
        this.tenants = tenants;
        this.access = access;
        this.encoder = encoder;
        this.policy = policy;
        this.throttle = throttle;
        this.mailer = mailer;
        this.audit = audit;
        this.props = props;
    }

    @Transactional
    public InvitationView invite(String rawEmail, String roleCode, UUID invitedBy) {
        UUID tenantId = TenantContext.requireTenantId();
        String email = User.normalizeEmail(rawEmail);
        Role role = roles.findByTenantIdAndCode(tenantId, roleCode)
                .orElseThrow(() -> ApiException.invalidField("roleCode", "Papel inexistente."));

        Optional<User> existing = users.findByEmail(email);
        if (existing.isPresent()) {
            memberships
                    .findByTenantIdAndUserId(tenantId, existing.get().getId())
                    .filter(Membership::isActive)
                    .ifPresent(m -> {
                        throw ApiException.conflict("ALREADY_MEMBER", "Essa pessoa já faz parte da equipe.");
                    });
        }
        invitations
                .findByTenantIdAndEmailAndStatus(tenantId, email, Invitation.Status.PENDING)
                .ifPresent(previous -> {
                    previous.revoke();
                    invitations.saveAndFlush(previous);
                });

        String raw = SecureTokens.generate();
        Invitation invitation = new Invitation(
                tenantId,
                email,
                role.getId(),
                SecureTokens.sha256Hex(raw),
                invitedBy,
                Instant.now().plus(props.invitationTtl()));
        invitations.saveAndFlush(invitation);
        audit.record(AuditAction.INVITATION_CREATED, "invitation", invitation.getId(), Map.of("role", roleCode));

        String clinicName = tenants.findById(tenantId).map(Tenant::getName).orElse("");
        mailer.sendInvitation(email, clinicName, role.getName(), raw);
        return toView(invitation, role.getCode());
    }

    @Transactional(readOnly = true)
    public List<InvitationView> listPending() {
        UUID tenantId = TenantContext.requireTenantId();
        Map<UUID, String> roleCodes = roleCodes(tenantId);
        return invitations.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, Invitation.Status.PENDING).stream()
                .map(i -> toView(i, roleCodes.get(i.getRoleId())))
                .toList();
    }

    @Transactional
    public void revoke(UUID invitationId) {
        UUID tenantId = TenantContext.requireTenantId();
        Invitation invitation = invitations
                .findByIdAndTenantId(invitationId, tenantId)
                .filter(i -> i.getStatus() == Invitation.Status.PENDING)
                .orElseThrow(() -> ApiException.notFound("Convite"));
        invitation.revoke();
        invitations.saveAndFlush(invitation);
        audit.record(AuditAction.INVITATION_REVOKED, "invitation", invitationId, Map.of());
    }

    @Transactional(readOnly = true)
    public InvitationPreview preview(String rawToken) {
        Invitation invitation = findUsable(rawToken);
        return TenantContext.runAs(invitation.getTenantId(), null, () -> {
            String clinicName = tenants.findById(invitation.getTenantId())
                    .map(Tenant::getName)
                    .orElse("");
            String roleName =
                    roles.findById(invitation.getRoleId()).map(Role::getName).orElse("");
            boolean existing = users.findByEmail(invitation.getEmail()).isPresent();
            return new InvitationPreview(clinicName, invitation.getEmail(), roleName, existing);
        });
    }

    @Transactional
    public void accept(AcceptInvitationRequest request) {
        Invitation invitation = findUsable(request.token());
        String email = invitation.getEmail();
        Optional<User> existing = users.findByEmail(email);

        User user;
        if (existing.isPresent()) {
            user = existing.get();
            String ip = RequestInfo.clientIp();
            throttle.check(email, ip);
            if (!user.isActive() || !encoder.matches(request.password(), user.getPasswordHash())) {
                throttle.recordFailure(email, ip, user.getId(), AuditAction.LOGIN_FAILED);
                throw new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "INVALID_CREDENTIALS",
                        "Falha na confirmação",
                        "Senha incorreta para a conta existente deste e-mail.");
            }
        } else {
            if (request.name() == null || request.name().isBlank()) {
                throw ApiException.invalidField("name", "Informe seu nome.");
            }
            policy.validate("password", request.password(), email);
            user = users.saveAndFlush(new User(email, request.name(), encoder.encode(request.password())));
        }

        UUID tenantId = invitation.getTenantId();
        UUID userId = user.getId();
        TenantContext.runAs(tenantId, userId, () -> {
            Optional<Membership> current = memberships.findByTenantIdAndUserId(tenantId, userId);
            boolean restart = current.map(m -> !m.isActive()).orElse(true);
            Membership membership = current.orElseGet(() -> new Membership(tenantId, userId));
            membership.activate();
            memberships.saveAndFlush(membership);
            if (restart) {
                access.replaceMembershipRoles(tenantId, membership.getId(), List.of(invitation.getRoleId()));
            } else {
                access.addMembershipRole(tenantId, membership.getId(), invitation.getRoleId());
            }

            invitation.accept(Instant.now());
            invitations.saveAndFlush(invitation);
            audit.record(AuditAction.INVITATION_ACCEPTED, "invitation", invitation.getId(), Map.of());
            return null;
        });
    }

    private Invitation findUsable(String rawToken) {
        ApiException invalid =
                ApiException.badRequest("INVALID_TOKEN", "Este convite é inválido, expirou ou já foi utilizado.");
        if (!SecureTokens.looksValid(rawToken)) throw invalid;
        String hash = SecureTokens.sha256Hex(rawToken);
        return TenantContext.runWithToken(hash, () -> invitations.findByTokenHash(hash))
                .filter(i -> i.isUsable(Instant.now()))
                .orElseThrow(() -> invalid);
    }

    private Map<UUID, String> roleCodes(UUID tenantId) {
        Map<UUID, String> map = new java.util.HashMap<>();
        roles.findByTenantIdOrderByName(tenantId).forEach(r -> map.put(r.getId(), r.getCode()));
        return map;
    }

    private static InvitationView toView(Invitation i, String roleCode) {
        return new InvitationView(
                i.getId(), i.getEmail(), roleCode, i.getStatus().name(), i.getExpiresAt(), i.getCreatedAt());
    }
}
