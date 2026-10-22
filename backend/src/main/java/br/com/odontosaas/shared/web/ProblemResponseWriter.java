package br.com.odontosaas.shared.web;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class ProblemResponseWriter {

    private ProblemResponseWriter() {}

    public static void write(HttpServletResponse response, int status, String code, String title, String detail)
            throws IOException {
        String traceId = RequestIdFilter.currentRequestId();
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter()
                .write("{\"type\":\"https://docs.odontosaas.local/problemas/" + code + "\"," + "\"title\":\""
                        + title + "\"," + "\"status\":"
                        + status + "," + "\"detail\":\""
                        + detail + "\"," + "\"code\":\""
                        + code + "\"" + (traceId == null ? "" : ",\"traceId\":\"" + traceId + "\"")
                        + "}");
    }
}
