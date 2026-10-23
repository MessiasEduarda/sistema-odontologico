package br.com.odontosaas.portal.dto.request;

import jakarta.validation.constraints.Size;

public record ContactBody(
        @Size(max = 20) String mobile,
        @Size(max = 20) String phone,
        Boolean allowEmail,
        Boolean allowSms,
        Boolean allowWhatsapp) {}
