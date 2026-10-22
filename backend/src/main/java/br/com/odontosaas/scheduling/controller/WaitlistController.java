package br.com.odontosaas.scheduling.controller;

import br.com.odontosaas.scheduling.dto.request.WaitlistRequest;
import br.com.odontosaas.scheduling.dto.request.WaitlistResolveRequest;
import br.com.odontosaas.scheduling.dto.response.WaitlistView;
import br.com.odontosaas.scheduling.service.WaitlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/waitlist")
@Tag(name = "Agenda: lista de espera", description = "Pacientes aguardando vaga ou encaixe")
public class WaitlistController {

    private final WaitlistService service;

    public WaitlistController(WaitlistService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('schedule:read')")
    @Operation(summary = "Lista de espera", description = "status: WAITING (padrão), SCHEDULED ou REMOVED.")
    public List<WaitlistView> list(@RequestParam(required = false) String status) {
        return service.list(status);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('schedule:write')")
    @ResponseStatus(HttpStatus.CREATED)
    public WaitlistView create(@Valid @RequestBody WaitlistRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('schedule:write')")
    public WaitlistView update(@PathVariable UUID id, @Valid @RequestBody WaitlistRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAuthority('schedule:write')")
    @Operation(summary = "Tira da lista", description = "SCHEDULED com o appointmentId marcado, ou REMOVED.")
    public WaitlistView resolve(@PathVariable UUID id, @Valid @RequestBody WaitlistResolveRequest request) {
        return service.resolve(id, request);
    }
}
