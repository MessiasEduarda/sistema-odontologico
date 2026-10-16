package br.com.odontosaas.shared.web;

import java.net.URI;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/**
 * Fábrica de respostas de erro no formato RFC 9457 (Problem Details), com os campos extras padronizados da API:
 * {@code code} (identificador estável, para o front-end) e {@code traceId}.
 */
public final class ApiProblems {

    private static final String TYPE_BASE = "https://docs.odontosaas.local/problemas/";

    private ApiProblems() {}

    public static ProblemDetail of(HttpStatusCode status, String code, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_BASE + code));
        problem.setTitle(title);
        problem.setProperty("code", code);
        enrich(problem);
        return problem;
    }

    /** Adiciona os campos padronizados a um ProblemDetail criado pelo Spring. */
    public static ProblemDetail enrich(ProblemDetail problem) {
        String traceId = RequestIdFilter.currentRequestId();
        if (traceId != null) {
            problem.setProperty("traceId", traceId);
        }
        return problem;
    }
}
