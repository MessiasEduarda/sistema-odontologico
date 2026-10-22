package br.com.odontosaas.shared;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class RequestInfo {

    private RequestInfo() {}

    public static String clientIp() {
        HttpServletRequest request = current();
        return request == null ? null : truncate(request.getRemoteAddr(), 64);
    }

    public static String userAgent() {
        HttpServletRequest request = current();
        return request == null ? null : truncate(request.getHeader("User-Agent"), 255);
    }

    private static HttpServletRequest current() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
