package br.com.odontosaas.portal.support;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class PortalCookies {

    public static final String SESSION = "odonto_portal";
    public static final String DEVICE = "odonto_portal_device";

    private final boolean secure;
    private final Duration sessionMaxAge;

    public PortalCookies(
            @Value("${app.security.cookie-secure}") boolean secure,
            @Value("${app.security.session-absolute-timeout}") Duration sessionMaxAge) {
        this.secure = secure;
        this.sessionMaxAge = sessionMaxAge;
    }

    public void writeSession(HttpServletResponse response, String raw) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(SESSION, raw, sessionMaxAge));
    }

    public void clearSession(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(SESSION, "", Duration.ZERO));
    }

    public void writeDevice(HttpServletResponse response, String raw, Duration maxAge) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(DEVICE, raw, maxAge));
    }

    private String build(String name, String value, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build()
                .toString();
    }
}
