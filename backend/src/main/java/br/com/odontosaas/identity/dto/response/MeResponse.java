package br.com.odontosaas.identity.dto.response;

import java.util.List;

/**
 * Quem sou eu: usuário, clínica ativa, permissões nela, clínicas disponíveis para troca e se já aceitou a versão
 * vigente dos termos ({@code termsVersion}).
 */
public record MeResponse(
        UserView user,
        boolean platformAdmin,
        ClinicSummary activeClinic,
        List<String> permissions,
        List<ClinicSummary> clinics,
        boolean termsAccepted,
        String termsVersion) {}
