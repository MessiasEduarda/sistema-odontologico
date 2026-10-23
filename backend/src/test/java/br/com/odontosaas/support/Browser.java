package br.com.odontosaas.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

public final class Browser {

    public static final String SESSION_COOKIE = "odonto_session";

    private final MockMvcTester mvc;
    private static final List<String> TRACKED = List.of(SESSION_COOKIE, "odonto_portal", "odonto_portal_device");

    private final String csrf = "csrf-" + UUID.randomUUID();
    private final Map<String, String> jar = new LinkedHashMap<>();

    public Browser(MockMvcTester mvc) {
        this.mvc = mvc;
    }

    public String session() {
        return jar.get(SESSION_COOKIE);
    }

    public String cookie(String name) {
        return jar.get(name);
    }

    public Browser useSession(String session) {
        if (session == null) jar.remove(SESSION_COOKIE);
        else jar.put(SESSION_COOKIE, session);
        return this;
    }

    public Browser withCookie(String name, String value) {
        if (value == null) jar.remove(name);
        else jar.put(name, value);
        return this;
    }

    public MvcTestResult get(String uri) {
        return send(HttpMethod.GET, uri, null, true);
    }

    public MvcTestResult getEncoded(String rawUri) {
        return send(HttpMethod.GET, java.net.URI.create(rawUri), null, true);
    }

    public MvcTestResult post(String uri, String json) {
        return send(HttpMethod.POST, uri, json, true);
    }

    public MvcTestResult put(String uri, String json) {
        return send(HttpMethod.PUT, uri, json, true);
    }

    public MvcTestResult delete(String uri) {
        return send(HttpMethod.DELETE, uri, null, true);
    }

    public MvcTestResult postWithoutCsrf(String uri, String json) {
        return send(HttpMethod.POST, uri, json, false);
    }

    public Browser login(String email, String password) {
        MvcTestResult result = post("/api/v1/auth/login", loginJson(email, password));
        assertThat(result).hasStatusOk();
        return this;
    }

    public Browser switchClinic(UUID clinicId) {
        MvcTestResult result = post("/api/v1/auth/switch-clinic", "{\"clinicId\":\"%s\"}".formatted(clinicId));
        assertThat(result).hasStatusOk();
        return this;
    }

    public static String loginJson(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }

    public static <T> T json(MvcTestResult result, String path) {
        return JsonPath.read(body(result), path);
    }

    public static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private MvcTestResult send(HttpMethod method, Object uri, String json, boolean withCsrf) {
        List<Cookie> cookies = new ArrayList<>();
        cookies.add(new Cookie("XSRF-TOKEN", csrf));
        jar.forEach((name, value) -> cookies.add(new Cookie(name, value)));

        var builder = mvc.method(method);
        var request = (uri instanceof java.net.URI raw ? builder.uri(raw) : builder.uri((String) uri))
                .cookie(cookies.toArray(Cookie[]::new));
        if (withCsrf) request = request.header("X-XSRF-TOKEN", csrf);
        if (json != null)
            request = request.contentType(MediaType.APPLICATION_JSON).content(json);

        MvcTestResult result = request.exchange();
        captureSession(result);
        return result;
    }

    private void captureSession(MvcTestResult result) {
        for (String header : result.getResponse().getHeaders("Set-Cookie")) {
            for (String name : TRACKED) {
                if (header.startsWith(name + "=")) {
                    String value = header.substring(name.length() + 1, header.indexOf(';'));
                    withCookie(name, value.isEmpty() ? null : value);
                }
            }
        }
    }
}
