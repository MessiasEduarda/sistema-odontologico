package br.com.odontosaas.identity.service;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.identity.dto.request.LoginRequest;
import br.com.odontosaas.identity.dto.response.ClinicSummary;
import br.com.odontosaas.identity.dto.response.MeResponse;
import br.com.odontosaas.identity.dto.response.UserView;
import br.com.odontosaas.identity.entity.User;
import br.com.odontosaas.identity.repository.UserRepository;
import br.com.odontosaas.identity.security.AuthenticatedUser;
import br.com.odontosaas.identity.security.Permission;
import br.com.odontosaas.identity.support.SessionCookies;
import br.com.odontosaas.shared.ApiException;
import br.com.odontosaas.shared.RequestInfo;
import br.com.odontosaas.tenancy.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final LoginThrottle throttle;
    private final SessionStore sessions;
    private final SessionCookies cookies;
    private final AccessQueries access;
    private final AuditService audit;
    private final TermsService terms;
    private final String dummyHash;

    public AuthService(
            UserRepository users,
            PasswordEncoder encoder,
            LoginThrottle throttle,
            SessionStore sessions,
            SessionCookies cookies,
            AccessQueries access,
            AuditService audit,
            TermsService terms) {
        this.users = users;
        this.encoder = encoder;
        this.throttle = throttle;
        this.sessions = sessions;
        this.cookies = cookies;
        this.access = access;
        this.audit = audit;
        this.terms = terms;
        // Usado quando o e-mail não existe, para o tempo de resposta não revelar quais contas existem.
        this.dummyHash = encoder.encode("senha-ficticia-para-igualar-tempo");
    }

    /**
     * Login. Respostas de erro são idênticas para e-mail inexistente, senha errada e conta desativada. Se o usuário
     * tiver exatamente uma clínica, ela já fica ativa.
     */
    public MeResponse login(LoginRequest request, HttpServletResponse response) {
        String email = User.normalizeEmail(request.email());
        String ip = RequestInfo.clientIp();
        try {
            throttle.check(email, ip);
        } catch (ApiException blocked) {
            throttle.recordFailure(email, ip, null, AuditAction.LOGIN_BLOCKED);
            throw blocked;
        }

        Optional<User> found = users.findByEmail(email);
        boolean valid;
        if (found.isPresent()) {
            valid = encoder.matches(request.password(), found.get().getPasswordHash())
                    && found.get().isActive();
        } else {
            encoder.matches(request.password(), dummyHash);
            valid = false;
        }
        if (!valid) {
            throttle.recordFailure(email, ip, found.map(User::getId).orElse(null), AuditAction.LOGIN_FAILED);
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Falha no login", "E-mail ou senha inválidos.");
        }

        User user = found.get();
        throttle.recordSuccess(email, ip);
        List<ClinicSummary> clinics =
                TenantContext.runAs(null, user.getId(), () -> access.activeClinicsOf(user.getId()));
        UUID activeTenant = clinics.size() == 1 ? clinics.getFirst().id() : null;

        SessionStore.NewSession session = sessions.create(user.getId(), activeTenant);
        cookies.write(response, session.rawToken());
        TenantContext.runAs(activeTenant, user.getId(), () -> {
            audit.record(AuditAction.LOGIN_SUCCEEDED, "session", session.id(), Map.of());
            return null;
        });
        return buildMe(user.getId(), user.getName(), user.getEmail(), user.isPlatformAdmin(), activeTenant);
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        cookies.read(request).flatMap(sessions::findActive).ifPresent(session -> {
            sessions.revoke(session.id(), "LOGOUT");
            TenantContext.runAs(session.activeTenantId(), session.userId(), () -> {
                audit.record(AuditAction.LOGOUT, "session", session.id(), Map.of());
                return null;
            });
        });
        cookies.clear(response);
    }

    /**
     * Troca a clínica ativa. O ID recebido é apenas uma ESCOLHA: só é aceito se o servidor confirmar vínculo ativo do
     * usuário com uma clínica ativa. A sessão é rotacionada (novo token).
     */
    @Transactional
    public MeResponse switchClinic(AuthenticatedUser current, UUID clinicId, HttpServletResponse response) {
        boolean allowed = TenantContext.runAs(
                        clinicId, current.userId(), () -> access.permissionsIn(clinicId, current.userId()))
                .isPresent();
        if (!allowed) {
            throw ApiException.notFound("Clínica");
        }
        sessions.revoke(current.sessionId(), "ROTATED");
        SessionStore.NewSession session = sessions.create(current.userId(), clinicId);
        cookies.write(response, session.rawToken());
        TenantContext.runAs(clinicId, current.userId(), () -> {
            audit.record(AuditAction.TENANT_SWITCHED, "tenant", clinicId, Map.of());
            return null;
        });
        return buildMe(current.userId(), current.name(), current.email(), current.platformAdmin(), clinicId);
    }

    public MeResponse me(AuthenticatedUser current) {
        return buildMe(current.userId(), current.name(), current.email(), current.platformAdmin(), current.tenantId());
    }

    private MeResponse buildMe(UUID userId, String name, String email, boolean platformAdmin, UUID activeTenant) {
        return TenantContext.runAs(activeTenant, userId, () -> {
            List<ClinicSummary> clinics = access.activeClinicsOf(userId);
            ClinicSummary active = activeTenant == null
                    ? null
                    : clinics.stream()
                            .filter(c -> c.id().equals(activeTenant))
                            .findFirst()
                            .orElse(null);
            Set<Permission> permissions = active == null
                    ? Set.of()
                    : access.permissionsIn(activeTenant, userId).orElse(Set.of());
            return new MeResponse(
                    new UserView(userId, name, email),
                    platformAdmin,
                    active,
                    permissions.stream().map(Permission::code).sorted().toList(),
                    clinics,
                    terms.hasAcceptedCurrent(userId),
                    TermsService.CURRENT_VERSION);
        });
    }
}
