package br.com.odontosaas.identity.dto.response;

public record InvitationPreview(String clinicName, String email, String roleName, boolean existingAccount) {}
