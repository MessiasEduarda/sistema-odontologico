package br.com.odontosaas.shared;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Erro de negócio ou de requisição com resposta HTTP padronizada. A mensagem ({@code detail}) é sempre segura para
 * exibir ao usuário final.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String title;
    private final List<Map<String, String>> fieldErrors;
    private final Long retryAfterSeconds;

    public ApiException(
            HttpStatus status,
            String code,
            String title,
            String detail,
            List<Map<String, String>> fieldErrors,
            Long retryAfterSeconds) {
        super(detail);
        this.status = status;
        this.code = code;
        this.title = title;
        this.fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public ApiException(HttpStatus status, String code, String title, String detail) {
        this(status, code, title, detail, null, null);
    }

    /** Recurso inexistente OU pertencente a outra clínica — a resposta nunca diferencia os dois casos. */
    public static ApiException notFound(String what) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Não encontrado", what + " não encontrado(a).");
    }

    public static ApiException conflict(String code, String detail) {
        return new ApiException(HttpStatus.CONFLICT, code, "Conflito", detail);
    }

    public static ApiException businessRule(String code, String detail) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, code, "Operação não permitida", detail);
    }

    public static ApiException badRequest(String code, String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, "Requisição inválida", detail);
    }

    public static ApiException invalidField(String field, String message) {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Dados inválidos",
                "Um ou mais campos não passaram na validação.",
                List.of(Map.of("field", field, "message", message)),
                null);
    }

    public static ApiException forbidden() {
        return new ApiException(
                HttpStatus.FORBIDDEN, "FORBIDDEN", "Acesso negado", "Você não tem permissão para executar esta ação.");
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String title() {
        return title;
    }

    public List<Map<String, String>> fieldErrors() {
        return fieldErrors;
    }

    public Long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
