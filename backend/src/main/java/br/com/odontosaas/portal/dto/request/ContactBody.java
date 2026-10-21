package br.com.odontosaas.portal.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ContactBody(
        @NotBlank @Size(max = 64) String token,
        @Email @Size(max = 254) String email,
        @Size(max = 20) String mobile,
        @Size(max = 20) String phone,
        boolean allowEmail,
        boolean allowSms,
        boolean allowWhatsapp) {}
