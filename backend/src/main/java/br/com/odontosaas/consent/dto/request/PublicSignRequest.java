package br.com.odontosaas.consent.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PublicSignRequest(
        @NotBlank @Size(max = 64) String token,
        @NotBlank @Size(max = 160) String signerName,
        @Size(max = 20) String signerDocument,

        @NotBlank
        @Size(max = 400000)
        @Pattern(regexp = "^data:image/png;base64,[A-Za-z0-9+/=]+$", message = "assinatura inválida")
        String signatureImage,

        @AssertTrue(message = "é necessário declarar que leu e concorda com o termo")
        boolean accepted) {}
