package br.com.odontosaas.shared.web;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Escreve Problem Details diretamente na resposta, para erros gerados fora do Spring MVC (filtros de segurança). Os
 * valores são constantes do servidor ou IDs validados, por isso a montagem manual do JSON é segura.
 */
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
