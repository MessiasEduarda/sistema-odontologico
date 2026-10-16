package br.com.odontosaas.clinic;

import br.com.odontosaas.clinic.ClinicDtos.ProcedureRequest;
import br.com.odontosaas.clinic.ClinicDtos.ProcedureView;
import br.com.odontosaas.clinic.ClinicDtos.ProfessionalRequest;
import br.com.odontosaas.clinic.ClinicDtos.ProfessionalView;
import br.com.odontosaas.clinic.ClinicDtos.RoomRequest;
import br.com.odontosaas.clinic.ClinicDtos.RoomView;
import br.com.odontosaas.clinic.ClinicDtos.ScheduleRequest;
import br.com.odontosaas.clinic.ClinicDtos.ScheduleSlot;
import br.com.odontosaas.clinic.ClinicDtos.SettingsRequest;
import br.com.odontosaas.clinic.ClinicDtos.SettingsView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Cadastros da clínica ativa. Leitura: qualquer membro (necessária para agenda, prontuário e orçamentos).
 * Escrita: {@code clinic:settings:manage}.
 */
@RestController
@RequestMapping("/api/v1/clinic")
@Tag(name = "Cadastros da clínica", description = "Configurações, profissionais, horários, salas e procedimentos")
public class ClinicController {

    private static final String MEMBER = "isAuthenticated() and principal.tenantId() != null";
    private static final String MANAGE = "hasAuthority('clinic:settings:manage')";

    private final ClinicService service;
    private final ClinicLogoService logos;

    public ClinicController(ClinicService service, ClinicLogoService logos) {
        this.service = service;
        this.logos = logos;
    }

    @GetMapping("/logo")
    @PreAuthorize(MEMBER)
    @Operation(summary = "Logo da clínica (404 se não houver)")
    public ResponseEntity<byte[]> logo() {
        return logos.get()
                .map(l -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(l.contentType()))
                        .header("X-Content-Type-Options", "nosniff")
                        .header(HttpHeaders.CACHE_CONTROL, "private, max-age=86400")
                        .body(l.data()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record LogoResult(Instant logoUpdatedAt) {}

    @PutMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(MANAGE)
    @Operation(summary = "Envia ou troca a logo (PNG, até 1 MB)")
    public LogoResult saveLogo(@RequestParam("file") MultipartFile file) {
        return new LogoResult(logos.save(file));
    }

    @DeleteMapping("/logo")
    @PreAuthorize(MANAGE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a logo (o menu volta a mostrar as iniciais)")
    public void deleteLogo() {
        logos.delete();
    }

    @GetMapping("/settings")
    @PreAuthorize(MEMBER)
    @Operation(summary = "Dados e preferências da clínica")
    public SettingsView settings() {
        return service.settings();
    }

    @PutMapping("/settings")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Atualiza dados e preferências da clínica", description = "Permissão: clinic:settings:manage")
    public SettingsView updateSettings(@Valid @RequestBody SettingsRequest request) {
        return service.updateSettings(request);
    }

    @GetMapping("/professionals")
    @PreAuthorize(MEMBER)
    @Operation(summary = "Profissionais", description = "Filtro opcional ?active=true|false")
    public List<ProfessionalView> professionals(@RequestParam(required = false) Boolean active) {
        return service.professionals(active);
    }

    @GetMapping("/professionals/{id}")
    @PreAuthorize(MEMBER)
    public ProfessionalView professional(@PathVariable UUID id) {
        return service.professional(id);
    }

    @PostMapping("/professionals")
    @PreAuthorize(MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastra profissional", description = "Permissão: clinic:settings:manage")
    public ProfessionalView createProfessional(@Valid @RequestBody ProfessionalRequest request) {
        return service.createProfessional(request);
    }

    @PutMapping("/professionals/{id}")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Atualiza profissional", description = "Envie version para controle de concorrência (409).")
    public ProfessionalView updateProfessional(@PathVariable UUID id, @Valid @RequestBody ProfessionalRequest request) {
        return service.updateProfessional(id, request);
    }

    @GetMapping("/professionals/{id}/schedule")
    @PreAuthorize(MEMBER)
    @Operation(summary = "Horários de atendimento (weekday ISO: 1=segunda ... 7=domingo)")
    public List<ScheduleSlot> schedule(@PathVariable UUID id) {
        return service.schedule(id);
    }

    @PutMapping("/professionals/{id}/schedule")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Substitui os horários de atendimento")
    public List<ScheduleSlot> replaceSchedule(@PathVariable UUID id, @Valid @RequestBody ScheduleRequest request) {
        return service.replaceSchedule(id, request.slots());
    }

    @GetMapping("/rooms")
    @PreAuthorize(MEMBER)
    public List<RoomView> rooms() {
        return service.rooms();
    }

    @PostMapping("/rooms")
    @PreAuthorize(MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    public RoomView createRoom(@Valid @RequestBody RoomRequest request) {
        return service.createRoom(request);
    }

    @PutMapping("/rooms/{id}")
    @PreAuthorize(MANAGE)
    public RoomView updateRoom(@PathVariable UUID id, @Valid @RequestBody RoomRequest request) {
        return service.updateRoom(id, request);
    }

    @GetMapping("/procedures")
    @PreAuthorize(MEMBER)
    @Operation(summary = "Catálogo de procedimentos", description = "Filtros opcionais: q (nome/código), active")
    public List<ProcedureView> procedures(
            @RequestParam(required = false) String q, @RequestParam(required = false) Boolean active) {
        return service.procedures(q, active);
    }

    @PostMapping("/procedures")
    @PreAuthorize(MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    public ProcedureView createProcedure(@Valid @RequestBody ProcedureRequest request) {
        return service.createProcedure(request);
    }

    @PutMapping("/procedures/{id}")
    @PreAuthorize(MANAGE)
    public ProcedureView updateProcedure(@PathVariable UUID id, @Valid @RequestBody ProcedureRequest request) {
        return service.updateProcedure(id, request);
    }
}
