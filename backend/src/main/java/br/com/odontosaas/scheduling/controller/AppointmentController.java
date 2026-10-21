package br.com.odontosaas.scheduling.controller;

import br.com.odontosaas.scheduling.dto.request.AppointmentRequest;
import br.com.odontosaas.scheduling.dto.request.PublicResponseRequest;
import br.com.odontosaas.scheduling.dto.request.StatusRequest;
import br.com.odontosaas.scheduling.dto.request.TokenRequest;
import br.com.odontosaas.scheduling.dto.response.AppointmentDetail;
import br.com.odontosaas.scheduling.dto.response.AppointmentView;
import br.com.odontosaas.scheduling.dto.response.FreeSlot;
import br.com.odontosaas.scheduling.dto.response.PublicAppointmentView;
import br.com.odontosaas.scheduling.service.AppointmentLinks;
import br.com.odontosaas.scheduling.service.AppointmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Agenda")
public class AppointmentController {

    private final AppointmentService service;
    private final AppointmentLinks links;

    public AppointmentController(AppointmentService service, AppointmentLinks links) {
        this.service = service;
        this.links = links;
    }

    @GetMapping("/api/v1/appointments")
    @PreAuthorize("hasAuthority('schedule:read')")
    @Operation(
            summary = "Lista agendamentos",
            description = "Período (from/to, até 62 dias) ou patientId (histórico). Filtros: professionalId, status.")
    public List<AppointmentView> list(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) UUID professionalId,
            @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) String status) {
        return service.list(from, to, professionalId, patientId, status);
    }

    @GetMapping("/api/v1/appointments/{id}")
    @PreAuthorize("hasAuthority('schedule:read')")
    @Operation(summary = "Agendamento com histórico de alterações")
    public AppointmentDetail detail(@PathVariable UUID id) {
        return service.detail(id);
    }

    @PostMapping("/api/v1/appointments")
    @PreAuthorize("hasAuthority('schedule:write')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Agenda atendimento", description = "409 SCHEDULE_CONFLICT se profissional/sala ocupados.")
    public AppointmentView create(@Valid @RequestBody AppointmentRequest request) {
        return service.create(request);
    }

    @PutMapping("/api/v1/appointments/{id}")
    @PreAuthorize("hasAuthority('schedule:write')")
    @Operation(
            summary = "Edita ou remarca",
            description = "422 APPOINTMENT_CLOSED; 409 SCHEDULE_CONFLICT/STALE_VERSION")
    public AppointmentView update(@PathVariable UUID id, @Valid @RequestBody AppointmentRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/api/v1/appointments/{id}/status")
    @PreAuthorize("hasAuthority('schedule:write')")
    @Operation(
            summary = "Muda o status (confirmar, chegada, atendimento, concluir, falta, cancelar)",
            description = "422 INVALID_STATUS_TRANSITION. Cancelamento exige motivo.")
    public AppointmentView changeStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest request) {
        return service.changeStatus(id, request.status(), request.reason());
    }

    @PostMapping("/api/v1/appointments/{id}/patient-link")
    @PreAuthorize("hasAuthority('schedule:write')")
    @Operation(
            summary = "Gera o link do paciente para confirmar ou cancelar a consulta",
            description = "Usado no lembrete por WhatsApp. Renova o link (o anterior deixa de valer). "
                    + "Retorna o caminho relativo ao endereço do sistema.")
    public java.util.Map<String, String> patientLink(@PathVariable UUID id) {
        service.get(id); // 404 se não for desta clínica
        return java.util.Map.of("path", "/consulta#token=" + links.issueToken(id));
    }

    @GetMapping("/api/v1/appointments/availability")
    @PreAuthorize("hasAuthority('schedule:read')")
    @Operation(summary = "Horários livres de um profissional num dia")
    public List<FreeSlot> availability(
            @RequestParam UUID professionalId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "30") int durationMinutes) {
        return service.availability(professionalId, date, Math.clamp(durationMinutes, 5, 480));
    }

    @PostMapping("/api/v1/public/appointments/lookup")
    @Operation(summary = "Público: dados da consulta pelo link do paciente", tags = "Público")
    public PublicAppointmentView publicLookup(@Valid @RequestBody TokenRequest request) {
        return links.lookup(request.token());
    }

    @PostMapping("/api/v1/public/appointments/respond")
    @Operation(summary = "Público: paciente confirma ou cancela pelo link", tags = "Público")
    public PublicAppointmentView publicRespond(@Valid @RequestBody PublicResponseRequest request) {
        return links.respond(request.token(), request.action());
    }
}
