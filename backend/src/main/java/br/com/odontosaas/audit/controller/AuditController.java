package br.com.odontosaas.audit.controller;

import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.audit.dto.response.AuditEventView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Auditoria")
public class AuditController {

    private final AuditService audit;

    public AuditController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping("/api/v1/clinic/audit")
    @PreAuthorize("hasAuthority('audit:read')")
    @Operation(summary = "Eventos da clínica", description = "Filtros: from, to (datas), action, resourceType")
    public List<AuditEventView> events(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType) {
        return audit.events(from, to, action, resourceType);
    }
}
