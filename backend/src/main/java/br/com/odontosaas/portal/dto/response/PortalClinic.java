package br.com.odontosaas.portal.dto.response;

public record PortalClinic(
        String name,
        String legalName,
        String document,
        String phone,
        String email,
        String zipCode,
        String address,
        String city,
        String state) {}
