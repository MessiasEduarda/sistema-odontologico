package br.com.odontosaas.identity.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Parâmetros de autenticação e sessão ({@code app.security.*}). */
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        String sessionCookieName,
        boolean cookieSecure,
        Duration sessionIdleTimeout,
        Duration sessionAbsoluteTimeout,
        Duration loginWindow,
        int loginMaxFailuresPerEmail,
        int loginMaxFailuresPerIp,
        Duration passwordResetTtl,
        int passwordResetMaxPerHour,
        Duration invitationTtl) {}
