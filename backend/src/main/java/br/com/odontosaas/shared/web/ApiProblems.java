package br.com.odontosaas.shared.web;

import java.net.URI;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

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

    public static ProblemDetail enrich(ProblemDetail problem) {
        String traceId = RequestIdFilter.currentRequestId();
        if (traceId != null) {
            problem.setProperty("traceId", traceId);
        }
        return problem;
    }
}
