package br.com.odontosaas.identity.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
