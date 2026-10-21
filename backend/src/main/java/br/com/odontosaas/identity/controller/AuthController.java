package br.com.odontosaas.identity.controller;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.identity.dto.request.AcceptTermsRequest;
import br.com.odontosaas.identity.dto.request.ChangePasswordRequest;
import br.com.odontosaas.identity.dto.request.ForgotPasswordRequest;
import br.com.odontosaas.identity.dto.request.LoginRequest;
import br.com.odontosaas.identity.dto.request.ResetPasswordRequest;
import br.com.odontosaas.identity.dto.request.SwitchClinicRequest;
import br.com.odontosaas.identity.dto.response.MeResponse;
import br.com.odontosaas.identity.dto.response.SessionView;
import br.com.odontosaas.identity.security.AuthenticatedUser;
import br.com.odontosaas.identity.security.CurrentUser;
import br.com.odontosaas.identity.service.AuthService;
import br.com.odontosaas.identity.service.PasswordService;
import br.com.odontosaas.identity.service.SessionStore;
import br.com.odontosaas.identity.service.TermsService;
import br.com.odontosaas.shared.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticação", description = "Login, sessão, clínica ativa e senha")
public class AuthController {

    private final AuthService auth;
    private final PasswordService passwords;
    private final SessionStore sessions;
    private final AuditService audit;
    private final TermsService terms;

    public AuthController(
            AuthService auth,
            PasswordService passwords,
            SessionStore sessions,
            AuditService audit,
            TermsService terms) {
        this.auth = auth;
        this.passwords = passwords;
        this.sessions = sessions;
        this.audit = audit;
        this.terms = terms;
    }

    @GetMapping("/csrf")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Emite o cookie XSRF-TOKEN",
            description = "Público. Requisições que alteram estado devem enviar o valor no header X-XSRF-TOKEN.")
    public void csrf(CsrfToken token) {
        token.getToken(); // força a geração/gravação do cookie
    }

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Público. Erros: 400, 401 INVALID_CREDENTIALS, 429 TOO_MANY_ATTEMPTS.")
    public MeResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        return auth.login(request, response);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Logout", description = "Revoga a sessão atual e apaga o cookie.")
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        auth.logout(request, response);
    }

    @GetMapping("/me")
    @Operation(summary = "Usuário autenticado, clínica ativa, permissões e clínicas disponíveis")
    public MeResponse me() {
        return auth.me(CurrentUser.require());
    }

    @PostMapping("/terms/accept")
    @Operation(
            summary = "Aceita os Termos de uso e a Política de privacidade",
            description =
                    "Uma vez por usuário e versão (idempotente). 409 TERMS_OUTDATED se a versão não for a vigente.")
    public MeResponse acceptTerms(@Valid @RequestBody AcceptTermsRequest request) {
        AuthenticatedUser user = CurrentUser.require();
        terms.accept(user.userId(), request.version());
        return auth.me(user);
    }

    @PostMapping("/switch-clinic")
    @Operation(
            summary = "Troca a clínica ativa",
            description = "Aceito somente se houver vínculo ativo. Rotaciona a sessão. 404 se não houver vínculo.")
    public MeResponse switchClinic(@Valid @RequestBody SwitchClinicRequest request, HttpServletResponse response) {
        return auth.switchClinic(CurrentUser.require(), request.clinicId(), response);
    }

    @GetMapping("/sessions")
    @Operation(summary = "Sessões ativas do próprio usuário")
    public List<SessionView> listSessions() {
        AuthenticatedUser user = CurrentUser.require();
        return sessions.listActive(user.userId()).stream()
                .map(s -> new SessionView(
                        s.id(), s.createdAt(), s.lastSeenAt(), s.ip(), s.userAgent(), s.id().equals(user.sessionId())))
                .toList();
    }

    @DeleteMapping("/sessions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Encerra uma sessão do próprio usuário", description = "404 se não for do usuário.")
    public void revokeSession(@PathVariable UUID id) {
        AuthenticatedUser user = CurrentUser.require();
        if (!sessions.revokeOwned(user.userId(), id, "USER_REVOKED")) {
            throw ApiException.notFound("Sessão");
        }
        audit.recordGlobal(AuditAction.SESSION_REVOKED, user.userId(), "session", id, Map.of());
    }

    @PostMapping("/sessions/revoke-others")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Encerra todas as outras sessões do usuário")
    public void revokeOtherSessions() {
        AuthenticatedUser user = CurrentUser.require();
        int count = sessions.revokeAllForUser(user.userId(), "USER_REVOKED", user.sessionId());
        audit.recordGlobal(AuditAction.SESSION_REVOKED, user.userId(), "session", null, Map.of("count", count));
    }

    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Solicita e-mail de redefinição", description = "Público. Sempre 202 (não revela contas).")
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwords.requestReset(request.email());
    }

    @PostMapping("/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Redefine a senha com o token do e-mail",
            description = "Público. Encerra todas as sessões. Erros: 400 INVALID_TOKEN, 400 VALIDATION_ERROR.")
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwords.reset(request.token(), request.newPassword());
    }

    @PostMapping("/password/change")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Troca a própria senha", description = "Exige a senha atual. Encerra as outras sessões.")
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        passwords.change(CurrentUser.require(), request.currentPassword(), request.newPassword());
    }
}
