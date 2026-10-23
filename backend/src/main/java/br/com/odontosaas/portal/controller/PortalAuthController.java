package br.com.odontosaas.portal.controller;

import br.com.odontosaas.portal.dto.request.ActivationBody;
import br.com.odontosaas.portal.dto.request.EmailBody;
import br.com.odontosaas.portal.dto.request.PortalLoginBody;
import br.com.odontosaas.portal.dto.request.ProfileBody;
import br.com.odontosaas.portal.dto.request.ResetBody;
import br.com.odontosaas.portal.dto.request.TokenBody;
import br.com.odontosaas.portal.dto.request.VerifyCodeBody;
import br.com.odontosaas.portal.dto.response.ActivationView;
import br.com.odontosaas.portal.dto.response.LoginResult;
import br.com.odontosaas.portal.dto.response.PortalMe;
import br.com.odontosaas.portal.service.PortalAuthService;
import br.com.odontosaas.portal.service.PortalAuthService.Outcome;
import br.com.odontosaas.portal.support.PortalCookies;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Portal do paciente")
public class PortalAuthController {

    private static final String BASE = "/api/v1/public/portal";

    private final PortalAuthService auth;
    private final PortalCookies cookies;

    public PortalAuthController(PortalAuthService auth, PortalCookies cookies) {
        this.auth = auth;
        this.cookies = cookies;
    }

    @PostMapping(BASE + "/activation/lookup")
    @Operation(summary = "Público: dados do convite para criar a senha", tags = "Público")
    public ActivationView activationLookup(@Valid @RequestBody TokenBody body) {
        return auth.activationLookup(body.token());
    }

    @PostMapping(BASE + "/activation")
    @Operation(summary = "Público: confirma a data de nascimento e cria a senha do portal", tags = "Público")
    public PortalMe activate(@Valid @RequestBody ActivationBody body, HttpServletResponse response) {
        return apply(auth.activate(body), response);
    }

    @PostMapping(BASE + "/login")
    @Operation(summary = "Público: login do paciente (pede código por e-mail em aparelho novo)", tags = "Público")
    public LoginResult login(
            @Valid @RequestBody PortalLoginBody body,
            @CookieValue(name = PortalCookies.DEVICE, required = false) String device,
            HttpServletResponse response) {
        return apply(auth.login(body.email(), body.password(), device), response);
    }

    @PostMapping(BASE + "/login/verify")
    @Operation(summary = "Público: confirma o código enviado por e-mail", tags = "Público")
    public LoginResult verify(@Valid @RequestBody VerifyCodeBody body, HttpServletResponse response) {
        return apply(auth.verifyCode(body.challengeId(), body.code()), response);
    }

    @PostMapping(BASE + "/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Público: encerra a sessão do paciente", tags = "Público")
    public void logout(
            @CookieValue(name = PortalCookies.SESSION, required = false) String session, HttpServletResponse response) {
        auth.logout(session);
        cookies.clearSession(response);
    }

    @PostMapping(BASE + "/password/forgot")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Público: envia o link de redefinição de senha (sempre responde igual)", tags = "Público")
    public void forgot(@Valid @RequestBody EmailBody body) {
        auth.forgotPassword(body.email());
    }

    @PostMapping(BASE + "/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Público: cria nova senha a partir do link; encerra as sessões abertas", tags = "Público")
    public void reset(@Valid @RequestBody ResetBody body) {
        auth.resetPassword(body.token(), body.password());
    }

    @GetMapping(BASE + "/me")
    @Operation(summary = "Paciente logado: e-mail e perfis (clínica e paciente) disponíveis", tags = "Público")
    public PortalMe me(@CookieValue(name = PortalCookies.SESSION, required = false) String session) {
        return auth.me(session);
    }

    @PostMapping(BASE + "/profile")
    @Operation(summary = "Paciente logado: escolhe a clínica/paciente ativo", tags = "Público")
    public PortalMe profile(
            @CookieValue(name = PortalCookies.SESSION, required = false) String session,
            @Valid @RequestBody ProfileBody body) {
        return auth.selectProfile(session, body.tenantId(), body.patientId());
    }

    private <T> T apply(Outcome<T> outcome, HttpServletResponse response) {
        if (outcome.sessionToken() != null) cookies.writeSession(response, outcome.sessionToken());
        if (outcome.deviceToken() != null) {
            cookies.writeDevice(response, outcome.deviceToken(), PortalAuthService.DEVICE_TTL);
        }
        return outcome.body();
    }
}
