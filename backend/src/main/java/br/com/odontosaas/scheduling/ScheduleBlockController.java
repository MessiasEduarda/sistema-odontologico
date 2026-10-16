package br.com.odontosaas.scheduling;

import br.com.odontosaas.scheduling.SchedulingDtos.ScheduleBlockRequest;
import br.com.odontosaas.scheduling.SchedulingDtos.ScheduleBlockView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/schedule-blocks")
@Tag(name = "Agenda: bloqueios", description = "Folgas, férias, feriados e outros períodos sem atendimento")
public class ScheduleBlockController {

    private final ScheduleBlockService service;

    public ScheduleBlockController(ScheduleBlockService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('schedule:read')")
    @Operation(summary = "Bloqueios no período", description = "from e to obrigatórios.")
    public List<ScheduleBlockView> list(@RequestParam Instant from, @RequestParam Instant to) {
        return service.list(from, to);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('schedule:write')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Cria bloqueio",
            description = "professionalId nulo bloqueia a clínica inteira. Atendimentos já marcados no período não "
                    + "mudam (appointmentsInside informa quantos remarcar).")
    public ScheduleBlockView create(@Valid @RequestBody ScheduleBlockRequest request) {
        return service.create(request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('schedule:write')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
