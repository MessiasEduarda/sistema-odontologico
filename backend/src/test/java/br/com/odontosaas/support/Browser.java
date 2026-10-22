package br.com.odontosaas.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

public final class Browser {

    public static final String SESSION_COOKIE = "odonto_session";

    private final MockMvcTester mvc;
    private final String csrf = "csrf-" + UUID.randomUUID();
    private String session;

    public Browser(MockMvcTester mvc) {
        this.mvc = mvc;
    }

    public String session() {
        return session;
    }

    public Browser useSession(String session) {
        this.session = session;
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
        if (session != null) cookies.add(new Cookie(SESSION_COOKIE, session));

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
            if (header.startsWith(SESSION_COOKIE + "=")) {
                String value = header.substring(SESSION_COOKIE.length() + 1, header.indexOf(';'));
                session = value.isEmpty() ? null : value;
            }
        }
    }
}
