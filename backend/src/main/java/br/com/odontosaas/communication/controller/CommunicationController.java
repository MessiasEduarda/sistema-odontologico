package br.com.odontosaas.communication.controller;

import br.com.odontosaas.communication.dto.request.TemplateRequest;
import br.com.odontosaas.communication.dto.response.MessageView;
import br.com.odontosaas.communication.dto.response.TemplateView;
import br.com.odontosaas.communication.service.CommunicationService;
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
@RequestMapping("/api/v1/communication")
@PreAuthorize("hasAuthority('clinic:settings:manage')")
@Tag(
        name = "Comunicação",
        description = "Modelos de mensagem e histórico de envios (permissão: clinic:settings:manage)")
public class CommunicationController {

    private final CommunicationService service;

    public CommunicationController(CommunicationService service) {
        this.service = service;
    }

    @GetMapping("/templates")
    public List<TemplateView> templates() {
        return service.templates();
    }

    @PutMapping("/templates/{type}")
    @Operation(summary = "Personaliza modelo", description = "Campos: {{paciente}} {{clinica}} {{data}} {{hora}} ...")
    public TemplateView saveTemplate(@PathVariable String type, @Valid @RequestBody TemplateRequest request) {
        return service.saveTemplate(type, request);
    }

    @GetMapping("/messages")
    @Operation(summary = "Histórico de envios", description = "status: PENDING, SENT, FAILED")
    public List<MessageView> messages(@RequestParam(required = false) String status) {
        return service.messages(status);
    }

    @PostMapping("/messages/{id}/retry")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retry(@PathVariable UUID id) {
        service.retry(id);
    }
}
