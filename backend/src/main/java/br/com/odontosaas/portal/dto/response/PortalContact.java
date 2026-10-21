package br.com.odontosaas.portal.dto.response;

public record PortalContact(
        String fullName,
        String email,
        String mobile,
        String phone,
        boolean allowEmail,
        boolean allowSms,
        boolean allowWhatsapp) {}
