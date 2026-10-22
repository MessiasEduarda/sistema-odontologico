package br.com.odontosaas.shared.web;

import br.com.odontosaas.shared.ApiException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ApiProblems.of(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Dados inválidos",
                "Um ou mais campos não passaram na validação.");
        List<Map<String, String>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field",
                        error.getField(),
                        "message",
                        error.getDefaultMessage() == null ? "inválido" : error.getDefaultMessage()))
                .toList();
        problem.setProperty("fieldErrors", fieldErrors);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApi(ApiException ex) {
        ProblemDetail problem = ApiProblems.of(ex.status(), ex.code(), ex.title(), ex.getMessage());
        if (!ex.fieldErrors().isEmpty()) {
            problem.setProperty("fieldErrors", ex.fieldErrors());
        }
        ResponseEntity.BodyBuilder response = ResponseEntity.status(ex.status());
        if (ex.retryAfterSeconds() != null) {
            response.header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.retryAfterSeconds()));
        }
        return response.body(problem);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        ApiException forbidden = ApiException.forbidden();
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiProblems.of(
                        HttpStatus.FORBIDDEN, forbidden.code(), forbidden.title(), forbidden.getMessage()));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiProblems.of(
                        HttpStatus.CONFLICT,
                        "STALE_VERSION",
                        "Registro desatualizado",
                        "O registro foi alterado por outra pessoa. Recarregue e tente novamente."));
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleIntegrity(org.springframework.dao.DataIntegrityViolationException ex) {
        String state = sqlState(ex);
        String constraint = ex.getMostSpecificCause().getMessage() == null
                ? ""
                : ex.getMostSpecificCause().getMessage();
        ApiException mapped;
        if ("23P01".equals(state)) {
            mapped = ApiException.conflict(
                    "SCHEDULE_CONFLICT",
                    constraint.contains("room_overlap")
                            ? "A sala já está ocupada nesse horário."
                            : "O profissional já tem um atendimento nesse horário.");
        } else if ("23505".equals(state)) {
            mapped = ApiException.conflict("DUPLICATE", "Já existe um registro com esses dados.");
        } else if ("23503".equals(state)) {
            mapped = ApiException.businessRule(
                    "INVALID_REFERENCE", "Um dos registros relacionados não existe ou está em uso.");
        } else if ("23514".equals(state) || (state != null && state.startsWith("22"))) {
            // 23514: check constraint. Classe 22: texto longo demais, número fora da faixa, formato inválido.
            mapped = ApiException.businessRule("INVALID_VALUE", "Um ou mais valores estão fora do permitido.");
        } else {
            return handleUnexpected(ex);
        }
        return handleApi(mapped);
    }

    private static String sqlState(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof java.sql.SQLException sql && sql.getSQLState() != null) return sql.getSQLState();
        }
        return null;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Erro não tratado", ex);
        ProblemDetail problem = ApiProblems.of(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "Erro interno",
                "Ocorreu um erro inesperado. Informe o traceId ao suporte.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            if (problem.getProperties() == null || !problem.getProperties().containsKey("code")) {
                problem.setProperty("code", "HTTP_" + statusCode.value());
            }
            ApiProblems.enrich(problem);
        }
        return response;
    }
}
