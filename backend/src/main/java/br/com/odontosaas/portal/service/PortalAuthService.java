package br.com.odontosaas.portal.service;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.portal.dto.request.ActivationBody;
import br.com.odontosaas.portal.dto.response.ActivationView;
import br.com.odontosaas.portal.dto.response.LoginResult;
import br.com.odontosaas.portal.dto.response.PortalMe;
import br.com.odontosaas.portal.dto.response.PortalProfile;
import br.com.odontosaas.portal.repository.PortalAccountRepository;
import br.com.odontosaas.portal.repository.PortalAccountRepository.AccountRow;
import br.com.odontosaas.portal.repository.PortalAccountRepository.ChallengeRow;
import br.com.odontosaas.portal.repository.PortalAccountRepository.LinkRow;
import br.com.odontosaas.portal.repository.PortalAccountRepository.PatientInfo;
import br.com.odontosaas.portal.repository.PortalAccountRepository.SessionRow;
import br.com.odontosaas.portal.repository.PortalAccountRepository.TokenRow;
import br.com.odontosaas.shared.ApiException;
import br.com.odontosaas.shared.PasswordPolicy;
import br.com.odontosaas.shared.RequestInfo;
import br.com.odontosaas.shared.SecureTokens;
import br.com.odontosaas.tenancy.TenantContext;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortalAuthService {

    public static final Duration DEVICE_TTL = Duration.ofDays(180);
    static final Duration CODE_TTL = Duration.ofMinutes(10);
    static final Duration RESET_TTL = Duration.ofHours(1);
    static final int MAX_CODE_ATTEMPTS = 5;
    static final int MAX_BIRTH_DATE_ATTEMPTS = 5;
    static final int MAX_RESETS_PER_HOUR = 3;

    public record Profile(UUID sessionId, UUID accountId, UUID tenantId, UUID patientId) {}

    public record Outcome<T>(T body, String sessionToken, String deviceToken) {}

    private static final SecureRandom RANDOM = new SecureRandom();

    private final PortalAccountRepository accounts;
    private final PasswordEncoder encoder;
    private final PasswordPolicy policy;
    private final AuditService audit;
    private final PortalMailer mailer;
    private final Duration idleTimeout;
    private final Duration absoluteTimeout;
    private final Duration loginWindow;
    private final int maxFailuresPerEmail;
    private final int maxFailuresPerIp;
    private final String dummyHash;

    public PortalAuthService(
            PortalAccountRepository accounts,
            PasswordEncoder encoder,
            PasswordPolicy policy,
            AuditService audit,
            PortalMailer mailer,
            @Value("${app.security.session-idle-timeout}") Duration idleTimeout,
            @Value("${app.security.session-absolute-timeout}") Duration absoluteTimeout,
            @Value("${app.security.login-window}") Duration loginWindow,
            @Value("${app.security.login-max-failures-per-email}") int maxFailuresPerEmail,
            @Value("${app.security.login-max-failures-per-ip}") int maxFailuresPerIp) {
        this.accounts = accounts;
        this.encoder = encoder;
        this.policy = policy;
        this.audit = audit;
        this.mailer = mailer;
        this.idleTimeout = idleTimeout;
        this.absoluteTimeout = absoluteTimeout;
        this.loginWindow = loginWindow;
        this.maxFailuresPerEmail = maxFailuresPerEmail;
        this.maxFailuresPerIp = maxFailuresPerIp;
        this.dummyHash = encoder.encode("portal-" + UUID.randomUUID());
    }

    @Transactional(readOnly = true)
    public ActivationView activationLookup(String rawToken) {
        TokenRow token = validToken(rawToken, "ACTIVATION");
        AccountRow account = accounts.find(token.accountId()).orElseThrow(PortalAuthService::invalidLink);
        PatientInfo patient = patientOf(token);
        String clinic = TenantContext.runAs(token.tenantId(), null, () -> accounts.clinicName(token.tenantId()));
        return new ActivationView(patient.fullName().split(" ")[0], clinic, account.email());
    }

    @Transactional(noRollbackFor = ApiException.class)
    public Outcome<PortalMe> activate(ActivationBody body) {
        TokenRow token = validToken(body.token(), "ACTIVATION");
        PatientInfo patient = patientOf(token);
        if (patient.birthDate() != null && !patient.birthDate().equals(body.birthDate())) {
            int attempts = accounts.registerTokenFailure(token.id());
            if (attempts >= MAX_BIRTH_DATE_ATTEMPTS) {
                accounts.useToken(token.id());
                throw ApiException.badRequest(
                        "ACTIVATION_LOCKED",
                        "Muitas tentativas com a data de nascimento errada. Peça um novo convite à clínica.");
            }
            throw ApiException.invalidField("birthDate", "A data de nascimento não confere com o cadastro da clínica.");
        }
        AccountRow account = accounts.find(token.accountId()).orElseThrow(PortalAuthService::invalidLink);
        policy.validate("password", body.password(), account.email());
        accounts.activate(account.id(), encoder.encode(body.password()));
        accounts.useToken(token.id());
        accounts.invalidateTokens(account.id(), "ACTIVATION");
        audit.recordGlobal(AuditAction.PORTAL_ACCOUNT_ACTIVATED, null, "portal_account", account.id(), Map.of());
        String device = trustNewDevice(account.id());
        String session = openSession(account.id(), token.tenantId(), token.patientId());
        return new Outcome<>(me(account.id(), token.tenantId(), token.patientId()), session, device);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public Outcome<LoginResult> login(String email, String password, String deviceToken) {
        String ip = RequestInfo.clientIp();
        long window = loginWindow.toSeconds();
        if (accounts.countEmailFailures(email, window) >= maxFailuresPerEmail
                || (ip != null && accounts.countIpFailures(ip, window) >= maxFailuresPerIp)) {
            throw new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "TOO_MANY_ATTEMPTS",
                    "Muitas tentativas",
                    "Muitas tentativas sem sucesso. Aguarde alguns minutos e tente novamente.",
                    null,
                    window);
        }
        Optional<AccountRow> found = accounts.findByEmail(email).filter(a -> "ACTIVE".equals(a.status()));
        String hash = found.map(AccountRow::passwordHash).orElse(dummyHash);
        boolean matches = encoder.matches(password, hash);
        if (found.isEmpty() || !matches) {
            accounts.recordAttempt(email, ip, false);
            audit.recordGlobal(
                    AuditAction.PORTAL_LOGIN_FAILED,
                    null,
                    "portal_account",
                    found.map(AccountRow::id).orElse(null),
                    Map.of());
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Não autenticado", "E-mail ou senha incorretos.");
        }
        AccountRow account = found.get();
        accounts.recordAttempt(email, ip, true);
        if (deviceToken != null
                && SecureTokens.looksValid(deviceToken)
                && accounts.isTrustedDevice(account.id(), SecureTokens.sha256Hex(deviceToken))) {
            return completeLogin(account, null);
        }
        String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
        UUID challenge = accounts.createChallenge(
                account.id(), codeHash(code), Instant.now().plus(CODE_TTL));
        mailer.sendLoginCode(account.email(), code);
        return new Outcome<>(LoginResult.codeRequired(challenge, mask(account.email())), null, null);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public Outcome<LoginResult> verifyCode(UUID challengeId, String code) {
        ChallengeRow challenge = accounts.findValidChallenge(challengeId)
                .orElseThrow(() -> ApiException.badRequest(
                        "CODE_EXPIRED", "O código expirou ou já foi usado. Entre novamente para receber outro."));
        if (!challenge.codeHash().equals(codeHash(code))) {
            int attempts = accounts.registerChallengeFailure(challenge.id());
            if (attempts >= MAX_CODE_ATTEMPTS) {
                accounts.useChallenge(challenge.id());
                throw ApiException.badRequest(
                        "CODE_EXPIRED", "Muitas tentativas com o código errado. Entre novamente para receber outro.");
            }
            throw ApiException.invalidField("code", "Código incorreto.");
        }
        accounts.useChallenge(challenge.id());
        AccountRow account = accounts.find(challenge.accountId()).orElseThrow();
        return completeLogin(account, trustNewDevice(account.id()));
    }

    private Outcome<LoginResult> completeLogin(AccountRow account, String newDevice) {
        accounts.touchLogin(account.id());
        audit.recordGlobal(AuditAction.PORTAL_LOGIN_SUCCEEDED, null, "portal_account", account.id(), Map.of());
        List<PortalProfile> profiles = profiles(account.id());
        PortalProfile single = profiles.size() == 1 ? profiles.getFirst() : null;
        String session = openSession(
                account.id(), single == null ? null : single.tenantId(), single == null ? null : single.patientId());
        PortalMe me = new PortalMe(
                account.email(),
                profiles,
                single == null ? null : single.tenantId(),
                single == null ? null : single.patientId());
        return new Outcome<>(LoginResult.authenticated(me), session, newDevice);
    }

    @Transactional
    public void forgotPassword(String email) {
        accounts.findByEmail(email).filter(a -> "ACTIVE".equals(a.status())).ifPresent(account -> {
            if (accounts.tokensCreatedSince(account.id(), "RESET", 3600) >= MAX_RESETS_PER_HOUR) return;
            accounts.invalidateTokens(account.id(), "RESET");
            String raw = SecureTokens.generate();
            accounts.insertToken(
                    account.id(),
                    "RESET",
                    SecureTokens.sha256Hex(raw),
                    null,
                    null,
                    Instant.now().plus(RESET_TTL));
            mailer.sendPasswordReset(account.email(), raw);
        });
    }

    @Transactional
    public void resetPassword(String rawToken, String password) {
        TokenRow token = validToken(rawToken, "RESET");
        AccountRow account = accounts.find(token.accountId()).orElseThrow(PortalAuthService::invalidLink);
        policy.validate("password", password, account.email());
        accounts.changePassword(account.id(), encoder.encode(password));
        accounts.useToken(token.id());
        accounts.revokeAllSessions(account.id());
        accounts.revokeDevices(account.id());
        audit.recordGlobal(AuditAction.PORTAL_PASSWORD_RESET, null, "portal_account", account.id(), Map.of());
    }

    @Transactional
    public PortalMe me(String sessionToken) {
        SessionRow session = requireSession(sessionToken);
        return me(session.accountId(), session.tenantId(), session.patientId());
    }

    @Transactional
    public PortalMe selectProfile(String sessionToken, UUID tenantId, UUID patientId) {
        SessionRow session = requireSession(sessionToken);
        boolean allowed = profiles(session.accountId()).stream()
                .anyMatch(p -> p.tenantId().equals(tenantId) && p.patientId().equals(patientId));
        if (!allowed) throw ApiException.notFound("Perfil");
        accounts.selectProfile(session.id(), tenantId, patientId);
        return me(session.accountId(), tenantId, patientId);
    }

    @Transactional
    public void logout(String sessionToken) {
        if (sessionToken == null || !SecureTokens.looksValid(sessionToken)) return;
        accounts.findActiveSession(SecureTokens.sha256Hex(sessionToken), idleTimeout.toSeconds())
                .ifPresent(s -> accounts.revokeSession(s.id()));
    }

    @Transactional
    public Profile requireProfile(String sessionToken) {
        SessionRow session = requireSession(sessionToken);
        if (session.tenantId() == null || session.patientId() == null) {
            throw ApiException.conflict("PROFILE_REQUIRED", "Escolha a clínica para continuar.");
        }
        boolean active = TenantContext.runAsPortalAccount(
                session.accountId(),
                () -> accounts.linksOf(session.accountId()).stream()
                        .anyMatch(l -> l.blockedAt() == null
                                && l.tenantId().equals(session.tenantId())
                                && l.patientId().equals(session.patientId())));
        if (!active) {
            accounts.revokeSession(session.id());
            throw unauthenticated();
        }
        return new Profile(session.id(), session.accountId(), session.tenantId(), session.patientId());
    }

    private SessionRow requireSession(String sessionToken) {
        if (sessionToken == null || !SecureTokens.looksValid(sessionToken)) throw unauthenticated();
        SessionRow session = accounts.findActiveSession(SecureTokens.sha256Hex(sessionToken), idleTimeout.toSeconds())
                .orElseThrow(PortalAuthService::unauthenticated);
        accounts.touchSession(session.id());
        return session;
    }

    private PortalMe me(UUID accountId, UUID tenantId, UUID patientId) {
        AccountRow account = accounts.find(accountId).orElseThrow(PortalAuthService::unauthenticated);
        return new PortalMe(account.email(), profiles(accountId), tenantId, patientId);
    }

    private List<PortalProfile> profiles(UUID accountId) {
        List<LinkRow> links = TenantContext.runAsPortalAccount(accountId, () -> accounts.linksOf(accountId));
        return links.stream()
                .filter(l -> l.blockedAt() == null)
                .map(l -> TenantContext.runAs(
                        l.tenantId(),
                        null,
                        () -> accounts.patient(l.tenantId(), l.patientId())
                                .map(p -> new PortalProfile(
                                        l.tenantId(), l.patientId(), accounts.clinicName(l.tenantId()), p.fullName()))
                                .orElse(null)))
                .filter(Objects::nonNull)
                .toList();
    }

    private String openSession(UUID accountId, UUID tenantId, UUID patientId) {
        String raw = SecureTokens.generate();
        accounts.createSession(
                SecureTokens.sha256Hex(raw), accountId, tenantId, patientId, absoluteTimeout.toSeconds());
        return raw;
    }

    private String trustNewDevice(UUID accountId) {
        String raw = SecureTokens.generate();
        accounts.trustDevice(
                accountId, SecureTokens.sha256Hex(raw), Instant.now().plus(DEVICE_TTL));
        return raw;
    }

    private TokenRow validToken(String raw, String purpose) {
        if (raw == null || !SecureTokens.looksValid(raw)) throw invalidLink();
        return accounts.findValidToken(SecureTokens.sha256Hex(raw), purpose)
                .orElseThrow(PortalAuthService::invalidLink);
    }

    private PatientInfo patientOf(TokenRow token) {
        return TenantContext.runAs(token.tenantId(), null, () -> accounts.patient(token.tenantId(), token.patientId()))
                .orElseThrow(PortalAuthService::invalidLink);
    }

    private static String codeHash(String code) {
        return SecureTokens.sha256Hex("portal-code:" + code);
    }

    private static String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return email;
        return email.charAt(0) + "•••" + email.substring(at);
    }

    private static ApiException invalidLink() {
        return ApiException.badRequest("INVALID_TOKEN", "Este link é inválido, expirou ou já foi usado.");
    }

    private static ApiException unauthenticated() {
        return new ApiException(
                HttpStatus.UNAUTHORIZED,
                "PORTAL_UNAUTHENTICATED",
                "Não autenticado",
                "Sua sessão terminou. Entre novamente no portal.");
    }
}
