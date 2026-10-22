package br.com.odontosaas.consent.controller;

import br.com.odontosaas.consent.dto.request.PublicSignRequest;
import br.com.odontosaas.consent.dto.request.PublicTokenRequest;
import br.com.odontosaas.consent.dto.response.PublicConsentView;
import br.com.odontosaas.consent.service.ConsentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Prontuário")
public class PublicConsentController {

    private final ConsentService consents;

    public PublicConsentController(ConsentService consents) {
        this.consents = consents;
    }

    @PostMapping("/api/v1/public/consents/lookup")
    @Operation(summary = "Público: termo a assinar (link do paciente)", tags = "Público")
    public PublicConsentView publicLookup(@Valid @RequestBody PublicTokenRequest request) {
        return consents.publicLookup(request.token());
    }

    @PostMapping("/api/v1/public/consents/sign")
    @Operation(summary = "Público: paciente assina o termo", tags = "Público")
    public PublicConsentView publicSign(@Valid @RequestBody PublicSignRequest request) {
        return consents.publicSign(
                request.token(), request.signerName(), request.signerDocument(), request.signatureImage());
    }
}
