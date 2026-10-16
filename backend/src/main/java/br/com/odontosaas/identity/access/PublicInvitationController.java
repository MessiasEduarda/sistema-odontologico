package br.com.odontosaas.identity.access;

import br.com.odontosaas.identity.access.AccessDtos.AcceptInvitationRequest;
import br.com.odontosaas.identity.access.AccessDtos.InvitationPreview;
import br.com.odontosaas.identity.access.AccessDtos.TokenRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Aceite de convite (público; o token do e-mail é a autorização). Token sempre no corpo, nunca na URL. */
@RestController
@RequestMapping("/api/v1/public/invitations")
@Tag(name = "Convites (público)")
public class PublicInvitationController {

    private final InvitationService invitations;

    public PublicInvitationController(InvitationService invitations) {
        this.invitations = invitations;
    }

    @PostMapping("/preview")
    @Operation(summary = "Dados do convite para a tela de aceite", description = "400 INVALID_TOKEN")
    public InvitationPreview preview(@Valid @RequestBody TokenRequest request) {
        return invitations.preview(request.token());
    }

    @PostMapping("/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Aceita o convite",
            description = "Conta nova: name + password. Conta existente: password atual. 400 INVALID_TOKEN, 401.")
    public void accept(@Valid @RequestBody AcceptInvitationRequest request) {
        invitations.accept(request);
    }
}
